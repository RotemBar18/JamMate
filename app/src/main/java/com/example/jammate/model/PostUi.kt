package com.example.jammate.model

data class PostUi(
    val post: Post,
    val distanceKm: Double,
    val owner:User = User(),
    val ownerPhotoUrl: String = "",
    // UI state (NOT stored in DB)
    val isLikedByMe: Boolean = false,
    val isAppliedByMe: Boolean = false,
    val isComingByMe: Boolean = false,
    val isFollowingOwner: Boolean = false
) {
    val searchableText: String
        get() = buildString {
            append(post.description).append(" ")
            append(post.type).append(" ")
            append(post.instrument.joinToString(" ")).append(" ")
            append(post.genre.joinToString(" ")).append(" ")
            append(post.location?.name ?: "").append(" ")
            append(owner.firstName)
            append(owner.lastName)
            append(owner.stageName)
        }.lowercase()
}