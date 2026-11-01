package com.gwynn7.motolog.Database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gwynn7.motolog.Models.RepairsLog

@Dao
interface RepairsLogDAO {
    @Insert
    suspend fun insert(log: RepairsLog): Long

    @Update
    suspend fun update(log: RepairsLog)

    @Delete
    suspend fun delete(log: RepairsLog)

    @Query("SELECT * FROM repairs_log WHERE motorcycleId = :bikeId ORDER BY date DESC, id DESC")
    fun getLogsForBike(bikeId: Int): LiveData<List<RepairsLog>>

    @Query("DELETE FROM repairs_log WHERE motorcycleId = :bikeId")
    suspend fun deleteAllForBike(bikeId: Int)
}
