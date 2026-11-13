package com.gwynn7.motolog.rides.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.MainActivity
import com.gwynn7.motolog.R
import com.gwynn7.motolog.rides.data.Ride
import com.gwynn7.motolog.rides.data.RidePoint
import com.gwynn7.motolog.rides.data.RideRepository
import com.gwynn7.motolog.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

enum class RecordingPhase { IDLE, SEARCHING_GPS, GPS_READY, CALIBRATING, RECORDING, PAUSED }

data class RecorderStatus(val rideId: String? = null, val running: Boolean = false,
    val speed: Float = 0f, val error: String? = null,
    val phase: RecordingPhase = RecordingPhase.IDLE, val accuracyMeters: Float? = null,
    val leanDegrees: Int? = null, val calibrationSeconds: Int = 0, val leanEnabled: Boolean = true,
    val elapsedMillis: Long = 0)

/** Starts only from a visible screen after permission is granted. No background-location grant. */
class RideRecorderService : Service(), LocationListener, SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val repository by lazy { RideRepository(MotorcycleDatabase.getDatabase(this)) }
    private val locations by lazy { getSystemService(LocationManager::class.java) }
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var current: Ride? = null
    private var previous: Location? = null
    private var durationBase = 0L
    private var resumedAt = 0L
    private var lean = LeanEstimator()
    private var leanDegrees: Int? = null
    private var acceleration: Float? = null
    private var sensorSpeed = 0f
    private var phase = RecordingPhase.IDLE
    private var preparedBikeId = -1
    private var preparationFix: Location? = null
    private var calibrationStarted = 0L
    private var lastCheckpoint = 0L
    private var leanEnabled = true
    private var acceptingLocations = false
    private var locationGeneration = 0

    private sealed interface Command {
        data class Action(val action: String?, val bikeId: Int) : Command
        data class Fix(val location: Location, val generation: Int, val acceleration: Float?, val lean: Int?) : Command
        data object Tick : Command
        data object GpsDisabled : Command
    }

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.ride_recording_channel),
            NotificationManager.IMPORTANCE_LOW))
        scope.launch {
            for (command in commands) {
                try {
                    when (command) {
                        is Command.Action -> handleAction(command)
                        is Command.Fix -> record(command)
                        Command.Tick -> tick()
                        Command.GpsDisabled -> {
                            preparationFix = null
                            sensorSpeed = 0f
                            if (phase in listOf(RecordingPhase.GPS_READY, RecordingPhase.CALIBRATING))
                                phase = RecordingPhase.SEARCHING_GPS
                            if (acceptingLocations) publish()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    stopInputs()
                    mutableStatus.value = RecorderStatus(current?.id, error = getString(R.string.ride_recording_error))
                    stopSelf()
                }
            }
        }
        scope.launch {
            while (true) { delay(250.milliseconds); commands.send(Command.Tick) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasLocationPermission(this)) {
            mutableStatus.value = RecorderStatus(error = getString(R.string.ride_location_required))
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            mutableStatus.value = mutableStatus.value.copy(running = true, error = null)
            commands.trySend(Command.Action(intent?.action, intent?.getIntExtra(BIKE_ID, -1) ?: -1))
        } catch (_: SecurityException) {
            mutableStatus.value = RecorderStatus(error = getString(R.string.ride_location_required))
            stopSelf()
        } catch (_: RuntimeException) {
            // The app may lose foreground eligibility between the visible tap and service startup.
            mutableStatus.value = RecorderStatus(error = getString(R.string.ride_recording_error))
            stopSelf()
        }
        // After process death, offer explicit recovery instead of silently restarting location access.
        return START_NOT_STICKY
    }

    private suspend fun handleAction(command: Command.Action) {
        when (command.action) {
            PREPARE -> {
                check(current == null && repository.getActiveRide() == null)
                preparedBikeId = command.bikeId
                preparationFix = null
                lean = LeanEstimator()
                leanDegrees = null
                phase = RecordingPhase.SEARCHING_GPS
                startInputs(preparing = true)
                publish()
            }
            BEGIN, BEGIN_WITHOUT_LEAN -> {
                if (phase != RecordingPhase.GPS_READY || !gpsReady()) return
                if (command.action == BEGIN && leanEnabled) {
                    lean = LeanEstimator()
                    calibrationStarted = SystemClock.elapsedRealtime()
                    phase = RecordingPhase.CALIBRATING
                    publish()
                } else {
                    leanEnabled = false
                    beginPreparedRide()
                }
            }
            CANCEL_PREPARATION -> {
                if (current == null) { stopInputs(); phase = RecordingPhase.IDLE; publish(); stopSelf() }
                else {
                    // Back can arrive while the final calibration tick is committing the new ride.
                    checkpoint(); stopInputs()
                    current = current?.copy(state = Ride.PAUSED)
                    current?.let { repository.checkpoint(it) }
                    phase = RecordingPhase.PAUSED
                    publish()
                }
            }
            RESUME -> {
                if (current?.state == Ride.RECORDING && acceptingLocations) return
                val saved = current ?: repository.getActiveRide()
                current = requireNotNull(saved) { "No unfinished ride to resume" }
                current = current!!.copy(state = Ride.RECORDING, segment = current!!.segment + 1)
                durationBase = current!!.durationMillis
                resumedAt = SystemClock.elapsedRealtime()
                previous = null
                lean = LeanEstimator()
                leanDegrees = null
                acceleration = null
                repository.checkpoint(current!!)
                startInputs()
                phase = RecordingPhase.RECORDING
                publish()
            }
            PAUSE -> {
                checkpoint()
                stopInputs()
                current = current?.copy(state = Ride.PAUSED)
                current?.let { repository.checkpoint(it) }
                phase = RecordingPhase.PAUSED
                publish()
            }
            FINISH -> {
                checkpoint()
                stopInputs()
                val ride = current ?: repository.getActiveRide()
                ride?.let { repository.finish(it.id) }
                mutableStatus.value = RecorderStatus(ride?.id)
                stopSelf()
            }
        }
        notifications.notify(NOTIFICATION_ID, notification())
    }

    private fun gpsReady(): Boolean {
        val fix = preparationFix ?: return false
        return GpsReadiness.isReady(fix.accuracy, (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1e9)
    }

    private suspend fun beginPreparedRide() {
        if (!gpsReady()) { phase = RecordingPhase.SEARCHING_GPS; publish(); return }
        current = repository.start(preparedBikeId, AppSettings(this).distanceUnit())
        AppSettings(this).selectBike(preparedBikeId)
        durationBase = 0
        resumedAt = SystemClock.elapsedRealtime()
        lastCheckpoint = resumedAt
        previous = null
        phase = RecordingPhase.RECORDING
        locations.removeUpdates(this)
        locationGeneration++
        @Suppress("MissingPermission")
        locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, AppSettings(this).tracking.first().intervalMillis,
            0f, this, Looper.getMainLooper())
        // Preparation/calibration locations never contribute to the recorded distance.
        publish()
    }

    private suspend fun tick() {
        val now = SystemClock.elapsedRealtime()
        if (phase == RecordingPhase.CALIBRATING) {
            if (now - calibrationStarted >= 6500) {
                lean.finishCalibration()
                beginPreparedRide()
            } else publish()
        } else if (phase == RecordingPhase.RECORDING) {
            publish()
        } else if (phase == RecordingPhase.GPS_READY && !gpsReady()) {
            phase = RecordingPhase.SEARCHING_GPS
            publish()
        }
        if (now - lastCheckpoint >= 5000) { lastCheckpoint = now; checkpoint() }
    }

    private fun publish() {
        mutableStatus.value = RecorderStatus(current?.id, running = true, speed = sensorSpeed,
            phase = phase, accuracyMeters = preparationFix?.accuracy, leanDegrees = leanDegrees,
            calibrationSeconds = if (phase == RecordingPhase.CALIBRATING)
                ((6500 - (SystemClock.elapsedRealtime() - calibrationStarted)).coerceAtLeast(0) / 1000 + 1).toInt() else 0,
            leanEnabled = leanEnabled,
            elapsedMillis = if (phase == RecordingPhase.RECORDING) durationBase + SystemClock.elapsedRealtime() - resumedAt
                else current?.durationMillis ?: 0)
    }

    private suspend fun startInputs(preparing: Boolean = false) {
        check(hasLocationPermission(this))
        check(locations.isProviderEnabled(LocationManager.GPS_PROVIDER))
        val settings = AppSettings(this).tracking.first()
        leanEnabled = settings.leanEnabled && sensors.getDefaultSensor(Sensor.TYPE_GRAVITY) != null &&
            sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null
        locationGeneration++
        acceptingLocations = true
        @Suppress("MissingPermission")
        locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, if (preparing) 1000L else settings.intervalMillis, 0f, this, Looper.getMainLooper())
        sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (leanEnabled) sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopInputs() {
        acceptingLocations = false
        locationGeneration++
        locations.removeUpdates(this)
        sensors.unregisterListener(this)
        previous = null
        sensorSpeed = 0f
    }

    override fun onLocationChanged(location: Location) {
        if (acceptingLocations) commands.trySend(Command.Fix(Location(location), locationGeneration, acceleration, leanDegrees))
    }

    // Implement these explicitly for Android 9/10, where LocationListener has no default bodies.
    override fun onProviderDisabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) commands.trySend(Command.GpsDisabled)
    }
    override fun onProviderEnabled(provider: String) = Unit
    @Deprecated("Deprecated in the platform")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private suspend fun record(fix: Command.Fix) {
        if (!acceptingLocations || fix.generation != locationGeneration) return
        val location = fix.location
        if (phase in listOf(RecordingPhase.SEARCHING_GPS, RecordingPhase.GPS_READY, RecordingPhase.CALIBRATING)) {
            if (location.hasAccuracy()) preparationFix = location
            if (phase != RecordingPhase.CALIBRATING) phase = if (gpsReady()) RecordingPhase.GPS_READY else RecordingPhase.SEARCHING_GPS
            publish()
            return
        }
        val ride = current ?: return
        if (ride.state != Ride.RECORDING) return
        val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1e9
        if (!location.hasAccuracy() || !LocationFilter.isUsable(location.accuracy, age)) return
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        val speed = if (location.hasSpeed() && location.speed.isFinite()) location.speed.coerceAtLeast(0f) else 0f
        val last = previous
        val seconds = last?.let { (location.elapsedRealtimeNanos - it.elapsedRealtimeNanos) / 1e9 }
        if (seconds != null && seconds <= 0) return
        val delta = last?.let { LocationFilter.segmentDistance(it.distanceTo(location).toDouble(), seconds!!, location.accuracy, speed) }
        // Split the line after GPS gaps or jumps; do not add the unknown distance.
        val segment = if (last != null && delta == null) ride.segment + 1 else ride.segment
        current = ride.copy(distanceMeters = ride.distanceMeters + (delta ?: 0.0),
            durationMillis = durationBase + SystemClock.elapsedRealtime() - resumedAt, segment = segment)
        val point = RidePoint(rideId = ride.id, recordedAt = location.time, latitude = location.latitude,
            longitude = location.longitude, accuracyMeters = location.accuracy,
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() && it.isFinite() },
            speedMetersPerSecond = speed, acceleration = fix.acceleration, leanDegrees = fix.lean, segment = segment)
        repository.checkpoint(current!!, point)
        previous = location
        sensorSpeed = speed
        preparationFix = location
        publish()
    }

    private suspend fun checkpoint() {
        val ride = current ?: return
        if (ride.state != Ride.RECORDING) return
        current = ride.copy(durationMillis = durationBase + SystemClock.elapsedRealtime() - resumedAt)
        repository.checkpoint(current!!)
        notifications.notify(NOTIFICATION_ID, notification())
    }

    override fun onSensorChanged(event: SensorEvent) {
        val calibrating = phase == RecordingPhase.CALIBRATING || (phase == RecordingPhase.RECORDING && sensorSpeed < 4.2f)
        if (!leanEnabled && event.sensor.type == Sensor.TYPE_GRAVITY) return
        when (event.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                acceleration = sqrt(event.values.take(3).sumOf { (it * it).toDouble() }).toFloat().takeIf { it.isFinite() }
                lean.acceleration(event.values, calibrating &&
                    (phase != RecordingPhase.CALIBRATING || SystemClock.elapsedRealtime() - calibrationStarted >= 2500))
            }
            Sensor.TYPE_GRAVITY -> {
                leanDegrees = lean.gravity(event.values, calibrating)
                // Limit dashboard updates to the timer/location cadence, not the sensor sample rate.
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val intent = Intent(this, MainActivity::class.java).putExtra(OPEN_RECORDER, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, 41, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_ride)
            .setContentTitle(getString(when {
                current == null -> R.string.ride_preparing
                current?.state == Ride.PAUSED -> R.string.ride_paused
                else -> R.string.ride_recording
            }))
            .setContentText(current?.motorcycleName ?: getString(R.string.ride_waiting_gps))
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
    }

    override fun onDestroy() {
        stopInputs()
        commands.close()
        scope.cancel()
        mutableStatus.value = mutableStatus.value.copy(running = false, speed = 0f)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val PREPARE = "com.gwynn7.motolog.ride.PREPARE"
        const val BEGIN = "com.gwynn7.motolog.ride.BEGIN"
        const val BEGIN_WITHOUT_LEAN = "com.gwynn7.motolog.ride.BEGIN_WITHOUT_LEAN"
        const val CANCEL_PREPARATION = "com.gwynn7.motolog.ride.CANCEL_PREPARATION"
        const val RESUME = "com.gwynn7.motolog.ride.RESUME"
        const val PAUSE = "com.gwynn7.motolog.ride.PAUSE"
        const val FINISH = "com.gwynn7.motolog.ride.FINISH"
        const val OPEN_RECORDER = "open_recorder"
        private const val BIKE_ID = "motorcycle_id"
        private const val CHANNEL = "ride_recording"
        private const val NOTIFICATION_ID = 41
        private val mutableStatus = MutableStateFlow(RecorderStatus())
        val status = mutableStatus.asStateFlow()
        fun hasLocationPermission(context: Context) = ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun command(context: Context, action: String, bikeId: Int = -1) {
            ContextCompat.startForegroundService(context, Intent(context, RideRecorderService::class.java)
                .setAction(action).putExtra(BIKE_ID, bikeId))
        }
    }
}
