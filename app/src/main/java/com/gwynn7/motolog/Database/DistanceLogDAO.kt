package com.gwynn7.motolog.Database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gwynn7.motolog.Models.DistanceLog

@Dao
interface DistanceLogDAO {
    @Insert
    suspend fun insert(log: DistanceLog): Long

    @Update
    suspend fun update(log: DistanceLog)

    @Delete
    suspend fun delete(log: DistanceLog)

    @Query("SELECT * FROM distance_log WHERE motorcycleId = :bikeId ORDER BY date DESC, id DESC")
    fun getLogsForBike(bikeId: Int): LiveData<List<DistanceLog>>

    @Query("SELECT * FROM distance_log WHERE motorcycleId = :bikeId ORDER BY date DESC, id DESC")
    suspend fun getLogsForBikeNow(bikeId: Int): List<DistanceLog>

    @Query("DELETE FROM distance_log WHERE motorcycleId = :bikeId")
    suspend fun deleteAllForBike(bikeId: Int)
}
