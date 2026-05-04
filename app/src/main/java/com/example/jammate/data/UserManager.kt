package com.example.jammate.data

import com.example.jammate.model.Notification
import com.example.jammate.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class UserManager(
    private val db: DatabaseReference = FirebaseDatabase.getInstance().reference
) {

    companion object {
        val instance: UserManager by lazy { UserManager() }
    }

    fun saveCompletedProfile(userId: String, user: User, onResult: (Boolean, String?) -> Unit) {
        user.profileCompleted = true
        db.child("users").child(userId)
            .setValue(user)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun fetchUser(userId: String, onResult: (Boolean, User?, String?) -> Unit) {
        db.child("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                val user = snapshot.getValue(User::class.java)
                onResult(user != null, user, if (user == null) "Profile not found" else null)
            }
            .addOnFailureListener { e ->
                onResult(false, null, e.message)
            }
    }

    fun toggleFollow(userId: String, onResult: (Boolean, String?, Boolean) -> Unit) {
        val myUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return onResult(false, "You need to be logged in", false)
        if (myUserId == userId) return onResult(false, "You can't follow yourself", false)

        val followRef = db.child("userFollowing").child(myUserId).child(userId)
        
        followRef.get().addOnSuccessListener { snapshot ->
            val isNowFollowing = !snapshot.exists()

            val updates = hashMapOf<String, Any?>(
                "/userFollowing/$myUserId/$userId" to if (isNowFollowing) true else null,
                "/userFollowers/$userId/$myUserId" to if (isNowFollowing) true else null
            )

            db.updateChildren(updates).addOnSuccessListener {
                if (isNowFollowing) sendFollowNotification(myUserId, userId)
                onResult(true, null, isNowFollowing) 
            }.addOnFailureListener { e -> onResult(false, e.message, !isNowFollowing) }
        }.addOnFailureListener { e -> onResult(false, e.message, false) }
    }

    private fun sendFollowNotification(senderId: String, receiverId: String) {
        fetchUser(senderId) { success, user, _ ->
            if (success && user != null) {
                NotificationManager.instance.sendNotification(
                    Notification(
                        type = "follow",
                        senderId = senderId,
                        senderName = user.stageName.ifBlank { "${user.firstName} ${user.lastName}" },
                        senderPhotoUrl = user.profilePhotoUrl,
                        receiverId = receiverId,
                        message = "started following you"
                    )
                )
            }
        }
    }

    fun fetchFollowersCount(userId: String, onResult: (Int) -> Unit) {
        db.child("userFollowers").child(userId).get()
            .addOnSuccessListener { onResult(it.childrenCount.toInt()) }
            .addOnFailureListener { onResult(0) }
    }

    fun fetchMultipleUsers(userIds: List<String>, onResult: (Map<String, User>) -> Unit) {
        if (userIds.isEmpty()) return onResult(emptyMap())
        
        val result = mutableMapOf<String, User>()
        var pending = userIds.size
        
        userIds.forEach { userId ->
            fetchUser(userId) { success, user, _ ->
                if (success && user != null) result[userId] = user
                if (--pending == 0) onResult(result)
            }
        }
    }

    fun fetchFollowStatus(userIds: List<String>, currentUid: String, onDone: (Set<String>) -> Unit) {
        if (userIds.isEmpty()) return onDone(emptySet())
        
        val followed = mutableSetOf<String>()
        var pending = userIds.size
        
        userIds.forEach { userId ->
            db.child("userFollowing").child(currentUid).child(userId).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) followed.add(userId)
                if (--pending == 0) onDone(followed)
            }.addOnFailureListener {
                if (--pending == 0) onDone(followed)
            }
        }
    }
}
