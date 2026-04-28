package com.example.jammate.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jammate.R
import com.example.jammate.databinding.ItemCommentBinding
import com.example.jammate.model.Comment
import com.example.jammate.utilities.ImageLoader
import androidx.core.net.toUri

// Manages the list of user comments and how they appear on screen.
class
CommentAdapter : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    // Holds references to the views for each individual comment item.
    class CommentViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding

        binding.commentLBLUserName.text = item.ownerName
        binding.commentLBLText.text = item.text
        
        // Calculates and shows the relative time since the comment was posted.
        binding.commentLBLTime.text = DateUtils.getRelativeTimeSpanString(
            item.createdAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )

        // Loads the profile picture or sets a default icon if the URL is empty.
        val photoUrl = item.ownerPhotoUrl
        if (photoUrl.isNotBlank()) {
            ImageLoader.getInstance().loadImage(
                photoUrl.toUri(),
                binding.commentIMGAvatar,
                R.drawable.ic_profile
            )
        } else {
            binding.commentIMGAvatar.setImageResource(R.drawable.ic_profile)
        }
    }

    // Compares items to determine if the list needs to be updated efficiently.
    class CommentDiffCallback : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(old: Comment, new: Comment) = old.commentId == new.commentId
        override fun areContentsTheSame(old: Comment, new: Comment) = old == new
    }
}
