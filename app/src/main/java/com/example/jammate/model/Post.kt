package com.example.jammate.model

data class Post(
    // Common
    var postId: String = "",
    var type: String = "",
    var createdAt: Long = 0L,
    var ownerId: String = "",
    var description: String = "",

    var location: LocationData? = null,

    var dateTime: Long? = null,
    var mediaUrl: String? = null,
    var mediaType: String? = null,

    var likesCount: Int = 0,
    var arrivalsCount: Int = 0,
    var applicationsCount: Int = 0,
    var commentsCount: Int = 0,

    // Band member
    var instrument: List<String> = emptyList(),
    var genre: List<String> = emptyList(),
    var skillLevel: String? = null
)