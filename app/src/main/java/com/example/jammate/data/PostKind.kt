package com.example.jammate.data

import com.example.jammate.model.Post
import com.example.jammate.utilities.Constants

enum class PostKind { NORMAL, MEMBER, JAM, UNKNOWN }

fun Post.kind(): PostKind = when (type) {
    Constants.PostTypes.NORMAL_POST -> PostKind.NORMAL
    Constants.PostTypes.BAND_MEMBER -> PostKind.MEMBER
    Constants.PostTypes.JAM_SESSION -> PostKind.JAM
    else -> PostKind.UNKNOWN
}

data class MediaInfo(
    val hasMedia: Boolean,
    val isVideo: Boolean,
    val url: String
)

fun Post.mediaInfo(): MediaInfo {
    val url = mediaUrl?.trim().orEmpty()
    val hasMedia = url.isNotBlank() && !url.equals("null", true)
    val isVideo = hasMedia && mediaType?.trim().equals("video", true)
    
    return MediaInfo(
        hasMedia = hasMedia, 
        isVideo = isVideo, 
        url = url
    )
}
