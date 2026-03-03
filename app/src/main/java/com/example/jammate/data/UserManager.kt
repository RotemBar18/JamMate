package com.example.jammate.data

import com.example.jammate.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

// This class handles user-related data operations, such as profile management and social interactions.
class UserManager(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
) {

    companion object {
        val instance: UserManager by lazy { UserManager() }
    }

    // Marks a user profile as complete and saves the user details to the database.
    fun saveCompletedProfile(uid: String, user: User, onResult: (Boolean, String?) -> Unit) {
        user.profileCompleted = true
        db.child("users").child(uid)
            .setValue(user)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    // Retrieves user information from the database using their unique ID.
    fun fetchUser(uid: String, onResult: (Boolean, User?, String?) -> Unit) {
        db.child("users").child(uid).get()
            .addOnSuccessListener { snap ->
                val u = snap.getValue(User::class.java)
                onResult(u != null, u, if (u == null) "User not found" else null)
            }
            .addOnFailureListener { e ->
                onResult(false, null, e.message)
            }
    }

    // Toggles the follow status between the current user and a target user in the database.
    fun toggleFollow(targetUid: String, onResult: (Boolean, String?, Boolean) -> Unit) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return onResult(false, "User not logged in", false)
        if (currentUid == targetUid) return onResult(false, "You cannot follow yourself", false)

        val followingRef = db.child("userFollowing").child(currentUid).child(targetUid)
        followingRef.get().addOnSuccessListener { snap ->
            val shouldFollow = !snap.exists()

            val updates = hashMapOf<String, Any?>(
                "/userFollowing/$currentUid/$targetUid" to if (shouldFollow) true else null,
                "/userFollowers/$targetUid/$currentUid" to if (shouldFollow) true else null
            )

            db.updateChildren(updates).addOnSuccessListener { 
                onResult(true, null, shouldFollow) 
            }.addOnFailureListener { e -> onResult(false, e.message, !shouldFollow) }
        }
    }

    // Counts the total number of followers for a specific user.
    fun fetchFollowersCount(uid: String, onResult: (Int) -> Unit) {
        db.child("userFollowers").child(uid).get()
            .addOnSuccessListener { onResult(it.childrenCount.toInt()) }
            .addOnFailureListener { onResult(0) }
    }

    // Fetches multiple user records from the database in a batch-like manner.
    fun fetchMultipleUsers(uids: List<String>, onResult: (Map<String, User>) -> Unit) {
        if (uids.isEmpty()) return onResult(emptyMap())
        val result = mutableMapOf<String, User>()
        var remaining = uids.size
        uids.forEach { uid ->
            fetchUser(uid) { ok, user, _ ->
                if (ok && user != null) result[uid] = user
                if (--remaining == 0) onResult(result)
            }
        }
    }

    // Determines the following status for a list of users relative to the current user.
    fun fetchFollowStatus(targetUids: List<String>, currentUid: String, onDone: (Set<String>) -> Unit) {
        if (targetUids.isEmpty()) return onDone(emptySet())
        val followed = mutableSetOf<String>()
        var remaining = targetUids.size
        targetUids.forEach { uid ->
            db.child("userFollowing").child(currentUid).child(uid).get().addOnSuccessListener { snap ->
                if (snap.exists()) followed.add(uid)
                if (--remaining == 0) onDone(followed)
            }.addOnFailureListener {
                if (--remaining == 0) onDone(followed)
            }
        }
    }
}
