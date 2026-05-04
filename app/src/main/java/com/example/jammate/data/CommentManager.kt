package com.example.jammate.data

import com.example.jammate.model.Comment
import com.example.jammate.model.Notification
import com.example.jammate.model.User
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class CommentManager private constructor() {

    private val db = FirebaseDatabase.getInstance().reference

    companion object {
        val instance: CommentManager by lazy { CommentManager() }
    }

    fun addComment(postId: String, text: String, user: User, onResult: (Boolean, String?) -> Unit) {
        val userId = PostManager.instance.getCurrentUid() ?: return onResult(false, "You must be logged in to comment")
        val commentId = db.child("postComments").child(postId).push().key ?: return onResult(false, "Failed to create comment ID")

        val comment = Comment(
            commentId = commentId,
            postId = postId,
            ownerId = userId,
            ownerName = user.stageName.ifBlank { "${user.firstName} ${user.lastName}" },
            ownerPhotoUrl = user.profilePhotoUrl,
            text = text,
            createdAt = System.currentTimeMillis()
        )

        db.child("postComments").child(postId).child(commentId).setValue(comment).addOnSuccessListener {
            updateCounter(db.child("posts").child(postId).child("commentsCount"), 1) { ok, err ->
                if (ok) sendCommentNotification(postId, userId, user, text)
                onResult(ok, err)
            }
        }.addOnFailureListener { e -> onResult(false, e.message) }
    }

    private fun sendCommentNotification(postId: String, senderId: String, user: User, text: String) {
        db.child("posts").child(postId).child("ownerId").get().addOnSuccessListener { snapshot ->
            val ownerId = snapshot.getValue(String::class.java) ?: return@addOnSuccessListener
            
            NotificationManager.instance.sendNotification(
                Notification(
                    type = "comment",
                    senderId = senderId,
                    senderName = user.stageName.ifBlank { "${user.firstName} ${user.lastName}" },
                    senderPhotoUrl = user.profilePhotoUrl,
                    receiverId = ownerId,
                    postId = postId,
                    message = "commented: $text"
                )
            )
        }
    }

    fun observeComments(postId: String, onUpdate: (List<Comment>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val comments = snapshot.children.mapNotNull { it.getValue(Comment::class.java) }.reversed()
                onUpdate(comments)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        db.child("postComments").child(postId).orderByChild("createdAt").addValueEventListener(listener)
        return listener
    }

    fun stopObservingComments(postId: String, listener: ValueEventListener) {
        db.child("postComments").child(postId).removeEventListener(listener)
    }

    private fun updateCounter(ref: DatabaseReference, delta: Int, onResult: (Boolean, String?) -> Unit) {
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val current = (data.value as? Long)?.toInt() ?: 0
                data.value = maxOf(0, current + delta)
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                onResult(error == null, error?.message)
            }
        })
    }
}
