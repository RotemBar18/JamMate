package com.example.jammate.data

import android.net.Uri
import com.example.jammate.model.Comment
import com.example.jammate.model.Post
import com.example.jammate.model.PostUi
import com.example.jammate.model.User
import com.example.jammate.utilities.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

// This class manages all post-related operations, including creation, deletion, and real-time updates.
class PostManager private constructor() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    companion object {
        private const val TAG = "PostManager"
        val instance: PostManager by lazy { PostManager() }
    }

    // Retrieves the unique ID of the currently logged-in user.
    fun getCurrentUid(): String? = auth.currentUser?.uid

    // Validates and saves a new post to the database under multiple organizational nodes.
    fun createPost(post: Post, onResult: (Boolean, String?, String?) -> Unit) {
        val error = validatePost(post)
        if (error != null) return onResult(false, null, error)

        val uid = getCurrentUid() ?: return onResult(false, null, "User not logged in")
        val postId = db.child("posts").push().key ?: return onResult(false, null, "ID failure")

        post.apply {
            this.postId = postId
            this.ownerId = uid
            this.createdAt = System.currentTimeMillis()
        }

        val updates = hashMapOf<String, Any>(
            "/posts/$postId" to post,
            "/postsByType/${post.type}/$postId" to post.createdAt,
            "/userPosts/$uid/$postId" to post.createdAt
        )

        db.updateChildren(updates).addOnSuccessListener { 
            onResult(true, postId, null) 
        }.addOnFailureListener { e -> onResult(false, null, e.message) }
    }

    // Removes a post and all its associated data like likes and comments from the database.
    fun deletePost(post: Post, onResult: (Boolean, String?) -> Unit) {
        val uid = getCurrentUid() ?: return onResult(false, "No user")
        if (post.ownerId != uid) return onResult(false, "Permission denied")

        val postId = post.postId
        val updates = hashMapOf<String, Any?>(
            "/posts/$postId" to null,
            "/postsByType/${post.type}/$postId" to null,
            "/userPosts/$uid/$postId" to null,
            "/postLikes/$postId" to null,
            "/postComments/$postId" to null,
            "/jamArrivals/$postId" to null,
            "/memberApplications/$postId" to null
        )

        db.updateChildren(updates).addOnSuccessListener { 
            onResult(true, null) 
        }.addOnFailureListener { e -> onResult(false, e.message) }
    }

    // Checks if the post has all required fields based on its specific type.
    private fun validatePost(post: Post): String? {
        if (post.description.isBlank()) return "Description is required"
        if (post.type == Constants.PostTypes.JAM_SESSION && post.location == null) return "Location required for Jam"
        if (post.type == Constants.PostTypes.BAND_MEMBER) {
            if (post.genre.isEmpty()) return "Genre required"
            if (post.instrument.isEmpty()) return "Instrument required"
            if (post.skillLevel.isNullOrBlank()) return "Skill level required"
        }
        return null
    }

    // Transfers post media to storage and returns the resulting download URL.
    fun uploadPostMedia(postId: String?, uri: Uri, mediaType: String, onResult: (Boolean, String?, String?) -> Unit) {
        val uid = getCurrentUid() ?: return onResult(false, null, "Not logged in")
        val ext = if (mediaType == "video") "mp4" else "jpg"
        val ref = storage.child("postMedia/$uid/$postId.$ext")

        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url -> 
                onResult(true, url.toString(), null) 
            }.addOnFailureListener { e -> onResult(false, null, e.message) }
        }.addOnFailureListener { e -> onResult(false, null, e.message) }
    }

    // Updates a specific post record with the permanent link to its media file.
    fun attachMediaToPost(postId: String, url: String, type: String, onResult: (Boolean, String?) -> Unit) {
        val updates = mapOf("/posts/$postId/mediaUrl" to url, "/posts/$postId/mediaType" to type)
        db.updateChildren(updates).addOnSuccessListener { onResult(true, null) }.addOnFailureListener { e -> onResult(false, e.message) }
    }

    // Toggles social actions like likes or applications and updates the corresponding counters.
    fun togglePostAction(postId: String, tag: String, onResult: (Boolean, String?, Boolean) -> Unit) {
        val uid = getCurrentUid() ?: return onResult(false, "No user", false)

        val (root, field) = when (tag) {
            Constants.PostActions.LIKE -> "postLikes" to "likesCount"
            Constants.PostActions.COMING -> "jamArrivals" to "arrivalsCount"
            Constants.PostActions.APPLY -> "memberApplications" to "applicationsCount"
            else -> return onResult(false, "Invalid action", false)
        }

        val actionRef = db.child(root).child(postId).child(uid)
        val counterRef = db.child("posts").child(postId).child(field)

        actionRef.get().addOnSuccessListener { snap ->
            val isNowOn = !snap.exists()
            val task = if (isNowOn) actionRef.setValue(true) else actionRef.removeValue()

            task.addOnSuccessListener {
                updateCounter(counterRef, if (isNowOn) 1 else -1) { ok, err -> 
                    onResult(ok, err, isNowOn) 
                }
            }.addOnFailureListener { e -> onResult(false, e.message, false) }
        }
    }

    // Adds a new comment to a post and increments the total comment count.
    fun addComment(postId: String, text: String, user: User, onResult: (Boolean, String?) -> Unit) {
        val uid = getCurrentUid() ?: return onResult(false, "No user")
        val commentId = db.child("postComments").child(postId).push().key ?: return onResult(false, "ID failure")

        val comment = Comment(commentId, postId, uid, user.stageName.ifBlank { "${user.firstName} ${user.lastName}" }, user.profilePhotoUrl, text, System.currentTimeMillis())
        db.child("postComments").child(postId).child(commentId).setValue(comment).addOnSuccessListener {
            updateCounter(db.child("posts").child(postId).child("commentsCount"), 1) { ok, err -> onResult(ok, err) }
        }.addOnFailureListener { e -> onResult(false, e.message) }
    }

    // Starts listening for real-time changes to the comments list of a specific post.
    fun observeComments(postId: String, onUpdate: (List<Comment>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                onUpdate(s.children.mapNotNull { it.getValue(Comment::class.java) }.reversed())
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        db.child("postComments").child(postId).orderByChild("createdAt").addValueEventListener(listener)
        return listener
    }

    // Stops an active real-time listener for a post's comments.
    fun stopObservingComments(postId: String, listener: ValueEventListener) {
        db.child("postComments").child(postId).removeEventListener(listener)
    }

    // Atomically increments or decrements a numerical counter in the database.
    private fun updateCounter(ref: com.google.firebase.database.DatabaseReference, delta: Int, onResult: (Boolean, String?) -> Unit) {
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: com.google.firebase.database.MutableData): Transaction.Result {
                val cur = (data.value as? Long)?.toInt() ?: 0
                data.value = maxOf(0, cur + delta)
                return Transaction.success(data)
            }
            override fun onComplete(e: com.google.firebase.database.DatabaseError?, b: Boolean, s: DataSnapshot?) {
                onResult(e == null, e?.message)
            }
        })
    }

    // Retrieves all available posts from the main post repository.
    fun fetchAllPosts(onResult: (Boolean, List<Post>, String?) -> Unit) {
        db.child("posts").get().addOnSuccessListener { snap ->
            onResult(true, snap.children.mapNotNull { it.getValue(Post::class.java)?.apply { postId = it.key ?: "" } }, null)
        }.addOnFailureListener { e -> onResult(false, emptyList(), e.message) }
    }

    // Fetches posts with pagination, ordered by creation time.
    fun fetchPostsPaginated(pageSize: Int, startAfter: Long?, onResult: (Boolean, List<Post>, String?) -> Unit) {
        var query = db.child("posts").orderByChild("createdAt")

        // If startAfter is provided, we fetch posts created at or before that timestamp.
        // We subtract 1 from startAfter to avoid fetching the same last post again.
        if (startAfter != null) {
            query = query.endAt((startAfter - 1).toDouble())
        }

        query.limitToLast(pageSize).get().addOnSuccessListener { snap ->
            val posts = snap.children.mapNotNull { it.getValue(Post::class.java)?.apply { postId = it.key ?: "" } }.reversed()
            onResult(true, posts, null)
        }.addOnFailureListener { e -> onResult(false, emptyList(), e.message) }
    }

    // Identifies which posts in a provided list have been liked by the current user.
    fun fetchLikedPostIds(ids: List<String>, onResult: (Set<String>) -> Unit) {
        val uid = getCurrentUid() ?: return onResult(emptySet())
        val liked = mutableSetOf<String>()
        var remaining = ids.size
        if (remaining == 0) return onResult(liked)

        ids.forEach { pid ->
            db.child("postLikes").child(pid).child(uid).get().addOnSuccessListener { snap ->
                if (snap.exists()) liked.add(pid)
                if (--remaining == 0) onResult(liked)
            }.addOnFailureListener { if (--remaining == 0) onResult(liked) }
        }
    }

    // Finds the IDs of all posts where the current user has performed a specific action.
    fun fetchMyPostIds(root: String, uid: String, onDone: (Set<String>) -> Unit) {
        db.child(root).get().addOnSuccessListener { snap ->
            onDone(snap.children.filter { it.hasChild(uid) }.mapNotNull { it.key }.toSet())
        }.addOnFailureListener { onDone(emptySet()) }
    }

    // Retrieves all posts owned by a specific user and wraps them for UI display.
    fun fetchUserPostUis(ownerId: String, owner: User, onDone: (Boolean, List<PostUi>, String?) -> Unit) {
        db.child("userPosts").child(ownerId).get().addOnSuccessListener { snap ->
            val ids = snap.children.mapNotNull { it.key }
            if (ids.isEmpty()) return@addOnSuccessListener onDone(true, emptyList(), null)

            val posts = mutableListOf<Post>()
            var remaining = ids.size
            ids.forEach { pid ->
                db.child("posts").child(pid).get().addOnSuccessListener { pSnap ->
                    pSnap.getValue(Post::class.java)?.let { posts.add(it.apply { postId = pSnap.key ?: pid }) }
                    if (--remaining == 0) onDone(true, posts.sortedByDescending { it.createdAt }.map { PostUi(it, 0.0, owner, owner.profilePhotoUrl) }, null)
                }.addOnFailureListener { if (--remaining == 0) onDone(true, posts.map { PostUi(it, 0.0, owner, owner.profilePhotoUrl) }, null) }
            }
        }.addOnFailureListener { e -> onDone(false, emptyList(), e.message) }
    }


   // prepare a list of PostUi objects with full user and status data.

    fun prepareViewerData(posts: List<Post>, onDone: (List<PostUi>) -> Unit) {
        val uid = getCurrentUid() ?: ""
        val ownerIds = posts.map { it.ownerId }.distinct()
        
        UserManager.instance.fetchMultipleUsers(ownerIds) { usersById ->
            val initialUiList = posts.mapNotNull { p ->
                val owner = usersById[p.ownerId] ?: return@mapNotNull null
                PostUi(post = p, distanceKm = 0.0, owner = owner, ownerPhotoUrl = owner.profilePhotoUrl)
            }

            val ids = initialUiList.mapNotNull { it.post.postId }
            fetchLikedPostIds(ids) { likedIds ->
                fetchMyPostIds("jamArrivals", uid) { arrivalIds ->
                    fetchMyPostIds("memberApplications", uid) { applyIds ->
                        UserManager.instance.fetchFollowStatus(ownerIds, uid) { followedOwnerIds ->
                            val finalized = initialUiList.map { ui ->
                                ui.copy(
                                    isLikedByMe = likedIds.contains(ui.post.postId),
                                    isComingByMe = arrivalIds.contains(ui.post.postId),
                                    isAppliedByMe = applyIds.contains(ui.post.postId),
                                    isFollowingOwner = followedOwnerIds.contains(ui.post.ownerId)
                                )
                            }
                            onDone(finalized)
                        }
                    }
                }
            }
        }
    }


     //Fetches post objects for a list of IDs.

    fun fetchPostsByIds(postIds: List<String>, onDone: (List<Post>) -> Unit) {
        val posts = mutableListOf<Post>()
        var remaining = postIds.size
        if (remaining == 0) return onDone(posts)
        postIds.forEach { pid ->
            db.child("posts").child(pid).get().addOnSuccessListener { pSnap ->
                pSnap.getValue(Post::class.java)?.let { posts.add(it.apply { postId = pSnap.key ?: pid }) }
                if (--remaining == 0) onDone(posts)
            }.addOnFailureListener { if (--remaining == 0) onDone(posts) }
        }
    }
}