package com.gwynn7.motolog.rides.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Query("SELECT * FROM rides ORDER BY startedAt DESC")
    fun observeRides(): Flow<List<Ride>>

    @Query("SELECT * FROM rides WHERE id = :id")
    fun observeRide(id: String): Flow<Ride?>

    @Query("SELECT * FROM rides WHERE activeSlot = 1 LIMIT 1")
    fun observeActiveRide(): Flow<Ride?>

    @Query("SELECT * FROM rides WHERE activeSlot = 1 LIMIT 1")
    suspend fun activeRide(): Ride?

    @Query("SELECT * FROM rides WHERE id = :id")
    suspend fun getRide(id: String): Ride?

    @Query("SELECT * FROM ride_points WHERE rideId = :id ORDER BY id ASC")
    fun observePoints(id: String): Flow<List<RidePoint>>

    @Query("SELECT * FROM ride_points WHERE rideId = :id ORDER BY id DESC LIMIT 2000")
    fun observeRecentPoints(id: String): Flow<List<RidePoint>>

    @Query("SELECT MAX(id) FROM ride_points WHERE rideId = :id")
    fun observeLastPointId(id: String): Flow<Long?>

    @Query("SELECT * FROM ride_points WHERE rideId = :id AND id > :afterId ORDER BY id ASC")
    suspend fun pointsAfter(id: String, afterId: Long): List<RidePoint>

    @Insert suspend fun insert(ride: Ride)
    @Update suspend fun update(ride: Ride)
    @Insert suspend fun insertPoint(point: RidePoint)

    @Query("DELETE FROM rides WHERE id = :id AND activeSlot IS NULL")
    suspend fun deleteFinished(id: String)
}
