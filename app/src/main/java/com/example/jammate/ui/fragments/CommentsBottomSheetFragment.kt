package com.example.jammate.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jammate.adapters.CommentAdapter
import com.example.jammate.data.CommentManager
import com.example.jammate.data.PostManager
import com.example.jammate.data.UserManager
import com.example.jammate.databinding.ModalCommentsBinding
import com.example.jammate.model.User
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.database.ValueEventListener

class CommentsBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var binding: ModalCommentsBinding
    private lateinit var postId: String
    private val adapter = CommentAdapter()
    private var currentUser: User? = null
    private var commentListener: ValueEventListener? = null
    
    private var onCommentAdded: ((Int) -> Unit)? = null

    companion object {
        private const val ARG_POST_ID = "post_id"

        fun newInstance(postId: String, onAdded: (Int) -> Unit): CommentsBottomSheetFragment {
            return CommentsBottomSheetFragment().apply {
                this.onCommentAdded = onAdded
                arguments = Bundle().apply {
                    putString(ARG_POST_ID, postId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postId = arguments?.getString(ARG_POST_ID).orEmpty()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = ModalCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupList()
        fetchCurrentUser()
        startObservingComments()

        binding.commentsBTNSend.setOnClickListener {
            val text = binding.commentsINPUTNewComment.text.toString().trim()
            if (text.isNotBlank()) {
                sendComment(text)
            }
        }
    }

    private fun setupList() {
        binding.commentsLSTList.layoutManager = LinearLayoutManager(context)
        binding.commentsLSTList.adapter = adapter
    }

    private fun startObservingComments() {
        if (postId.isBlank()) return
        
        commentListener = CommentManager.instance.observeComments(postId) { comments ->
            adapter.submitList(comments)
            binding.commentsLBLTitle.text = if (comments.isEmpty()) "No Comments" else "${comments.size} Comments"
            
            onCommentAdded?.invoke(comments.size)
        }
    }

    private fun fetchCurrentUser() {
        val uid = PostManager.instance.getCurrentUid() ?: return
        UserManager.instance.fetchUser(uid) { ok, user, _ ->
            if (ok) currentUser = user
        }
    }

    private fun sendComment(text: String) {
        val user = currentUser ?: return
        CommentManager.instance.addComment(postId, text, user) { ok, err ->
            if (ok) {
                binding.commentsINPUTNewComment.setText("")
            } else {
                Toast.makeText(context, err ?: "Failed to post comment", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        commentListener?.let { 
            CommentManager.instance.stopObservingComments(postId, it)
        }
    }


}