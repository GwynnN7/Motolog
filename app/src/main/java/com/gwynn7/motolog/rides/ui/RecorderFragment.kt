package com.gwynn7.motolog.rides.ui

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.view.Gravity
import android.widget.FrameLayout
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import com.gwynn7.motolog.rides.tracking.RecordingPhase
import androidx.activity.result.contract.ActivityResultContracts
import com.gwynn7.motolog.HomeNavDirections
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.gwynn7.motolog.R
import com.gwynn7.motolog.UnitHelper
import com.gwynn7.motolog.databinding.RideRecorderBinding
import com.gwynn7.motolog.rides.data.Ride
import com.gwynn7.motolog.rides.tracking.RideRecorderService
import kotlinx.coroutines.launch

class RecorderFragment : Fragment(R.layout.ride_recorder) {
    private val model: RecorderViewModel by viewModels()
    private var binding: RideRecorderBinding? = null
    private var dropdownLabels = emptyList<String>()
    private var dropdownIds = emptyList<Int>()
    private var metric = RouteMetric.ROUTE
    private var mapStatus: Int? = null
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (RideRecorderService.hasLocationPermission(requireContext())) {
            lifecycleScope.launch { lifecycle.withResumed { startOrResume() } }
        }
        else binding?.root?.let { Snackbar.make(it, R.string.ride_location_required, Snackbar.LENGTH_LONG)
            .setAction(R.string.ride_open_settings) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${requireContext().packageName}")))
            }.show() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = RideRecorderBinding.bind(view).also { binding = it }
        dropdownIds = emptyList()
        dropdownLabels = emptyList()
        metric = RouteMetric.entries.getOrElse(savedInstanceState?.getInt("metric") ?: 0) { RouteMetric.ROUTE }
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val panelWidth = (280 * resources.displayMetrics.density).toInt()
            (ui.dashboard.layoutParams as FrameLayout.LayoutParams).apply { width = panelWidth; gravity = Gravity.TOP or Gravity.START }
            (ui.recorderControls.layoutParams as FrameLayout.LayoutParams).apply { width = panelWidth; gravity = Gravity.BOTTOM or Gravity.END }
            ui.speed.textSize = 28f
            ui.lean.textSize = 28f
        }
        ui.routeMap.attach(viewLifecycleOwner, savedInstanceState, liveTracking = true) { message ->
            mapStatus = message
            ui.mapNotice.isVisible = message != null && !ui.preparation.isVisible
            if (message != null) ui.mapNotice.setText(message)
        }
        ui.recorderControls.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val content = ui.preparation.getChildAt(0)
            val bottom = ui.recorderControls.height + (24 * resources.displayMetrics.density).toInt()
            if (content.paddingBottom != bottom) content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, bottom)
        }
        ui.mapMetric.text = getString(metric.label)
        ui.mapMetric.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ride_stats)
                .setSingleChoiceItems(RouteMetric.entries.map { getString(it.label) }.toTypedArray(), metric.ordinal) { dialog, index ->
                    metric = RouteMetric.entries[index]
                    ui.mapMetric.text = getString(metric.label)
                    ui.routeMap.showSnapshot(model.route.value, metric)
                    dialog.dismiss()
                }.setNegativeButton(R.string.back, null).show()
        }
        ui.fitMap.setOnClickListener { ui.routeMap.fit() }
        ui.followMap.setOnClickListener { ui.routeMap.follow() }
        ui.leaveRecorder.setOnClickListener { handleBack() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
        ui.skipCalibration.setOnClickListener { sendCommand(RideRecorderService.BEGIN_WITHOUT_LEAN) }
        ui.bikeSelector.setOnItemClickListener { _, _, index, _ -> dropdownIds.getOrNull(index)?.let(model::selectBike) }
        ui.startPause.setOnClickListener {
            val ride = model.ride.value
            if (ride?.state == Ride.RECORDING && model.recorder.value.running && model.recorder.value.phase == RecordingPhase.RECORDING) sendCommand(RideRecorderService.PAUSE)
            else if (model.recorder.value.running && model.recorder.value.phase == RecordingPhase.GPS_READY) sendCommand(RideRecorderService.BEGIN)
            else requestRecording()
        }
        ui.finish.setOnClickListener {
            ui.finish.isEnabled = false
            if (model.recorder.value.running) sendCommand(RideRecorderService.FINISH)
            else model.finishRecovered()
        }
        ui.fuel.setOnClickListener { openFuelSearch(requireContext(), model.route.value.last) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.motorcycles.collect { render() } }
                launch { model.selectedBike.collect { render() } }
                launch { model.recorder.collect { render() } }
                launch { model.loaded.collect { render() } }
                launch { model.busy.collect { render() } }
                launch { model.ride.collect { ride ->
                    if (ride?.state == Ride.FINISHED && findNavController().currentDestination?.id == R.id.ride_recorder) {
                        findNavController().navigate(HomeNavDirections.openRideDetails(ride.id),
                            navOptions { popUpTo(R.id.ride_recorder) { inclusive = true } })
                    } else render()
                } }
                launch { model.route.collect { route -> ui.routeMap.showSnapshot(route, metric) } }
                launch { model.error.collect { error -> if (error != null) { Snackbar.make(ui.root, error, Snackbar.LENGTH_LONG).show(); model.clearError() } } }
            }
        }
    }

    private fun render() {
        val ui = binding ?: return
        val bikes = model.motorcycles.value
        val ride = model.ride.value
        val recorder = model.recorder.value
        val recording = ride?.state == Ride.RECORDING && recorder.running && recorder.phase == RecordingPhase.RECORDING
        val active = ride?.activeSlot != null
        val ids = bikes.map { it.id }
        val labels = bikes.map { it.alias.ifBlank { "${it.manufacturer} ${it.model}" } }
        if (dropdownIds != ids || dropdownLabels != labels) {
            dropdownIds = ids
            dropdownLabels = labels
            ui.bikeSelector.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
            )
        }
        if (!active && model.selectedBike.value !in ids && bikes.isNotEmpty()) model.selectBike(bikes.first().id)
        val bike = bikes.firstOrNull { it.id == model.selectedBike.value }
        ui.bikeSelector.setText(ride?.motorcycleName ?: bike?.let { it.alias.ifBlank { "${it.manufacturer} ${it.model}" } }.orEmpty(), false)
        val preparing = recorder.running && recorder.phase in listOf(RecordingPhase.SEARCHING_GPS, RecordingPhase.GPS_READY, RecordingPhase.CALIBRATING)
        ui.bikeInput.isEnabled = !active && !preparing
        ui.preparation.isVisible = !active
        ui.dashboard.isVisible = active
        ui.mapMetric.isVisible = active
        ui.mapActions.isVisible = active
        ui.mapNotice.isVisible = active && mapStatus != null
        ui.root.keepScreenOn = recording || preparing
        ui.gpsProgress.isVisible = preparing && recorder.phase != RecordingPhase.GPS_READY
        ui.gpsAccuracy.isVisible = preparing
        ui.gpsAccuracy.text = recorder.accuracyMeters?.let { getString(R.string.ride_gps_accuracy, it.toInt()) }.orEmpty()
        ui.skipCalibration.isVisible = preparing && recorder.phase == RecordingPhase.GPS_READY && recorder.leanEnabled
        ui.status.text = when {
            recorder.error != null -> recorder.error
            preparing && recorder.phase == RecordingPhase.SEARCHING_GPS -> getString(R.string.ride_gps_searching)
            preparing && recorder.phase == RecordingPhase.GPS_READY -> getString(R.string.ride_gps_ready)
            preparing && recorder.phase == RecordingPhase.CALIBRATING -> getString(
                if (recorder.calibrationSeconds > 4) R.string.ride_calibration_upright else R.string.ride_calibration_forward, recorder.calibrationSeconds)
            active && (!recorder.running || recorder.rideId != ride.id) -> getString(R.string.ride_recover)
            recording -> getString(R.string.ride_recording)
            active -> getString(R.string.ride_paused)
            bikes.isEmpty() -> getString(R.string.ride_no_bikes)
            else -> getString(R.string.ride_intro)
        }
        val unit = ride?.odometerUnit ?: UnitHelper.getDistance()
        val duration = if (recorder.running && recorder.rideId == ride?.id) recorder.elapsedMillis else ride?.durationMillis ?: 0
        ui.distance.text = "${rideDistance(ride?.distanceMeters ?: 0.0, unit)}  ·  ${rideDuration(duration)}"
        ui.speed.text = rideSpeed(recorder.speed, unit)
        ui.lean.text = recorder.leanDegrees?.let { "$it°" } ?: "—"
        ui.lean.contentDescription = getString(R.string.ride_lean) + ": " + (recorder.leanDegrees?.let { "$it°" } ?: getString(R.string.ride_unavailable))
        ui.recordingStatus.text = "${ride?.motorcycleName.orEmpty()} · ${ui.status.text}"
        ui.startPause.setText(when {
            recording -> R.string.ride_pause
            active -> R.string.ride_resume
            preparing && recorder.phase == RecordingPhase.GPS_READY -> if (recorder.leanEnabled) R.string.ride_calibrate_start else R.string.ride_start
            preparing -> R.string.ride_preparing
            else -> R.string.ride_prepare
        })
        ui.startPause.isEnabled = model.loaded.value && !model.busy.value && (active || bikes.isNotEmpty()) &&
            (!preparing || recorder.phase == RecordingPhase.GPS_READY)
        ui.finish.isVisible = active
        ui.finish.isEnabled = !model.busy.value
        ui.fuel.isEnabled = !recording
    }

    private fun requestRecording() {
        val requested = mutableListOf<String>()
        if (!RideRecorderService.hasLocationPermission(requireContext())) {
            // Android 12+ requires fine and coarse to be requested together.
            requested += Manifest.permission.ACCESS_COARSE_LOCATION
            requested += Manifest.permission.ACCESS_FINE_LOCATION
        }
        val prompts = requireContext().getSharedPreferences("permission_prompts", android.content.Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !prompts.getBoolean("ride_notification_asked", false)) {
            requested += Manifest.permission.POST_NOTIFICATIONS
            prompts.edit().putBoolean("ride_notification_asked", true).apply()
        }
        // Notification denial never blocks GPS recording or causes repeated prompts.
        if (requested.isEmpty()) startOrResume() else permissions.launch(requested.toTypedArray())
    }

    private fun startOrResume() {
        if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val locations = requireContext().getSystemService(LocationManager::class.java)
        if (LocationManager.GPS_PROVIDER !in locations.allProviders) {
            binding?.root?.let { Snackbar.make(it, R.string.ride_gps_unavailable, Snackbar.LENGTH_LONG).show() }
            return
        }
        if (!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.ride_gps_disabled)
                .setPositiveButton(R.string.ride_open_settings) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton(R.string.back, null).show()
            return
        }
        sendCommand(if (model.ride.value?.activeSlot != null) RideRecorderService.RESUME else RideRecorderService.PREPARE)
    }

    private fun sendCommand(action: String) {
        try { RideRecorderService.command(requireContext(), action, model.selectedBike.value) }
        catch (_: RuntimeException) { binding?.root?.let { Snackbar.make(it, R.string.ride_recording_error, Snackbar.LENGTH_LONG).show() } }
    }

    private fun handleBack() {
        val state = model.recorder.value
        if (state.running && state.phase == RecordingPhase.RECORDING) {
            sendCommand(RideRecorderService.PAUSE)
            return
        }
        if (state.running && state.phase in listOf(RecordingPhase.SEARCHING_GPS, RecordingPhase.GPS_READY, RecordingPhase.CALIBRATING))
            sendCommand(RideRecorderService.CANCEL_PREPARATION)
        if (!findNavController().popBackStack())
            (requireActivity() as com.gwynn7.motolog.MainActivity).returnToGarage()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("metric", metric.ordinal); binding?.routeMap?.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { binding = null; super.onDestroyView() }
}
