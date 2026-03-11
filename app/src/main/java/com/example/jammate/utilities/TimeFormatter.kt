package com.example.jammate.utilities

import java.util.concurrent.TimeUnit

object TimeFormatter {
    fun formatTime(lengthInMinutes: Int): String{
        var hours = lengthInMinutes / 60
        var minutes = lengthInMinutes % 60
        return buildString {
            append(String.format(locale = null, format = "%02dH", hours))
            append(" ")
            append(String.format(locale = null, format = "%02dM", minutes))
        }
    }

    fun formatElapsedTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            diff < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
            else -> {
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp))
            }
        }
    }
}