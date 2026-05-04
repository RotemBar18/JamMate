package com.example.jammate.data

import android.net.Uri
import com.example.jammate.model.Notification
import com.example.jammate.model.Post
import com.example.jammate.model.PostUi
import com.example.jammate.model.User
import com.example.jammate.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

class PostManager private constructor() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    companion object {
        val instance: PostManager by lazy { PostManager() }
    }

    fun getCurrentUid(): String? = auth.currentUser?.uid

    fun createPost(post: Post, onResult: (Boolean, String?, String?) -> Unit) {
        val validationError = validatePost(post)
        if (validationError != null) return onResult(false, null, validationError)

        val userId = getCurrentUid() ?: return onResult(false, null, "You need to be logged in to post")
        val postId = db.child("posts").push().key ?: return onResult(false, null, "Failed to create post ID")

        post.apply {
            this.postId = postId
            this.ownerId = userId
            this.createdAt = System.currentTimeMillis()
        }

        val updates = hashMapOf(
            "/posts/$postId" to post,
            "/postsByType/${post.type}/$postId" to post.createdAt,
            "/userPosts/$userId/$postId" to post.createdAt
        )

        db.updateChildren(updates).addOnSuccessListener { 
            onResult(true, postId, null) 
        }.addOnFailureListener { e -> onResult(false, null, e.message) }
    }

    fun deletePost(post: Post, onResult: (Boolean, String?) -> Unit) {
        val userId = getCurrentUid() ?: return onResult(false, "Authentication required")
        if (post.ownerId != userId) return onResult(false, "You can only delete your own posts")

        val postId = post.postId
        val updates = hashMapOf<String, Any?>(
            "/posts/$postId" to null,
            "/postsByType/${post.type}/$postId" to null,
            "/userPosts/$userId/$postId" to null,
            "/postLikes/$postId" to null,
            "/postComments/$postId" to null,
            "/jamArrivals/$postId" to null,
            "/memberApplications/$postId" to null
        )

        db.updateChildren(updates).addOnSuccessListener { 
            onResult(true, null) 
        }.addOnFailureListener { e -> onResult(false, e.message) }
    }

    private fun validatePost(post: Post): String? {
        if (post.description.isBlank()) return "Please add a description"
        if (post.type == Constants.PostTypes.JAM_SESSION && post.location == null) return "Jam sessions need a location"
        if (post.type == Constants.PostTypes.BAND_MEMBER) {
            if (post.genre.isEmpty()) return "Please select a genre"
            if (post.instrument.isEmpty()) return "Please select an instrument"
            if (post.skillLevel.isNullOrBlank()) return "Please select a skill level"
        }
        return null
    }

    fun uploadPostMedia(postId: String?, uri: Uri, mediaType: String, onResult: (Boolean, String?, String?) -> Unit) {
        val userId = getCurrentUid() ?: return onResult(false, null, "You must be logged in to upload media")
        val extension = if (mediaType == "video") "mp4" else "jpg"
        val ref = storage.child("postMedia/$userId/$postId.$extension")

        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url -> 
                onResult(true, url.toString(), null) 
            }.addOnFailureListener { e -> onResult(false, null, e.message) }
        }.addOnFailureListener { e -> onResult(false, null, e.message) }
    }

    fun attachMediaToPost(postId: String, url: String, type: String, onResult: (Boolean, String?) -> Unit) {
        val updates = mapOf("/posts/$postId/mediaUrl" to url, "/posts/$postId/mediaType" to type)
        db.updateChildren(updates).addOnSuccessListener { onResult(true, null) }.addOnFailureListener { e -> onResult(false, e.message) }
    }

    fun togglePostAction(postId: String, tag: String, onResult: (Boolean, String?, Boolean) -> Unit) {
        val userId = getCurrentUid() ?: return onResult(false, "You must be logged in", false)

        val (dbNode, countField) = when (tag) {
            Constants.PostActions.LIKE -> "postLikes" to "likesCount"
            Constants.PostActions.COMING -> "jamArrivals" to "arrivalsCount"
            Constants.PostActions.APPLY -> "memberApplications" to "applicationsCount"
            else -> return onResult(false, "Unsupported action", false)
        }

        val actionRef = db.child(dbNode).child(postId).child(userId)

        actionRef.get().addOnSuccessListener { snapshot ->
            val isActivating = !snapshot.exists()
            val toggleTask = if (isActivating) actionRef.setValue(true) else actionRef.removeValue()

            toggleTask.addOnSuccessListener {
                updateCounter(db.child("posts").child(postId).child(countField), if (isActivating) 1 else -1) { ok, err ->
                    if (ok && isActivating) sendActionNotification(postId, userId, tag)
                    onResult(ok, err, isActivating)
                }
            }.addOnFailureListener { onResult(false, it.message, false) }
        }.addOnFailureListener { onResult(false, it.message, false) }
    }

    private fun sendActionNotification(postId: String, senderId: String, tag: String) {
        db.child("posts").child(postId).get().addOnSuccessListener { snapshot ->
            val post = snapshot.getValue(Post::class.java) ?: return@addOnSuccessListener
            
            UserManager.instance.fetchUser(senderId) { ok, user, _ ->
                if (!ok || user == null) return@fetchUser
                
                val text = when (tag) {
                    Constants.PostActions.LIKE -> "liked your post"
                    Constants.PostActions.COMING -> "is coming to your jam"
                    Constants.PostActions.APPLY -> "applied to your band"
                    else -> ""
                }
                
                NotificationManager.instance.sendNotification(
                    Notification(
                        type = tag.lowercase(),
                        senderId = senderId,
                        senderName = user.stageName.ifBlank { "${user.firstName} ${user.lastName}" },
                        senderPhotoUrl = user.profilePhotoUrl,
                        receiverId = post.ownerId,
                        postId = postId,
                        postType = post.type,
                        message = text
                    )
                )
            }
        }
    }

    private fun updateCounter(ref: DatabaseReference, delta: Int, onResult: (Boolean, String?) -> Unit) {
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val cur = (data.value as? Long)?.toInt() ?: 0
                data.value = maxOf(0, cur + delta)
                return Transaction.success(data)
            }
            override fun onComplete(e: DatabaseError?, b: Boolean, s: DataSnapshot?) {
                onResult(e == null, e?.message)
            }
        })
    }

    fun fetchPostsPaginated(pageSize: Int, startAfter: Long?, onResult: (Boolean, List<Post>, String?) -> Unit) {
        var query = db.child("posts").orderByChild("createdAt")

        if (startAfter != null) {
            query = query.endAt((startAfter - 1).toDouble())
        }

        query.limitToLast(pageSize).get().addOnSuccessListener { snapshot ->
            val posts = snapshot.children.mapNotNull { it.getValue(Post::class.java)?.apply { postId = it.key ?: "" } }.reversed()
            onResult(true, posts, null)
        }.addOnFailureListener { e -> onResult(false, emptyList(), e.message) }
    }

    fun fetchLikedPostIds(postIds: List<String>, onResult: (Set<String>) -> Unit) {
        val userId = getCurrentUid() ?: return onResult(emptySet())
        val liked = mutableSetOf<String>()
        var pending = postIds.size
        if (pending == 0) return onResult(liked)

        postIds.forEach { postId ->
            db.child("postLikes").child(postId).child(userId).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) liked.add(postId)
                if (--pending == 0) onResult(liked)
            }.addOnFailureListener { if (--pending == 0) onResult(liked) }
        }
    }

    fun fetchMyPostIds(root: String, userId: String, onDone: (Set<String>) -> Unit) {
        db.child(root).get().addOnSuccessListener { snapshot ->
            onDone(snapshot.children.filter { it.hasChild(userId) }.mapNotNull { it.key }.toSet())
        }.addOnFailureListener { onDone(emptySet()) }
    }

    fun fetchUserPostUis(ownerId: String, owner: User, onDone: (Boolean, List<PostUi>, String?) -> Unit) {
        db.child("userPosts").child(ownerId).get().addOnSuccessListener { snapshot ->
            val ids = snapshot.children.mapNotNull { it.key }
            if (ids.isEmpty()) return@addOnSuccessListener onDone(true, emptyList(), null)

            val posts = mutableListOf<Post>()
            var pending = ids.size
            ids.forEach { postId ->
                db.child("posts").child(postId).get().addOnSuccessListener { postSnap ->
                    postSnap.getValue(Post::class.java)?.let { posts.add(it.apply { this.postId = postSnap.key ?: postId }) }
                    if (--pending == 0) {
                        val uiList = posts.sortedByDescending { it.createdAt }.map { PostUi(it, 0.0, owner, owner.profilePhotoUrl) }
                        onDone(true, uiList, null)
                    }
                }.addOnFailureListener { if (--pending == 0) onDone(true, posts.map { PostUi(it, 0.0, owner, owner.profilePhotoUrl) }, null) }
            }
        }.addOnFailureListener { e -> onDone(false, emptyList(), e.message) }
    }

    fun prepareViewerData(posts: List<Post>, onDone: (List<PostUi>) -> Unit) {
        val currentUserId = getCurrentUid() ?: ""
        val ownerIds = posts.map { it.ownerId }.distinct()
        
        UserManager.instance.fetchMultipleUsers(ownerIds) { usersById ->
            val initialUiList = posts.mapNotNull { post ->
                val owner = usersById[post.ownerId] ?: return@mapNotNull null
                PostUi(post = post, distanceKm = 0.0, owner = owner, ownerPhotoUrl = owner.profilePhotoUrl)
            }

            val postIds = initialUiList.map { it.post.postId }
            fetchLikedPostIds(postIds) { likedIds ->
                fetchMyPostIds("jamArrivals", currentUserId) { arrivalIds ->
                    fetchMyPostIds("memberApplications", currentUserId) { applyIds ->
                        UserManager.instance.fetchFollowStatus(ownerIds, currentUserId) { followedOwnerIds ->
                            val fullUiList = initialUiList.map { ui ->
                                ui.copy(
                                    isLikedByMe = likedIds.contains(ui.post.postId),
                                    isComingByMe = arrivalIds.contains(ui.post.postId),
                                    isAppliedByMe = applyIds.contains(ui.post.postId),
                                    isFollowingOwner = followedOwnerIds.contains(ui.post.ownerId)
                                )
                            }
                            onDone(fullUiList)
                        }
                    }
                }
            }
        }
    }


    fun fetchPostsByIds(postIds: List<String>, onDone: (List<Post>) -> Unit) {
        val posts = mutableListOf<Post>()
        var pending = postIds.size
        if (pending == 0) return onDone(posts)
        postIds.forEach { postId ->
            db.child("posts").child(postId).get().addOnSuccessListener { snapshot ->
                snapshot.getValue(Post::class.java)?.let { posts.add(it.apply { this.postId = snapshot.key ?: postId }) }
                if (--pending == 0) onDone(posts)
            }.addOnFailureListener { if (--pending == 0) onDone(posts) }
        }
    }

}