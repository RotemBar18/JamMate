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
        if (notification.senderId == notification.receiverId) return // Don't notify yourself

        val id = db.child("notifications").child(notification.receiverId).push().key ?: return
        notification.notificationId = id
        notification.timestamp = System.currentTimeMillis()

        db.child("notifications").child(notification.receiverId).child(id).setValue(notification)
    }

    fun observeNotifications(uid: String, onUpdate: (List<Notification>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(Notification::class.java) }
                    .sortedByDescending { it.timestamp }
                onUpdate(list)
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("notifications").child(uid).addValueEventListener(listener)
        return listener
    }

    fun stopObserving(uid: String, listener: ValueEventListener) {
        db.child("notifications").child(uid).removeEventListener(listener)
    }

    fun markAsRead(uid: String, notificationId: String) {
        db.child("notifications").child(uid).child(notificationId).child("isRead").setValue(true)
    }

    fun deleteNotification(uid: String, notificationId: String) {
        db.child("notifications").child(uid).child(notificationId).removeValue()
    }
}
