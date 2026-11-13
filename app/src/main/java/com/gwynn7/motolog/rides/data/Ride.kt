package com.gwynn7.motolog.rides.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gwynn7.motolog.Models.Motorcycle

/** SI units for tracking; legacy odometer readings retain their original user-selected unit. */
@Entity(
    tableName = "rides",
    foreignKeys = [ForeignKey(entity = Motorcycle::class, parentColumns = ["id"],
        childColumns = ["motorcycleId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("motorcycleId"), Index(value = ["activeSlot"], unique = true)]
)
data class Ride(
    @PrimaryKey val id: String,
    val motorcycleId: Int?,
    val motorcycleName: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMillis: Long = 0,
    val distanceMeters: Double = 0.0,
    val state: String = RECORDING,
    // SQLite permits multiple NULL values, but only one active ride with slot 1.
    val activeSlot: Int? = 1,
    val segment: Int = 0,
    val odometerAtStart: Int,
    val odometerUnit: String,
    val mileageDecision: String = PENDING,
    val distanceLogId: Int? = null,
    val confirmedOdometer: Int? = null
) {
    companion object {
        const val RECORDING = "recording"
        const val PAUSED = "paused"
        const val FINISHED = "finished"
        const val PENDING = "pending"
        const val APPLIED = "applied"
        const val SKIPPED = "skipped"
    }
}

@Entity(
    tableName = "ride_points",
    foreignKeys = [ForeignKey(entity = Ride::class, parentColumns = ["id"],
        childColumns = ["rideId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("rideId")]
)
data class RidePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: String,
    val recordedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float,
    val acceleration: Float?,
    val leanDegrees: Int?,
    val segment: Int
)
