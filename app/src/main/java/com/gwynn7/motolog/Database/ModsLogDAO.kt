package com.gwynn7.motolog.Database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.gwynn7.motolog.Models.ModsLog

@Dao
interface ModsLogDAO {
    @Insert
    suspend fun insert(log: ModsLog): Long

    @Update
    suspend fun update(log: ModsLog)

    @Delete
    suspend fun delete(log: ModsLog)

    @Query("SELECT * FROM mods_log WHERE motorcycleId = :bikeId ORDER BY date DESC, id DESC")
    fun getLogsForBike(bikeId: Int): LiveData<List<ModsLog>>

    @Query("DELETE FROM mods_log WHERE motorcycleId = :bikeId")
    suspend fun deleteAllForBike(bikeId: Int)
}
