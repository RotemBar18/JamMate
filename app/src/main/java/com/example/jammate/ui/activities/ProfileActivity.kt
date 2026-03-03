package com.example.jammate.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.jammate.R
import com.example.jammate.ui.fragments.ProfileFragment

class ProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val userId = intent.getStringExtra(EXTRA_USER_ID)
        
        if (savedInstanceState == null) {
            val fragment = ProfileFragment.newInstance(userId)
            supportFragmentManager.beginTransaction()
                .replace(R.id.profile_container, fragment)
                .commit()
        }
    }

    companion object {
        private const val EXTRA_USER_ID = "extra_user_id"

        fun start(context: Context, userId: String?) {
            val intent = Intent(context, ProfileActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
            }
            context.startActivity(intent)
        }
    }
}