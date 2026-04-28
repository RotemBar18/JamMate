package com.example.jammate.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jammate.databinding.ItemProfileGridBinding
import com.example.jammate.model.PostUi
import com.example.jammate.utilities.ImageLoader
import androidx.core.net.toUri

// This adapter handles the grid display of posts on the profile page.
class ProfileGridAdapter(
    private val onClick: (PostUi) -> Unit
) : ListAdapter<PostUi, ProfileGridAdapter.ProfileGridViewHolder>(PostDiffCallback()) {

    class ProfileGridViewHolder(val binding: ItemProfileGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileGridViewHolder {
        val binding = ItemProfileGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProfileGridViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileGridViewHolder, position: Int) {
        val item = getItem(position)
        val post = item.post
        val binding = holder.binding

        ImageLoader.getInstance().loadImage(
            post.mediaUrl.orEmpty().toUri(),
            binding.profileGridIMGPostImg,
        )

        val isVideo = (post.mediaType ?: "").equals("video", ignoreCase = true)
        binding.profileGridIMGVideoBadge.isVisible = isVideo

        binding.root.setOnClickListener { onClick(item) }
    }

    private class PostDiffCallback : DiffUtil.ItemCallback<PostUi>() {
        override fun areItemsTheSame(oldItem: PostUi, newItem: PostUi) =
            oldItem.post.postId == newItem.post.postId

        override fun areContentsTheSame(oldItem: PostUi, newItem: PostUi) =
            oldItem == newItem
    }
}
