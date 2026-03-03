package com.example.jammate.interfaces

import com.example.jammate.model.LocationData

interface LocationCallback {
    fun onLocation(location: LocationData)
    fun onLocationError(message: String)
}
