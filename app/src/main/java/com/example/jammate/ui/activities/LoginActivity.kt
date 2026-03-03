package com.example.jammate.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.jammate.R
import com.example.jammate.databinding.ActivityLoginBinding
import com.example.jammate.databinding.ModalAuthOptionsBinding
import com.example.jammate.utilities.Constants
import com.example.jammate.utilities.ThemeManager
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

// This activity manages the user authentication flow and provides a modern landing experience.
class LoginActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private lateinit var binding: ActivityLoginBinding

    // Handles the result of the sign-in intent and routes the user accordingly.
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract(),
    ) { res ->
        this.onSignInResult(res)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        
        enableEdgeToEdge()
        setContentView(binding.root)

        // Adjusts view padding to account for system bars while maintaining an immersive layout.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Sets up the landing page action to open the authentication options modal.
        binding.loginBTNSignIn.setOnClickListener {
            showAuthModal()
        }

        // Checks if a user is already authenticated to skip the sign-in screen.
        if (FirebaseAuth.getInstance().currentUser != null) {
            routeAfterAuth()
        }
    }

    // Displays a bottom sheet modal containing different authentication provider options.
    private fun showAuthModal() {
        val dialog = BottomSheetDialog(this)
        val modalBinding = ModalAuthOptionsBinding.inflate(layoutInflater)
        dialog.setContentView(modalBinding.root)

        // Launches sign-in flow for the selected provider.
        modalBinding.authModalBTNEmail.setOnClickListener {
            dialog.dismiss()
            signIn(AuthUI.IdpConfig.EmailBuilder().build())
        }

        modalBinding.authModalBTNGoogle.setOnClickListener {
            dialog.dismiss()
            signIn(AuthUI.IdpConfig.GoogleBuilder().build())
        }

        dialog.show()
    }

    // Configures and launches the Firebase UI authentication intent for a specific provider.
    private fun signIn(provider: AuthUI.IdpConfig) {
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setLogo(R.drawable.ic_jam_session)
            .setAvailableProviders(listOf(provider))
            .setTheme(R.style.Theme_JamMate)
            .build()
        
        signInLauncher.launch(signInIntent)
    }

    // Processes the authentication result and displays an error message if the sign-in fails.
    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            routeAfterAuth()
        } else if (result.resultCode != RESULT_CANCELED) {
            Toast.makeText(this, "Error: Authentication failed!", Toast.LENGTH_LONG).show()
        }
    }

    // Navigates the user to either the Main screen or the Profile Creation screen based on the provided class name.
    private fun transactToNextScreen(className: String) {
        val targetIntent = when (className) {
            Constants.Activities.MAIN -> Intent(this, MainActivity::class.java)
            Constants.Activities.CREATE_PROFILE -> Intent(this, CreateProfileActivity::class.java)
            else -> null
        }
        
        targetIntent?.let {
            startActivity(it)
            finish()
        }
    }

    // Verifies the user's profile status in the database and directs them to the appropriate next screen.
    private fun routeAfterAuth() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val ref = db.child("users").child(uid).child("profileCompleted")

        // Retrieves the profile completion flag to determine if the user needs to set up their profile.
        ref.get()
            .addOnSuccessListener { snapshot ->
                val completed = snapshot.getValue(Boolean::class.java)

                when (completed) {
                    true -> transactToNextScreen(Constants.Activities.MAIN)
                    false -> transactToNextScreen(Constants.Activities.CREATE_PROFILE)
                    null -> {
                        // Initializes the profile completion flag for new users before navigating to setup.
                        ref.setValue(false)
                            .addOnSuccessListener {
                                transactToNextScreen(Constants.Activities.CREATE_PROFILE)
                            }
                            .addOnFailureListener {
                                transactToNextScreen(Constants.Activities.CREATE_PROFILE)
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Database access failed: ${e.message}", Toast.LENGTH_LONG).show()
                transactToNextScreen(Constants.Activities.CREATE_PROFILE)
            }
    }
}
