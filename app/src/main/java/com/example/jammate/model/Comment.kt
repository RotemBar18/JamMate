package com.example.jammate.model

data class Comment(
    var commentId: String = "",
    var postId: String = "",
    var ownerId: String = "",
    var ownerName: String = "",
    var ownerPhotoUrl: String = "",
    var text: String = "",
    var createdAt: Long = 0L
)