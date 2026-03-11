package com.example.jammate.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.exoplayer.ExoPlayer
import androidx.viewpager2.widget.ViewPager2
import com.example.jammate.data.PostManager
import com.example.jammate.data.UserManager
import com.example.jammate.model.Post
import com.example.jammate.model.PostUi
import com.example.jammate.model.User
import com.example.jammate.adapters.PostViewerPagerAdapter
import com.example.jammate.databinding.ActivityPostViewerBinding
import com.example.jammate.databinding.ModalDeleteConfirmationBinding
import com.example.jammate.databinding.ModalDeletePostBinding
import com.example.jammate.ui.fragments.CommentsBottomSheetFragment
import com.example.jammate.utilities.Constants
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// This activity displays posts in a full-screen vertical pager.
class PostViewerActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var adapter: PostViewerPagerAdapter
    private lateinit var binding: ActivityPostViewerBinding

    private var mode: String = MODE_OWNER
    private var ownerId: String = ""
    private var startPostId: String = ""
    private var feedPostIds: ArrayList<String> = arrayListOf()
    private var startIndex: Int = 0

    private var player: ExoPlayer? = null
    private var currentPos = 0

    // Handles page changes to manage video playback and tracking.
    private val pageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            currentPos = position
        }

        override fun onPageScrollStateChanged(state: Int) {
            val p = player ?: return
            when (state) {
                ViewPager2.SCROLL_STATE_DRAGGING -> {
                    adapter.stopAt(currentPos, p)
                    p.playWhenReady = false
                    p.pause()
                }
                ViewPager2.SCROLL_STATE_IDLE -> {
                    adapter.playAt(pager, currentPos, p)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        player = ExoPlayer.Builder(this).build()
        pager = binding.viewerPAGER

        setupAdapter()
        
        pager.adapter = adapter
        pager.orientation = ViewPager2.ORIENTATION_VERTICAL
        pager.registerOnPageChangeCallback(pageCallback)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_OWNER
        handleIntent()
    }

    // Configures the pager adapter with all required interaction callbacks.
    private fun setupAdapter() {
        adapter = PostViewerPagerAdapter(
            onBack = { finish() },
            onLike = { post -> toggleAction(post, Constants.PostActions.LIKE) },
            onComment = { post -> showComments(post) },
            onJamJoin = { post -> toggleAction(post, Constants.PostActions.COMING) },
            onMemberApply = { post -> toggleAction(post, Constants.PostActions.APPLY) },
            onFollow = { uid -> toggleFollow(uid) },
            onProfileClick = { uid ->
                if (uid == PostManager.instance.getCurrentUid()) {
                    finish()
                } else {
                    ProfileActivity.start(this, uid)
                }
            },
            onMoreClick = { post -> showDeleteBottomSheet(post) }
        )
    }

    // Opens the comments bottom sheet for a specific post.
    private fun showComments(post: Post) {
        CommentsBottomSheetFragment.newInstance(post.postId) { newCount ->
            val current = adapter.currentList.toMutableList()
            val idx = current.indexOfFirst { it.post.postId == post.postId }
            if (idx != -1) {
                current[idx] = current[idx].copy(post = current[idx].post.copy(commentsCount = newCount))
                adapter.submitList(current)
            }
        }.show(supportFragmentManager, "comments")
    }

    // Toggles the follow status for a user and updates the UI accordingly.
    private fun toggleFollow(uid: String) {
        UserManager.instance.toggleFollow(uid) { ok, err, isFollowing ->
            if (ok) {
                toast(if (isFollowing) "Following" else "Unfollowed")
                updateFollowStateInList(uid, isFollowing)
            } else {
                toast(err ?: "Follow failed")
            }
        }
    }

    // Shows the options menu for deleting a post.
    private fun showDeleteBottomSheet(post: Post) {
        val dialog = BottomSheetDialog(this)
        val modalBinding = ModalDeletePostBinding.inflate(layoutInflater)
        dialog.setContentView(modalBinding.root)

        modalBinding.deleteModalBTNDelete.setOnClickListener {
            dialog.dismiss()
            showDeleteConfirmation(post)
        }
        modalBinding.deleteModalBTNCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // Displays a confirmation dialog before permanently removing a post.
    private fun showDeleteConfirmation(post: Post) {
        val dialog = BottomSheetDialog(this)
        val modalBinding = ModalDeleteConfirmationBinding.inflate(layoutInflater)
        dialog.setContentView(modalBinding.root)

        modalBinding.deleteConfirmBTNDelete.setOnClickListener {
            dialog.dismiss()
            executeDeletion(post)
        }
        modalBinding.deleteConfirmBTNCancel.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // Removes the post from the database and closes the viewer.
    private fun executeDeletion(post: Post) {
        PostManager.instance.deletePost(post) { ok, err ->
            if (ok) {
                toast("Post deleted")
                finish() 
            } else {
                toast(err ?: "Error deleting post")
            }
        }
    }

    // Updates the follow status locally in the adapter list.
    private fun updateFollowStateInList(ownerId: String, isFollowing: Boolean) {
        val current = adapter.currentList.map { 
            if (it.post.ownerId == ownerId) it.copy(isFollowingOwner = isFollowing) else it 
        }
        adapter.submitList(current)
    }

    // Parses intent data to determine which posts to load.
    private fun handleIntent() {
        when (mode) {
            MODE_FEED -> {
                feedPostIds = intent.getStringArrayListExtra(EXTRA_FEED_POST_IDS) ?: arrayListOf()
                startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                if (feedPostIds.isEmpty()) { toast("No posts"); finish(); return }
                loadFeed(feedPostIds, startIndex)
            }
            else -> {
                ownerId = intent.getStringExtra(EXTRA_OWNER_ID).orEmpty()
                startPostId = intent.getStringExtra(EXTRA_POST_ID).orEmpty()
                if (ownerId.isBlank()) { toast("Missing ownerId"); finish(); return }
                loadOwnerPosts(ownerId, startPostId)
            }
        }
    }

    // Loads posts for a specific owner and prepares them for the viewer.
    private fun loadOwnerPosts(ownerId: String, startId: String) {
        PostManager.instance.fetchUserPostUis(ownerId, User()) { ok, list, err ->
            if (ok) {
                PostManager.instance.prepareViewerData(list.map { it.post }) { finalized ->
                    displayFinalizedData(finalized, startId, 0)
                }
            } else {
                toast(err ?: "Load failed"); finish()
            }
        }
    }

    // Loads a list of specific post IDs for the feed mode.
    private fun loadFeed(ids: List<String>, startIdx: Int) {
        PostManager.instance.fetchPostsByIds(ids) { posts ->
            PostManager.instance.prepareViewerData(posts) { finalized ->
                displayFinalizedData(finalized, null, startIdx)
            }
        }
    }

    // Submits the prepared data to the adapter and sets the starting page.
    private fun displayFinalizedData(finalized: List<PostUi>, startId: String?, startIdx: Int) {
        adapter.submitList(finalized)
        val index = if (startId != null) finalized.indexOfFirst { it.post.postId == startId }.coerceAtLeast(0) 
                    else startIdx.coerceIn(0, maxOf(0, finalized.size - 1))
        
        pager.post { 
            pager.setCurrentItem(index, false)
            currentPos = index
            player?.let { adapter.playAt(pager, index, it) }
        }
    }

    // Handles social actions like Likes and Registrations via the manager.
    private fun toggleAction(post: Post, action: String) {
        val postId = post.postId.orEmpty()
        if (postId.isBlank()) return

        PostManager.instance.togglePostAction(postId, action) { ok, err, isNowOn ->
            if (!ok) { toast(err ?: "Action failed"); return@togglePostAction
            }

            val current = adapter.currentList.toMutableList()
            val idx = current.indexOfFirst { it.post.postId == postId }
            if (idx == -1) return@togglePostAction

            val ui = current[idx]
            val newUi = when (action) {
                Constants.PostActions.LIKE -> {
                    val delta = if (isNowOn) 1 else -1
                    ui.copy(post = ui.post.copy(likesCount = (ui.post.likesCount + delta).coerceAtLeast(0)), isLikedByMe = isNowOn)
                }
                Constants.PostActions.COMING -> ui.copy(isComingByMe = isNowOn)
                Constants.PostActions.APPLY -> ui.copy(isAppliedByMe = isNowOn)
                else -> ui
            }
            current[idx] = newUi
            adapter.submitList(current)
        }
    }

    override fun onStart() {
        super.onStart()
        player?.let { adapter.playAt(pager, currentPos, it) }
    }

    override fun onStop() {
        super.onStop()
        player?.let { adapter.stopAt(currentPos, it); it.playWhenReady = false; it.pause() }
    }

    override fun onDestroy() {
        pager.unregisterOnPageChangeCallback(pageCallback)
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_OWNER_ID = "extra_owner_id"
        private const val EXTRA_POST_ID = "extra_post_id"
        private const val EXTRA_FEED_POST_IDS = "extra_feed_post_ids"
        private const val EXTRA_START_INDEX = "extra_start_index"
        private const val MODE_OWNER = "mode_owner"
        private const val MODE_FEED = "mode_feed"

        fun start(context: Context, postId: String, ownerId: String) {
            context.startActivity(Intent(context, PostViewerActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_OWNER); putExtra(EXTRA_OWNER_ID, ownerId); putExtra(EXTRA_POST_ID, postId)
            })
        }

        fun startFeed(context: Context, postIds: ArrayList<String>, startIndex: Int) {
            context.startActivity(Intent(context, PostViewerActivity::class.java).apply {
                putExtra(EXTRA_MODE, MODE_FEED); putStringArrayListExtra(EXTRA_FEED_POST_IDS, postIds); putExtra(EXTRA_START_INDEX, startIndex)
            })
        }
    }
}
