package com.example.jammate.model

data class User(
    var profileCompleted: Boolean = false,

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

    var posts: List<String> = emptyList()
)
