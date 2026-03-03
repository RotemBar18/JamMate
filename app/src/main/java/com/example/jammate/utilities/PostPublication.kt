package com.example.jammate.utilities

import android.net.Uri
import com.example.jammate.data.PostManager
import com.example.jammate.model.Post

class PostPublication(
    private val postManager: PostManager = PostManager.Companion.instance
) {

    fun publish(
        post: Post,
        mediaUri: Uri?,
        mediaType: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        postManager.createPost(post) { ok, postId, err ->
            if (!ok || postId == null) {
                onResult(false, err ?: "Failed creating post")
                return@createPost
            }

            // no media => done
            if (mediaUri == null || mediaType == null) {
                onResult(true, null)
                return@createPost
            }

            // upload media
            postManager.uploadPostMedia(postId, mediaUri, mediaType) { upOk, url, upErr ->
                if (!upOk || url == null) {
                    onResult(false, upErr ?: "Upload failed")
                    return@uploadPostMedia
                }

                // attach url/type
                postManager.attachMediaToPost(postId, url, mediaType) { attOk, attErr ->
                    if (!attOk) {
                        onResult(false, attErr ?: "Failed saving media url")
                        return@attachMediaToPost
                    }
                    onResult(true, null)
                }
            }
        }
    }
}