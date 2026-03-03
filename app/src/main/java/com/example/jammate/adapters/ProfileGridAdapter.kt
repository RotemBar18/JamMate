package com.example.jammate.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jammate.databinding.ItemProfileGridBinding
import com.example.jammate.model.PostUi
import com.example.jammate.utilities.ImageLoader

// This adapter handles the grid display of posts on the profile page.
class ProfileGridAdapter(
    private val onClick: (PostUi) -> Unit
) : ListAdapter<PostUi, ProfileGridAdapter.ProfileGridViewHolder>(PostDiffCallback()) {

    // This class holds references to the views for each individual grid item.
    class ProfileGridViewHolder(val binding: ItemProfileGridBinding) : RecyclerView.ViewHolder(binding.root)

    // This method creates a new view holder by inflating the grid item layout.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileGridViewHolder {
        val binding = ItemProfileGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProfileGridViewHolder(binding)
    }

    // This method connects the post media to the views in the holder.
    override fun onBindViewHolder(holder: ProfileGridViewHolder, position: Int) {
        val item = getItem(position)
        val post = item.post
        val binding = holder.binding

        // We load the post image using the mandatory media URL.
        ImageLoader.getInstance().loadImage(
            Uri.parse(post.mediaUrl.orEmpty()),
            binding.profileGridIMGPostImg,
        )

        // We display a play icon badge if the media type is a video.
        val isVideo = (post.mediaType ?: "").equals("video", ignoreCase = true)
        binding.profileGridIMGVideoBadge.isVisible = isVideo

        // We trigger the click callback when the user selects a grid item.
        binding.root.setOnClickListener { onClick(item) }
    }

    // This class provides logic to check for differences between list items for efficient updates.
    private class PostDiffCallback : DiffUtil.ItemCallback<PostUi>() {
        override fun areItemsTheSame(oldItem: PostUi, newItem: PostUi) =
            oldItem.post.postId == newItem.post.postId

        override fun areContentsTheSame(oldItem: PostUi, newItem: PostUi) =
            oldItem == newItem
    }
}
