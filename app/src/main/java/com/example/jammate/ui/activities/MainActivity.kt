// MainActivity.kt
package com.example.jammate.ui.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.jammate.R
import com.example.jammate.databinding.ActivityMainBinding
import com.example.jammate.interfaces.LocationCallback
import com.example.jammate.model.LocationData
import com.example.jammate.utilities.LocationDetector
import com.example.jammate.utilities.ThemeManager
import com.example.jammate.utilities.UserLocationStore

class MainActivity : AppCompatActivity(), LocationCallback {

    private lateinit var binding: ActivityMainBinding

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->

            val granted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (!granted) {
                Toast.makeText(
                    this,
                    "Location permission denied. Nearby posts will be less accurate.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                fetchAndCacheLocation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        UserLocationStore.load(applicationContext)
        requestLocationIfNeededOrFetch()

        val navHost = supportFragmentManager.findFragmentById(R.id.main_HOST_navHost) as NavHostFragment
        val navController = navHost.navController
        binding.mainNAVBottomNavbar.setupWithNavController(navController)
    }

    private fun requestLocationIfNeededOrFetch() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            fetchAndCacheLocation()
        }
    }

    private fun fetchAndCacheLocation() {
        if (UserLocationStore.isFresh(maxAgeMs = 30 * 60 * 1000L)) return

        val detector = LocationDetector(this, this)
        detector.fetchLastLocation()
    }

    override fun onLocation(location: LocationData) {
        UserLocationStore.save(applicationContext, location)
    }

    override fun onLocationError(message: String) {

    }
}
