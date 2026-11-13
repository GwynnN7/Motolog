package com.gwynn7.motolog.rides.data

import androidx.room.withTransaction
import com.gwynn7.motolog.Database.DataAccess
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.Models.DistanceLog
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class RideRepository(private val database: MotorcycleDatabase) {
    private val dao = database.rideDao()
    val rides = dao.observeRides()
    val activeRide = dao.observeActiveRide()
    fun observeRide(id: String) = dao.observeRide(id)
    fun points(id: String) = dao.observePoints(id)
    fun liveRoute(id: String) = flow {
        val geometry = RouteGeometry()
        var lastId = 0L
        dao.observeLastPointId(id).collect { latest ->
            if (latest != null && latest > lastId) {
                val added = dao.pointsAfter(id, lastId)
                if (added.isNotEmpty()) {
                    lastId = added.last().id
                    emit(geometry.append(added))
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    suspend fun getActiveRide() = dao.activeRide()

    suspend fun start(bikeId: Int, unit: String, now: Long = System.currentTimeMillis()): Ride = DataAccess.write { database.withTransaction {
        check(dao.activeRide() == null) { "Finish the current ride first" }
        val bike = requireNotNull(database.motorcycleDao().findMotorcycle(bikeId)) { "Motorcycle no longer exists" }
        val odometer = database.distanceLogDao().getLogsForBikeNow(bikeId).firstOrNull()?.distance ?: bike.start_km
        Ride(id = UUID.randomUUID().toString(), motorcycleId = bikeId,
            motorcycleName = bike.alias.ifBlank { "${bike.manufacturer} ${bike.model}" },
            startedAt = now, odometerAtStart = odometer, odometerUnit = unit).also { dao.insert(it) }
    } }

    /** Each accepted location and its cumulative distance are committed together. */
    suspend fun checkpoint(ride: Ride, point: RidePoint? = null) = DataAccess.write { database.withTransaction {
        val stored = dao.getRide(ride.id) ?: return@withTransaction
        if (stored.activeSlot == null) return@withTransaction
        point?.let { dao.insertPoint(it) }
        // Preserve edits which can happen while recording (e.g. deleting the motorcycle).
        dao.update(stored.copy(durationMillis = ride.durationMillis, distanceMeters = ride.distanceMeters,
            state = ride.state, segment = ride.segment))
    } }

    suspend fun finish(id: String, now: Long = System.currentTimeMillis()): Ride = DataAccess.write { database.withTransaction {
        val ride = requireNotNull(dao.getRide(id))
        if (ride.activeSlot == null) return@withTransaction ride
        ride.copy(state = Ride.FINISHED, activeSlot = null, endedAt = now).also { dao.update(it) }
    } }

    suspend fun currentOdometer(ride: Ride): Int? {
        val bike = ride.motorcycleId?.let { database.motorcycleDao().findMotorcycle(it) } ?: return null
        return database.distanceLogDao().getLogsForBikeNow(bike.id).firstOrNull()?.distance ?: bike.start_km
    }

    /** Idempotent, atomic confirmation: a retry can never add the same ride twice. */
    suspend fun confirmMileage(id: String, expectedOdometer: Int, confirmedOdometer: Int,
        unit: String, now: Long = System.currentTimeMillis()) = DataAccess.write { database.withTransaction {
        val ride = requireNotNull(dao.getRide(id))
        if (ride.mileageDecision != Ride.PENDING) return@withTransaction
        require(ride.state == Ride.FINISHED)
        require(ride.odometerUnit == unit) { "Distance unit changed; select the original unit before confirming" }
        val bike = ride.motorcycleId?.let { database.motorcycleDao().findMotorcycle(it) }
            ?: error("Motorcycle no longer exists")
        val latest = database.distanceLogDao().getLogsForBikeNow(bike.id).firstOrNull()
        val current = latest?.distance ?: bike.start_km
        check(current == expectedOdometer) { "Odometer changed; reopen the confirmation" }
        require(confirmedOdometer >= current && confirmedOdometer >= bike.start_km) { "Odometer cannot decrease" }
        require(latest == null || latest.date <= now) { "A future mileage entry must be corrected first" }
        val logId = database.distanceLogDao().insert(DistanceLog(motorcycleId = bike.id,
            distance = confirmedOdometer, date = now))
        check(logId <= Int.MAX_VALUE)
        database.motorcycleDao().updateMotorcycle(bike.copy(personal_km = confirmedOdometer - bike.start_km))
        dao.update(ride.copy(mileageDecision = Ride.APPLIED, distanceLogId = logId.toInt(),
            confirmedOdometer = confirmedOdometer))
    } }

    suspend fun skipMileage(id: String) = DataAccess.write { database.withTransaction {
        val ride = requireNotNull(dao.getRide(id))
        if (ride.state == Ride.FINISHED && ride.mileageDecision == Ride.PENDING) {
            dao.update(ride.copy(mileageDecision = Ride.SKIPPED))
        }
    } }

    suspend fun delete(id: String) = DataAccess.write { dao.deleteFinished(id) }
}
