package com.gwynn7.motolog.Database

import android.net.Uri
import androidx.room.TypeConverter
import com.gwynn7.motolog.Models.DistanceLog
import com.gwynn7.motolog.Models.ModsLog
import com.gwynn7.motolog.Models.RepairsLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.net.toUri

class Converters {
    @TypeConverter
    fun fromRepairsToString(value: List<RepairsLog>): String {
        val gson = Gson()
        val type = object : TypeToken<List<RepairsLog>>() {}.type
        return gson.toJson(value, type)
    }

    @TypeConverter
    fun fromStringToRepairs(value: String): List<RepairsLog> {
        val gson = Gson()
        val type = object : TypeToken<List<RepairsLog>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromModsToString(value: List<ModsLog>): String {
        val gson = Gson()
        val type = object : TypeToken<List<ModsLog>>() {}.type
        return gson.toJson(value, type)
    }

    @TypeConverter
    fun fromStringToMods(value: String): List<ModsLog> {
        val gson = Gson()
        val type = object : TypeToken<List<ModsLog>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromDistanceLogToString(value: List<DistanceLog>): String {
        val gson = Gson()
        val type = object : TypeToken<List<DistanceLog>>() {}.type
        return gson.toJson(value, type)
    }

    @TypeConverter
    fun fromStringToActionDistance(value: String): List<DistanceLog> {
        val gson = Gson()
        val type = object : TypeToken<List<DistanceLog>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun getStringFromURI(uri: Uri?): String? {
        if (uri == null) return null
        return uri.toString()
    }

    @TypeConverter
    fun getURIFromString(string: String?): Uri? {
        if (string == null) return null
        return string.toUri()
    }
}