package com.gwynn7.motolog.Repository

import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.gwynn7.motolog.Database.MotorcycleDatabase
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.Models.Motorcycle
import com.gwynn7.motolog.Models.RepairsLog

class MotorcycleRepository(private val database: MotorcycleDatabase) {
    private val motorcycleDao = database.motorcycleDao()
    private val distanceLogDao = database.distanceLogDao()
    private val repairsLogDao = database.repairsLogDao()
    private val modsLogDao = database.modsLogDao()

    val readAllData: LiveData<List<Motorcycle>> = motorcycleDao.readAllData()

    suspend fun addMotorcycle(motorcycle: Motorcycle) = motorcycleDao.addMotorcycle(motorcycle)

    /** Keeps the derived personal distance in sync with the persisted distance logs. */
    suspend fun updateMotorcycle(
        motorcycle: Motorcycle,
        distanceLogsToDelete: List<DistanceLog> = emptyList()
    ) = database.withTransaction {
        for (log in distanceLogsToDelete) {
            distanceLogDao.delete(log)
        }
        motorcycleDao.updateMotorcycle(motorcycle.withCurrentDistance(distanceLogDao.getLogsForBikeNow(motorcycle.id)))
    }

    suspend fun deleteMotorcycle(motorcycle: Motorcycle) = motorcycleDao.deleteMotorcycle(motorcycle)

    fun getMotorcycle(id: Int): LiveData<Motorcycle?> = motorcycleDao.getMotorcycle(id)

    fun getDistanceLogs(bikeId: Int): LiveData<List<DistanceLog>> = distanceLogDao.getLogsForBike(bikeId)
    suspend fun saveDistanceLog(log: DistanceLog) = database.withTransaction {
        if (log.id == 0) distanceLogDao.insert(log) else distanceLogDao.update(log)
        syncPersonalDistance(log.motorcycleId)
    }
    suspend fun deleteDistanceLog(log: DistanceLog) = database.withTransaction {
        distanceLogDao.delete(log)
        syncPersonalDistance(log.motorcycleId)
    }

    fun getRepairsLogs(bikeId: Int): LiveData<List<RepairsLog>> = repairsLogDao.getLogsForBike(bikeId)
    suspend fun saveRepairsLog(log: RepairsLog) {
        if (log.id == 0) repairsLogDao.insert(log) else repairsLogDao.update(log)
    }
    suspend fun deleteRepairsLog(log: RepairsLog) = repairsLogDao.delete(log)

    fun getModsLogs(bikeId: Int): LiveData<List<ModsLog>> = modsLogDao.getLogsForBike(bikeId)
    suspend fun saveModsLog(log: ModsLog) {
        if (log.id == 0) modsLogDao.insert(log) else modsLogDao.update(log)
    }
    suspend fun deleteModsLog(log: ModsLog) = modsLogDao.delete(log)

    private suspend fun syncPersonalDistance(bikeId: Int) {
        val motorcycle = motorcycleDao.findMotorcycle(bikeId) ?: return
        motorcycleDao.updateMotorcycle(motorcycle.withCurrentDistance(distanceLogDao.getLogsForBikeNow(bikeId)))
    }

    private fun Motorcycle.withCurrentDistance(logs: List<DistanceLog>): Motorcycle {
        val latestDistance = logs.firstOrNull()?.distance ?: start_km
        return copy(personal_km = latestDistance - start_km)
    }
}
