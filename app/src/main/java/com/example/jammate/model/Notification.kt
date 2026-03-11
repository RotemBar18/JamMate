package com.example.jammate.model

data class Notification(
    var notificationId: String = "",
    var type: String = "", // "like", "comment", "apply", "coming"
    var senderId: String = "",
    var senderName: String = "",
    var senderPhotoUrl: String = "",
    var receiverId: String = "",
    var postId: String = "",
    var postType: String = "",
    var timestamp: Long = 0L,
    var isRead: Boolean = false,
    var message: String = ""
)
