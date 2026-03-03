package com.example.jammate.utilities

import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class MediaPicker(
    caller: ActivityResultCaller,
    private val onPicked: (Uri, String) -> Unit
) {
    private val pickImage: ActivityResultLauncher<String> =
        caller.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) onPicked(uri, "image")
        }

    private val pickVideo: ActivityResultLauncher<String> =
        caller.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) onPicked(uri, "video")
        }

    fun openImage() = pickImage.launch("image/*")
    fun openVideo() = pickVideo.launch("video/*")

    fun openProfileImage() = openImage()
}