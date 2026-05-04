package com.example.jammate.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.jammate.App.Companion.toast
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

class LoginActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private lateinit var binding: ActivityLoginBinding

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.loginBTNSignIn.setOnClickListener {
            showAuthModal()
        }

        if (FirebaseAuth.getInstance().currentUser != null) {
            routeAfterAuth()
        }
    }

    private fun showAuthModal() {
        val dialog = BottomSheetDialog(this)
        val modalBinding = ModalAuthOptionsBinding.inflate(layoutInflater)
        dialog.setContentView(modalBinding.root)

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

    private fun signIn(provider: AuthUI.IdpConfig) {
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setLogo(R.drawable.ic_jam_session)
            .setAvailableProviders(listOf(provider))
            .setTheme(R.style.Theme_JamMate)
            .build()
        
        signInLauncher.launch(signInIntent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            routeAfterAuth()
        } else if (result.resultCode != RESULT_CANCELED) {
            Toast.makeText(this, "Error: Authentication failed!", Toast.LENGTH_LONG).show()
        }
    }

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

    private fun routeAfterAuth() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val ref = db.child("users").child(uid).child("profileCompleted")

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
                toast( "Database access failed: ${e.message}")
                transactToNextScreen(Constants.Activities.CREATE_PROFILE)
            }
    }
}
