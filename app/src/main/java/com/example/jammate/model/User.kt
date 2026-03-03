package com.example.jammate.model

data class User(
    // flow flag (root)
    var profileCompleted: Boolean = false,

    // flat user fields (root)
    var firstName: String = "",
    var lastName: String = "",
    var stageName: String = "",
    var location: String = "",
    var about: String = "",

    var skillLevel: String = "",
    var instruments: List<String> = emptyList(),
    var genres: List<String> = emptyList(),

    var email: String = "",
    var profilePhotoUrl: String = "",
    var createdAt: Long = 0L,

    // raterUid -> rating
    var ratingsByUser: Map<String, Double> = emptyMap(),

    // postId list (for now)
    var posts: List<String> = emptyList()
)
