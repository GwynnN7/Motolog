package com.gwynn7.motolog.Database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gwynn7.motolog.Models.Gear

@Dao
interface GearDAO {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addGear(gear: Gear)

    @Update
    suspend fun updateGear(gear: Gear)

    @Delete
    suspend fun deleteGear(gear: Gear)

    @Query("SELECT * FROM gear ORDER BY id ASC")
    fun readAllData(): LiveData<List<Gear>>

    @Query("SELECT * FROM gear WHERE id = :id")
    fun getGear(id: Int): LiveData<Gear?>
}
