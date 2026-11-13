package com.gwynn7.motolog.backup

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gwynn7.motolog.settings.settings
import kotlinx.coroutines.flow.first
import org.json.JSONObject

object BackupSettings {
    suspend fun capture(context: Context): JSONObject {
        val preferences = context.settings.data.first()
        return JSONObject().apply {
            put("distance", preferences[stringPreferencesKey("distance")] ?: "km")
            put("currency", preferences[stringPreferencesKey("currency")] ?: "€")
            put("tracking_interval_ms", preferences[longPreferencesKey("tracking_interval_ms")] ?: 2000L)
            put("tracking_lean", preferences[booleanPreferencesKey("tracking_lean")] ?: true)
            put("last_ride_motorcycle", preferences[intPreferencesKey("last_ride_motorcycle")] ?: -1)
            put("language", context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE).getString("language", "en"))
        }
    }

    fun validate(data: JSONObject) {
        require(data.getString("distance") in listOf("km", "mi"))
        require(data.getString("currency") in listOf("€", "$", "£", "¥"))
        require(data.getString("language") in listOf("en", "it"))
        require(data.getLong("tracking_interval_ms") in 1000..10000)
        data.getBoolean("tracking_lean")
        require(data.getInt("last_ride_motorcycle") >= -1)
    }

    suspend fun restore(context: Context, data: JSONObject) {
        validate(data)
        context.settings.edit {
            it[stringPreferencesKey("distance")] = data.getString("distance")
            it[stringPreferencesKey("currency")] = data.getString("currency")
            it[longPreferencesKey("tracking_interval_ms")] = data.getLong("tracking_interval_ms")
            it[booleanPreferencesKey("tracking_lean")] = data.getBoolean("tracking_lean")
            it[intPreferencesKey("last_ride_motorcycle")] = data.getInt("last_ride_motorcycle")
        }
        check(context.getSharedPreferences("locale_prefs", Context.MODE_PRIVATE).edit().putString("language", data.getString("language")).commit())
    }
}
