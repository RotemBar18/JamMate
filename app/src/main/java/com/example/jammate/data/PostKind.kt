package com.example.jammate.data

import com.example.jammate.model.Post
import com.example.jammate.utilities.Constants

// This enum defines the different categories of posts available in the app.
enum class PostKind { NORMAL, MEMBER, JAM, UNKNOWN }

// This function identifies the category of a post based on its type string.
fun Post.kind(): PostKind {
    return when (type) {
        Constants.PostTypes.NORMAL_POST -> PostKind.NORMAL
        Constants.PostTypes.BAND_MEMBER -> PostKind.MEMBER
        Constants.PostTypes.JAM_SESSION -> PostKind.JAM
        else -> PostKind.UNKNOWN
    }
}

// This data class holds processed information about a post's media content.
data class MediaInfo(
    val hasMedia: Boolean,
    val isVideo: Boolean,
    val url: String
)

// This function extracts and categorizes the media information from a post record.
fun Post.mediaInfo(): MediaInfo {
    val url = mediaUrl?.trim().orEmpty()
    val hasMedia = url.isNotBlank() && !url.equals("null", true)
    val isVideo = hasMedia && mediaType?.trim().equals("video", true)
    return MediaInfo(hasMedia = hasMedia, isVideo = isVideo, url = url)
}
