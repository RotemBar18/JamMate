package com.example.jammate.ui.fragments

import android.net.Uri
import android.view.View
import androidx.fragment.app.Fragment
import com.example.jammate.utilities.MediaPicker

class MediaPickerController(
    fragment: Fragment,
    private val preview: MediaPreviewView,
    private val addImageButton: View,
    private val addVideoButton: View
) {
    var pickedUri: Uri? = null
        private set
    var pickedType: String? = null
        private set

    private val picker = MediaPicker(fragment) { uri, type ->
        pickedUri = uri
        pickedType = type
        preview.setMedia(uri, type)
        preview.visibility = View.VISIBLE
    }

    init {
        addImageButton.setOnClickListener { picker.openImage() }
        addVideoButton.setOnClickListener { picker.openVideo() }
        preview.onRemove = {
            pickedUri = null
            pickedType = null
            preview.visibility = View.GONE
        }
    }

    fun clear() {
        pickedUri = null
        pickedType = null
        preview.clear()
    }
}