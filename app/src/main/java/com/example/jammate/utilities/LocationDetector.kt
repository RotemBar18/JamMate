package com.example.jammate.utilities

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.example.jammate.interfaces.LocationCallback
import com.example.jammate.model.LocationData
import com.google.android.gms.location.LocationServices

class LocationDetector(
    private val activity: AppCompatActivity,
    private val locationCallback: LocationCallback
) {

    private val fused = LocationServices.getFusedLocationProviderClient(activity)

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun fetchLastLocation() {
        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    val locationData = LocationData.fromRaw(loc.latitude, loc.longitude)
                    locationCallback.onLocation(locationData)
                } else {
                    locationCallback.onLocationError("Couldn't fetch location (GPS off?)")
                }
            }
            .addOnFailureListener {
                locationCallback.onLocationError("Error fetching location")
            }
    }
}
