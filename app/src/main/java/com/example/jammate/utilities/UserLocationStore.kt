package com.example.jammate.utilities

import android.content.Context
import com.example.jammate.model.LocationData
import androidx.core.content.edit

object UserLocationStore {

    private const val PREFS = "jm_user_location"
    private const val KEY_LAT = "lat"
    private const val KEY_LNG = "lng"
    private const val KEY_UPDATED = "updatedAt"

    var lastLocation: LocationData? = null
        private set

    fun load(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!sp.contains(KEY_LAT) || !sp.contains(KEY_LNG)) return

        val lat = sp.getFloat(KEY_LAT, 0f).toDouble()
        val lng = sp.getFloat(KEY_LNG, 0f).toDouble()
        val updated = sp.getLong(KEY_UPDATED, 0L)

        lastLocation = LocationData(
            lat = lat,
            lng = lng,
            updatedAt = updated
        )
    }

    fun save(context: Context, location: LocationData) {
        lastLocation = location
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit {
            putFloat(KEY_LAT, (location.lat ?: 0.0).toFloat())
                .putFloat(KEY_LNG, (location.lng ?: 0.0).toFloat())
                .putLong(KEY_UPDATED, location.updatedAt)
        }
    }

    fun isFresh(maxAgeMs: Long): Boolean {
        val t = lastLocation?.updatedAt ?: return false
        return System.currentTimeMillis() - t <= maxAgeMs
    }
}