package com.example.jammate.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

// This class coordinates the uploading and management of profile-related media files in Firebase Storage.
class MediaManager private constructor() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    companion object {
        // Provides a single point of access to the MediaManager instance.
        val instance: MediaManager by lazy { MediaManager() }
    }

    // Transfers a selected image file to the user's specific storage folder and returns the public download link.
    fun uploadProfilePhoto(uri: Uri, onResult: (Boolean, String?, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: run { 
            onResult(false, null, "User not logged in")
            return 
        }
        val ref = storage.child("users/$uid/profile.jpg")

        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                onResult(true, url.toString(), null)
            }.addOnFailureListener { e -> onResult(false, null, e.message) }
        }.addOnFailureListener { e -> onResult(false, null, e.message) }
    }

    // Records the permanent storage link for a profile photo within the user's database record.
    fun saveProfilePhotoUrlToUser(photoUrl: String, onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onResult(false, "User not logged in")
            return
        }
        db.child("users").child(uid).child("profilePhotoUrl").setValue(photoUrl)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    // Erases the profile photo record from the database and attempts to remove the file from physical storage.
    fun removeProfilePhoto(onResult: (Boolean, String?) -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            onResult(false, "User not logged in")
            return
        }

        db.child("users").child(uid).child("profilePhotoUrl").setValue("").addOnSuccessListener {
            storage.child("users/$uid/profile.jpg").delete().addOnSuccessListener { 
                onResult(true, null) 
            }.addOnFailureListener { 
                onResult(true, null) 
            }
        }.addOnFailureListener { e -> onResult(false, e.message) }
    }
}