package com.example.jammate.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.jammate.R
import com.example.jammate.databinding.ItemPostViewerPageBinding
import com.example.jammate.model.Post
import com.example.jammate.model.PostUi
import com.example.jammate.data.MediaInfo
import com.example.jammate.data.PostKind
import com.example.jammate.data.kind
import com.example.jammate.data.mediaInfo
import com.example.jammate.data.PostManager
import com.example.jammate.utilities.Constants
import com.example.jammate.utilities.ImageLoader
import com.google.android.material.button.MaterialButton

// This adapter manages the vertical scrolling list of posts in the full-screen viewer.
class PostViewerPagerAdapter(
    private val onBack: () -> Unit,
    private val onLike: (Post) -> Unit = {},
    private val onComment: (Post) -> Unit = {},
    private val onJamJoin: (Post) -> Unit = {},
    private val onMemberApply: (Post) -> Unit = {},
    private val onFollow: (ownerId: String) -> Unit = {},
    private val onProfileClick: (ownerId: String) -> Unit = {},
    private val onMoreClick: (Post) -> Unit = {}
) : ListAdapter<PostUi, PostViewerPagerAdapter.PostViewHolder>(PostDiffCallback()) {

    // Holds references to the UI elements for a single post page.
    class PostViewHolder(val binding: ItemPostViewerPageBinding) : RecyclerView.ViewHolder(binding.root) {
        var isExpanded = false
    }

    private var currentPager: ViewPager2? = null
    private var currentPosition: Int = -1
    private var playerListenerAttached = false

    // Listens for video playback status changes to update the loading animation.
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val binding = visibleBindingOrNull() ?: return
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    binding.postViewerLOTLoading.isVisible = true
                    binding.postViewerLOTLoading.playAnimation()
                }
                Player.STATE_READY, Player.STATE_ENDED -> hideLoader(binding)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) hideLoader(visibleBindingOrNull() ?: return)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemPostViewerPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        val item = getItem(position)
        val binding = holder.binding
        val hasMedia = item.post.mediaInfo().hasMedia

        if (payloads.contains(Constants.Payloads.LIKE_UPDATE)) {
            binding.postViewerBTNLikeCount.text = item.post.likesCount.toString()
            updateLikeStatus(binding, item, hasMedia)
        }

        if (payloads.contains(Constants.Payloads.ACTION_UPDATE)) {
            updateActionStatus(binding, item, hasMedia)
        }

        if (payloads.contains(Constants.Payloads.FOLLOW_UPDATE)) {
            updateFollowStatus(binding, item)
        }

        if (payloads.contains(Constants.Payloads.COMMENT_UPDATE)) {
            binding.postViewerBTNCommentCount.text = item.post.commentsCount.toString()
        }
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = getItem(position)
        val binding = holder.binding
        val media = item.post.mediaInfo()

        resetState(binding)
        bindHeader(binding, item)
        bindPostContent(holder, item, media)
        bindInteractions(binding, item, media.hasMedia)
    }

    // Clears media state to ensure clean view recycling.
    private fun resetState(binding: ItemPostViewerPageBinding) {
        hideLoader(binding)
        binding.postViewerPLYVideo.apply {
            player = null
            isGone = true
        }
        binding.postViewerIMGMedia.isVisible = true
    }

    // Configures user info, profile clicks, and follow button logic.
    private fun bindHeader(binding: ItemPostViewerPageBinding, item: PostUi) {
        binding.postViewerBTNBack.setOnClickListener { onBack() }
        binding.postViewerLBLUserName.text = item.owner.stageName.ifBlank { "${item.owner.firstName} ${item.owner.lastName}" }
        binding.postViewerLBLLocation.text = item.post.location?.name ?: item.owner.location.orEmpty()
        
        val photoUrl = item.ownerPhotoUrl.ifBlank { item.owner.profilePhotoUrl }.orEmpty()
        if (photoUrl.isNotBlank()) {
            ImageLoader.getInstance().loadImage(Uri.parse(photoUrl), binding.postViewerIMGAvatar, R.drawable.ic_profile)
        } else {
            binding.postViewerIMGAvatar.setImageResource(R.drawable.ic_profile)
        }

        updateFollowStatus(binding, item)
        binding.postViewerBTNFollow.setOnClickListener { onFollow(item.post.ownerId) }

        binding.postViewerLBLUserName.setOnClickListener { onProfileClick(item.post.ownerId) }
        binding.postViewerIMGAvatar.setOnClickListener { onProfileClick(item.post.ownerId) }
        
        val isOwnPost = item.post.ownerId == PostManager.instance.getCurrentUid()
        binding.postViewerBTNMore.isVisible = isOwnPost
        binding.postViewerBTNMore.setOnClickListener { onMoreClick(item.post) }
    }

    // Toggles the visibility and text of the follow button based on the owner.
    private fun updateFollowStatus(binding: ItemPostViewerPageBinding, item: PostUi) {
        val currentUid = PostManager.instance.getCurrentUid()
        if (item.post.ownerId == currentUid) {
            binding.postViewerBTNFollow.isGone = true
        } else {
            binding.postViewerBTNFollow.isVisible = true
            binding.postViewerBTNFollow.text = if (item.isFollowingOwner) "Following" else "Follow"
        }
    }

    // Renders images, videos, and dynamic tags for the post content.
    private fun bindPostContent(holder: PostViewHolder, item: PostUi, media: MediaInfo) {
        val post = item.post
        val binding = holder.binding
        val kind = post.kind()


        if (!media.isVideo) ImageLoader.getInstance().loadImage(Uri.parse(media.url), binding.postViewerIMGMedia)

        // Dynamically populates the tag container for member posts.
        binding.postViewerLAYTagsContainer.removeAllViews()
        if (kind == PostKind.MEMBER) {
            binding.postViewerSCROLLTags.isVisible = true
            val tags = post.instrument.plus(post.genre)
            val context = binding.root.context
            
            tags.forEach { tag ->
                val tagButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = View.generateViewId()
                    text = tag
                    isClickable = false
                    isFocusable = false
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    strokeColor = ColorStateList.valueOf(Color.WHITE)
                    strokeWidth = (resources.displayMetrics.density * 1).toInt()
                    cornerRadius = (resources.displayMetrics.density * 8).toInt()
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    minHeight = 0
                    minimumHeight = 0
                    setPadding((resources.displayMetrics.density * 8).toInt(), 0, (resources.displayMetrics.density * 8).toInt(), 0)
                    
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (resources.displayMetrics.density * 26).toInt()
                    ).apply { marginEnd = (resources.displayMetrics.density * 6).toInt() }
                }
                binding.postViewerLAYTagsContainer.addView(tagButton)
            }
        } else {
            binding.postViewerSCROLLTags.isGone = true
        }

        // Sets the caption and handles the expand toggle.
        binding.postViewerLBLDescription.text = post.description
        binding.postViewerLBLDescription.maxLines = if (holder.isExpanded) 100 else 2
        binding.postViewerLBLDescription.setOnClickListener {
            holder.isExpanded = !holder.isExpanded
            binding.postViewerLBLDescription.maxLines = if (holder.isExpanded) 100 else 2
        }
    }

    // Connects Like, Comment, and primary actions to their respective callbacks.
    private fun bindInteractions(binding: ItemPostViewerPageBinding, item: PostUi, hasMedia: Boolean) {
        val post = item.post
        binding.postViewerBTNLikeCount.text = post.likesCount.toString()
        binding.postViewerBTNCommentCount.text = post.commentsCount.toString()

        binding.postViewerBTNLike.setOnClickListener { onLike(post) }
        binding.postViewerBTNComment.setOnClickListener { onComment(post) }
        binding.postViewerBTNPrimaryAction.setOnClickListener {
            when (post.kind()) {
                PostKind.JAM -> onJamJoin(post)
                else -> onMemberApply(post)
            }
        }

        updateLikeStatus(binding, item, hasMedia)
        updateActionStatus(binding, item, hasMedia)
    }


    // Updates the heart icon color to reflect whether the user liked the post.
    private fun updateLikeStatus(binding: ItemPostViewerPageBinding, item: PostUi, hasMedia: Boolean) {
        val context = binding.root.context
        val color = if (item.isLikedByMe) ContextCompat.getColor(context, android.R.color.holo_red_light) 
                    else ContextCompat.getColor(context, if (hasMedia) R.color.white else R.color.dark)
        binding.postViewerBTNLike.setColorFilter(color)
    }

    // Manages text and color for primary actions like Register or Apply.
    private fun updateActionStatus(binding: ItemPostViewerPageBinding, item: PostUi, hasMedia: Boolean) {
        val kind = item.post.kind()
        if (kind == PostKind.NORMAL) {
            binding.postViewerBTNPrimaryAction.isGone = true
            binding.postViewerLBLPrimaryAction.isGone = true
            return
        }
        binding.postViewerBTNPrimaryAction.isVisible = true
        binding.postViewerLBLPrimaryAction.isVisible = true
        val isActive = if (kind == PostKind.JAM) item.isComingByMe else item.isAppliedByMe
        binding.postViewerLBLPrimaryAction.text = when (kind) {
            PostKind.JAM -> if (item.isComingByMe) "Registered" else "Register"
            else -> if (item.isAppliedByMe) "Applied" else "Apply"
        }
        val context = binding.root.context
        val color = if (isActive) ContextCompat.getColor(context, R.color.blue)
                    else ContextCompat.getColor(context, if (hasMedia) R.color.white else R.color.dark)
        binding.postViewerBTNPrimaryAction.setColorFilter(color)
        binding.postViewerLBLPrimaryAction.setTextColor(color)
    }

    // Stops the loading animation.
    private fun hideLoader(binding: ItemPostViewerPageBinding) {
        binding.postViewerLOTLoading.apply { cancelAnimation(); isGone = true }
    }

    // Prepares and plays video content for the currently selected page.
    fun playAt(pager: ViewPager2, position: Int, player: ExoPlayer) {
        if (position !in 0 until itemCount) return
        currentPager = pager
        currentPosition = position
        if (!playerListenerAttached) { player.addListener(playerListener); playerListenerAttached = true }
        val binding = getBindingAt(position) ?: return
        val media = getItem(position).post.mediaInfo()
        if (!media.isVideo) { stopAt( position, player); return }
        binding.postViewerLOTLoading.isVisible = true
        binding.postViewerLOTLoading.playAnimation()
        binding.postViewerIMGMedia.isGone = true
        binding.postViewerPLYVideo.apply { isVisible = true; this.player = player }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(media.url)))
        player.prepare()
        player.playWhenReady = true
    }

    // Stops video playback and detaches the player.
    fun stopAt(position: Int, player: ExoPlayer) {
        val binding = getBindingAt(position) ?: return
        hideLoader(binding)
        if (binding.postViewerPLYVideo.player === player) binding.postViewerPLYVideo.player = null
        binding.postViewerPLYVideo.isGone = true
        binding.postViewerIMGMedia.isVisible = true
    }

    // Retrieves the view binding for a specific adapter position.
    private fun getBindingAt(position: Int): ItemPostViewerPageBinding? {
        val rv = currentPager?.getChildAt(0) as? RecyclerView ?: return null
        return (rv.findViewHolderForAdapterPosition(position) as? PostViewHolder)?.binding
    }

    // Returns the binding for the page currently visible to the user.
    private fun visibleBindingOrNull() = getBindingAt(currentPosition)

    // Efficiently updates the list by identifying specific item differences.
    private class PostDiffCallback : DiffUtil.ItemCallback<PostUi>() {
        override fun areItemsTheSame(old: PostUi, new: PostUi) = old.post.postId == new.post.postId
        override fun areContentsTheSame(old: PostUi, new: PostUi) = old == new
        override fun getChangePayload(old: PostUi, new: PostUi): Any? {
            val likeChanged = old.post.likesCount != new.post.likesCount || old.isLikedByMe != new.isLikedByMe
            val actionChanged = old.isAppliedByMe != new.isAppliedByMe || old.isComingByMe != new.isComingByMe
            val followChanged = old.isFollowingOwner != new.isFollowingOwner
            val commentChanged = old.post.commentsCount != new.post.commentsCount
            return when {
                likeChanged -> Constants.Payloads.LIKE_UPDATE
                actionChanged -> Constants.Payloads.ACTION_UPDATE
                followChanged -> Constants.Payloads.FOLLOW_UPDATE
                commentChanged -> Constants.Payloads.COMMENT_UPDATE
                else -> null
            }
        }
    }
}
