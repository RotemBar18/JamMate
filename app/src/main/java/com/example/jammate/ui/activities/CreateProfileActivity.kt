package com.example.jammate.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.jammate.data.UserManager
import com.example.jammate.databinding.ActivityCreateProfileBinding
import com.example.jammate.model.LocationData
import com.example.jammate.model.User
import com.example.jammate.utilities.Constants
import com.example.jammate.utilities.PlacePicker
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class CreateProfileActivity : AppCompatActivity() {

    val isEditMode: Boolean by lazy {
        intent.getBooleanExtra(Constants.Extras.IS_EDIT_MODE, false)
    }

    private lateinit var binding: ActivityCreateProfileBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val userRepo = UserManager.instance

    private var selectedLocation: LocationData? = null
    private lateinit var placePicker: PlacePicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateProfileBinding.inflate(layoutInflater)

        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        prefillIfEditMode()
    }

    // Configures UI elements, navigation between form steps, and the location picker.
    private fun initViews() {
        binding.createProfileINCLUDEPage1.root.visibility = View.VISIBLE
        binding.createProfileINCLUDEPage2.root.visibility = View.GONE

        binding.createProfileBTNNext.visibility = View.VISIBLE
        binding.createProfileBTNFinish.visibility = View.GONE
        binding.createProfileBTNBack.visibility = View.GONE

        if (isEditMode) {
            binding.createProfileLBLHeader.text = "Edit your profile"
            binding.createProfileLBLSeconderyHeader.text = "Update your info"
        } else {
            binding.createProfileLBLHeader.text = "Create your profile"
            binding.createProfileLBLSeconderyHeader.text = "This helps other musicians find you"
        }

        binding.createProfileBTNNext.setOnClickListener {
            binding.createProfileINCLUDEPage1.root.visibility = View.GONE
            binding.createProfileINCLUDEPage2.root.visibility = View.VISIBLE

            binding.createProfileBTNNext.visibility = View.GONE
            binding.createProfileBTNFinish.visibility = View.VISIBLE
            binding.createProfileBTNBack.visibility = View.VISIBLE

            binding.createProfileBARProgBar1.progress = 0
            binding.createProfileBARProgBar2.progress = 1

            binding.createProfileLBLHeader.text = "Musical Taste"
            binding.createProfileLBLSeconderyHeader.text = "What do you play and listen to?"
        }

        binding.createProfileBTNBack.setOnClickListener {
            binding.createProfileINCLUDEPage1.root.visibility = View.VISIBLE
            binding.createProfileINCLUDEPage2.root.visibility = View.GONE

            binding.createProfileBTNNext.visibility = View.VISIBLE
            binding.createProfileBTNFinish.visibility = View.GONE
            binding.createProfileBTNBack.visibility = View.GONE

            binding.createProfileBARProgBar1.progress = 1
            binding.createProfileBARProgBar2.progress = 0

            if (isEditMode) {
                binding.createProfileLBLHeader.text = "Edit your profile"
                binding.createProfileLBLSeconderyHeader.text = "Update your info"
            } else {
                binding.createProfileLBLHeader.text = "Create your profile"
                binding.createProfileLBLSeconderyHeader.text = "This helps other musicians find you"
            }
        }

        binding.createProfileBTNFinish.setOnClickListener {
            if (isEditMode) saveProfileEditMode() else saveProfileCreateMode()
        }

        placePicker = PlacePicker(
            caller = this,
            context = this,
            locationInput = binding.createProfileINCLUDEPage1.createProfileStep1INPUTLocation
        ) { loc ->
            selectedLocation = loc
        }
        placePicker.bindInput()
    }

    // Loads current user data from the database to fill the form when in edit mode.
    private fun prefillIfEditMode() {
        if (!isEditMode) return

        val uid = auth.currentUser?.uid ?: return

        db.child("users").child(uid).get()
            .addOnSuccessListener { snap ->
                val firstName = snap.child("firstName").getValue(String::class.java).orEmpty()
                val lastNameVal = snap.child("lastName").getValue(String::class.java).orEmpty()
                val stageName = snap.child("stageName").getValue(String::class.java).orEmpty()
                val location = snap.child("location").getValue(String::class.java).orEmpty()
                val instruments = snap.child("instruments").children.mapNotNull { it.getValue(String::class.java) }.toSet()
                val genres = snap.child("genres").children.mapNotNull { it.getValue(String::class.java) }.toSet()
                val skillLevel = snap.child("skillLevel").getValue(String::class.java).orEmpty()
                val about = snap.child("about").getValue(String::class.java).orEmpty()

                binding.createProfileINCLUDEPage1.createProfileStep1INPUTFirstName.setText(firstName)
                binding.createProfileINCLUDEPage1.createProfileStep1INPUTLastName.setText(lastNameVal)
                binding.createProfileINCLUDEPage1.createProfileStep1INPUTStageName.setText(stageName)
                binding.createProfileINCLUDEPage1.createProfileStep1INPUTLocation.setText(location)
                binding.createProfileINCLUDEPage1.createProfileStep1INPUTAbout.setText(about)

                setCheckedByText(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPInstruments, instruments)
                setCheckedByText(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPGenres, genres)
                setSingleCheckedByText(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPSkill, skillLevel)
            }
    }

    // Marks multiple chips as checked if their text matches the provided set of strings.
    private fun setCheckedByText(group: ChipGroup, selected: Set<String>) {
        for (i in 0 until group.childCount) {
            val v = group.getChildAt(i)
            if (v is Chip) {
                v.isChecked = selected.contains(v.text.toString())
            }
        }
    }

    // Selects a single chip within a group that matches the specified text.
    private fun setSingleCheckedByText(group: ChipGroup, selectedOne: String) {
        for (i in 0 until group.childCount) {
            val v = group.getChildAt(i)
            if (v is Chip) {
                v.isChecked = v.text.toString().equals(selectedOne, ignoreCase = true)
            }
        }
    }

    // Validates inputs and creates a new user record in the database.
    private fun saveProfileCreateMode() {
        val firebaseUser = auth.currentUser ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val firstName = binding.createProfileINCLUDEPage1.createProfileStep1INPUTFirstName.text.toString().trim()
        val lastName  = binding.createProfileINCLUDEPage1.createProfileStep1INPUTLastName.text.toString().trim()
        val stageName = binding.createProfileINCLUDEPage1.createProfileStep1INPUTStageName.text.toString().trim()
        val location  = binding.createProfileINCLUDEPage1.createProfileStep1INPUTLocation.text.toString().trim()
        val about = binding.createProfileINCLUDEPage1.createProfileStep1INPUTAbout.text.toString().trim()
        val skillLevelList = getSelectedChips(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPSkill)
        val instruments    = getSelectedChips(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPInstruments)
        val genres         = getSelectedChips(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPGenres)

        if (firstName.isBlank() || lastName.isBlank() || location.isBlank() ||
            skillLevelList.isEmpty() || instruments.isEmpty() || genres.isEmpty()
        ) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userNode = User(
            profileCompleted = true,
            firstName = firstName,
            lastName = lastName,
            stageName = stageName,
            location = location,
            about = about,
            skillLevel = skillLevelList.joinToString(),
            instruments = instruments,
            genres = genres,
            email = firebaseUser.email ?: "",
            profilePhotoUrl = firebaseUser.photoUrl?.toString() ?: "",
            createdAt = System.currentTimeMillis()
        )

        userRepo.saveCompletedProfile(firebaseUser.uid, userNode) { ok, err ->
            if (ok) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Save failed: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Validates updated fields and applies changes to an existing database user record.
    private fun saveProfileEditMode() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val firstName = binding.createProfileINCLUDEPage1.createProfileStep1INPUTFirstName.text.toString().trim()
        val lastName  = binding.createProfileINCLUDEPage1.createProfileStep1INPUTLastName.text.toString().trim()
        val stageName = binding.createProfileINCLUDEPage1.createProfileStep1INPUTStageName.text.toString().trim()
        val location  = binding.createProfileINCLUDEPage1.createProfileStep1INPUTLocation.text.toString().trim()
        val about = binding.createProfileINCLUDEPage1.createProfileStep1INPUTAbout.text.toString().trim()
        val skillLevelList = getSelectedChips(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPSkill)
        val instruments    = getSelectedChips(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPInstruments)
        val genres         = getSelectedChips(binding.createProfileINCLUDEPage2.createProfileStep2CHIPGROUPGenres)

        if (firstName.isBlank() || lastName.isBlank() || location.isBlank() ||
            skillLevelList.isEmpty() || instruments.isEmpty() || genres.isEmpty()
        ) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val updates: Map<String, Any?> = mapOf(
            "profileCompleted" to true,
            "firstName" to firstName,
            "lastName" to lastName,
            "stageName" to stageName,
            "about" to about,
            "location" to location,
            "skillLevel" to skillLevelList.joinToString(),
            "instruments" to instruments,
            "genres" to genres
        )

        db.child("users").child(uid).updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Iterates through a chip group to collect the text of all selected items.
    private fun getSelectedChips(group: ChipGroup): List<String> {
        val selected = mutableListOf<String>()
        for (id in group.checkedChipIds) {
            val chip = group.findViewById<Chip>(id)
            selected.add(chip.text.toString())
        }
        return selected
    }
}
