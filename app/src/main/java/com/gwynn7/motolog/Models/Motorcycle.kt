package com.gwynn7.motolog.Models

import android.net.Uri
import android.os.Parcelable
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "motorcycle")
data class Motorcycle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val manufacturer: String,
    val model: String,
    val alias: String,
    val year: Int,
    val start_km: Int,
    var personal_km: Int = 0,
    var image: Uri? = null,
    var listImage: Uri? = null,
    @Embedded
    val info: Info = Info(),
    @Embedded
    val expiry: Expiry = Expiry(),
): Parcelable

@Parcelize
data class Info(
    var cylinders: Int = 0,
    var engine_cc: Double = 0.0,
    var horse_power: Double = 0.0,
    var torque: Double = 0.0,
    var price: Double = 0.0,
    var front_tire: String = "",
    var rear_tire: String = "",
    var plate_number: String = ""
): Parcelable

@Parcelize
data class Expiry(
    var insurance: Long = -1,
    var tax: Long = -1,
    var inspection: Long = -1,
): Parcelable

@Entity(
    tableName = "mods_log",
    foreignKeys = [ForeignKey(
        entity = Motorcycle::class,
        parentColumns = ["id"],
        childColumns = ["motorcycleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("motorcycleId")]
)
data class ModsLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val motorcycleId: Int,
    val title: String,
    val description: String,
    val date: Long,
    val price: Double
)

@Entity(
    tableName = "repairs_log",
    foreignKeys = [ForeignKey(
        entity = Motorcycle::class,
        parentColumns = ["id"],
        childColumns = ["motorcycleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("motorcycleId")]
)
data class RepairsLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val motorcycleId: Int,
    var typeIndex: Int,
    val typeText: String,
    val notes: String,
    val date: Long,
    val repair_km: Int,
    val price: Double
)

@Entity(
    tableName = "distance_log",
    foreignKeys = [ForeignKey(
        entity = Motorcycle::class,
        parentColumns = ["id"],
        childColumns = ["motorcycleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("motorcycleId")]
)
data class DistanceLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val motorcycleId: Int,
    val distance: Int,
    val date: Long,
)
