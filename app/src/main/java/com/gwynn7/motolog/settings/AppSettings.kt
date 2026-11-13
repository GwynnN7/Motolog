package com.gwynn7.motolog.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Keep the published DataStore filename and distance/currency keys.
val Context.settings by preferencesDataStore(name = "settings")

data class TrackingSettings(val intervalMillis: Long = 2000, val leanEnabled: Boolean = true)

class AppSettings(private val context: Context) {
    val tracking = context.settings.data.map { preferences ->
        TrackingSettings((preferences[INTERVAL] ?: 2000).coerceIn(1000, 10000), preferences[LEAN] ?: true)
    }
    suspend fun lastBike(): Int? = context.settings.data.first()[LAST_BIKE]
    suspend fun selectBike(id: Int) { context.settings.edit { it[LAST_BIKE] = id } }
    suspend fun trackingInterval(value: Long) { context.settings.edit { it[INTERVAL] = value.coerceIn(1000, 10000) } }
    suspend fun trackLean(value: Boolean) { context.settings.edit { it[LEAN] = value } }
    suspend fun distanceUnit(): String = context.settings.data.first()[DISTANCE] ?: "km"

    companion object {
        private val LAST_BIKE = intPreferencesKey("last_ride_motorcycle")
        private val INTERVAL = longPreferencesKey("tracking_interval_ms")
        private val LEAN = booleanPreferencesKey("tracking_lean")
        private val DISTANCE = stringPreferencesKey("distance")
    }
}
