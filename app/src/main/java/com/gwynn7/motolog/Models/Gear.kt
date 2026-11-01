package com.gwynn7.motolog.Models

import android.net.Uri
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "gear")
data class Gear(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val manufacturer: String,
    val model: String,
    val price: Double,
    val date: Long,
    var image: Uri? = null,
    var listImage: Uri? = null,
): Parcelable
