package com.example.jammate.data

import com.example.jammate.model.Notification
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class NotificationManager private constructor() {

    private val db = FirebaseDatabase.getInstance().reference

    companion object {
        val instance: NotificationManager by lazy { NotificationManager() }
    }

    fun sendNotification(notification: Notification) {
        if (notification.senderId == notification.receiverId) return

        val notificationId = db.child("notifications").child(notification.receiverId).push().key ?: return
        notification.notificationId = notificationId
        notification.timestamp = System.currentTimeMillis()

        db.child("notifications").child(notification.receiverId).child(notificationId).setValue(notification)
    }

    fun observeAndGetNotifications(userId: String, onUpdate: (List<Notification>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val notifications = snapshot.children.mapNotNull { it.getValue(Notification::class.java) }
                    .sortedByDescending { it.timestamp }
                onUpdate(notifications)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("notifications").child(userId).addValueEventListener(listener)
        return listener
    }

    fun markAllAsRead(userId: String) {
        db.child("notifications").child(userId).get().addOnSuccessListener { snapshot ->
            val updates = hashMapOf<String, Any>()
            snapshot.children.forEach { childSnapshot ->
                val notification = childSnapshot.getValue(Notification::class.java)
                if (notification?.readStatus == false) {
                    updates["${childSnapshot.key}/readStatus"] = true
                }
            }
            if (updates.isNotEmpty()) {
                db.child("notifications").child(userId).updateChildren(updates)
            }
        }
    }

    fun deleteNotification(userId: String, notificationId: String) {
        db.child("notifications").child(userId).child(notificationId).removeValue()
    }

    fun removeNotificationListener(userId: String, listener: ValueEventListener) {
        db.child("notifications").child(userId).removeEventListener(listener)
    }
}
