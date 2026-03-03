package com.example.jammate.model

import kotlin.math.round

data class LocationData(
    var placeId: String = "",
    var name: String = "",
    var address: String = "",
    var lat: Double? = 0.0,
    var lng: Double? = 0.0,
    var updatedAt : Long = 0,

    // for easy filtering now
    var cityKey: String = "",   // "tel_aviv", "jerusalem"

    // for later (Option B)
    var geohash: String = ""    // leave empty for now, fill later
){
    companion object {
        fun fromRaw(lat: Double, lng: Double): LocationData {
            fun round3(x: Double): Double = round(x * 1000.0) / 1000.0
            return LocationData(
                lat = round3(lat),
                lng = round3(lng),
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
