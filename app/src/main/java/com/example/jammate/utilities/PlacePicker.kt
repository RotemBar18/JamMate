package com.example.jammate.utilities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.jammate.model.LocationData
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode

class PlacePicker(
    private val caller: ActivityResultCaller,   // Fragment OR Activity
    private val context: Context,
    private val locationInput: EditText,
    private val onPicked: (LocationData) -> Unit
) {

    private val launcher: ActivityResultLauncher<Intent> =
        caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val place = Autocomplete.getPlaceFromIntent(result.data!!)

                val loc = LocationData(
                    name = place.displayName ?: "",
                    address = place.formattedAddress ?: "",
                    lat = place.location?.latitude,
                    lng = place.location?.longitude
                )

                locationInput.setText(loc.name.ifBlank { loc.address })
                onPicked(loc)

            } else if (result.data != null) {
                val status = Autocomplete.getStatusFromIntent(result.data!!)
                Toast.makeText(
                    context,
                    status.statusMessage ?: "Error selecting place",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    fun bindInput() {
        locationInput.isFocusable = false
        locationInput.isClickable = true
        locationInput.setOnClickListener { open() }
    }

    fun open() {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION
        )

        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
            .build(context)

        launcher.launch(intent)
    }
}