package com.example.jammate.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class MediaManager private constructor() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    companion object {
        val instance: MediaManager by lazy { MediaManager() }
    }

    fun uploadProfilePhoto(uri: Uri, onResult: (Boolean, String?, String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResult(false, null, "You must be logged in to upload a photo")
        val photoRef = storage.child("users/$userId/profile.jpg")

        photoRef.putFile(uri).addOnSuccessListener {
            photoRef.downloadUrl.addOnSuccessListener { url ->
                onResult(true, url.toString(), null)
            }.addOnFailureListener { e -> onResult(false, null, e.message) }
        }.addOnFailureListener { e -> onResult(false, null, e.message) }
    }

    fun saveProfilePhotoUrlToUser(photoUrl: String, onResult: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResult(false, "Authentication required")
        
        db.child("users").child(userId).child("profilePhotoUrl").setValue(photoUrl)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun removeProfilePhoto(onResult: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResult(false, "You need to be logged in to remove your photo")

        db.child("users").child(userId).child("profilePhotoUrl").setValue("").addOnSuccessListener {
            storage.child("users/$userId/profile.jpg").delete().addOnSuccessListener {
                onResult(true, null) 
            }.addOnFailureListener { 
                onResult(true, null) 
            }
        }.addOnFailureListener { e -> onResult(false, e.message) }
    }
}