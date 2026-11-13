package com.gwynn7.motolog.Database

import androidx.lifecycle.LiveData
import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gwynn7.motolog.Models.Motorcycle

@Dao
interface MotorcycleDAO {
    @Query("SELECT * FROM motorcycle ORDER BY id DESC")
    fun observeMotorcycles(): Flow<List<Motorcycle>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMotorcycle(motorcycle: Motorcycle)

    @Update
    suspend fun updateMotorcycle(motorcycle: Motorcycle)

    @Delete
    suspend fun deleteMotorcycle(motorcycle: Motorcycle)

    @Query("SELECT * FROM motorcycle ORDER BY id ASC")
    fun readAllData(): LiveData<List<Motorcycle>>

    @Query("SELECT * FROM motorcycle WHERE id = :id")
    fun getMotorcycle(id: Int): LiveData<Motorcycle?>

    @Query("SELECT * FROM motorcycle WHERE id = :id")
    suspend fun findMotorcycle(id: Int): Motorcycle?
    @Query("SELECT image FROM motorcycle WHERE image IS NOT NULL UNION SELECT listImage FROM motorcycle WHERE listImage IS NOT NULL")
    suspend fun photoUris(): List<String>
}
