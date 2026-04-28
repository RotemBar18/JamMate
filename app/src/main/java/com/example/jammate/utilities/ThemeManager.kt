package com.example.jammate.utilities

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import androidx.core.content.edit


object ThemeManager {
    private const val PREFS_NAME = "jam_mate_prefs"
    private const val KEY_THEME = "theme_mode"

    private fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    private fun getThemeKeyForCurrentUser(): String? {
        val userId = getCurrentUserId()
        return if (userId != null) {
            "${KEY_THEME}_$userId"
        } else {
            null
        }
    }

    fun applyTheme(context: Context) {
        val key = getThemeKeyForCurrentUser()
        val mode = if (key != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(key, AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun saveTheme(context: Context, mode: Int) {
        val key = getThemeKeyForCurrentUser()
        if (key != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { putInt(key, mode) }
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getTheme(context: Context): Int {
        val key = getThemeKeyForCurrentUser()
        return if (key != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getInt(key, AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
    }
}