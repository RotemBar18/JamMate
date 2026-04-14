package com.example.jammate.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.jammate.ui.activities.CreateProfileActivity
import com.example.jammate.ui.activities.LoginActivity
import com.example.jammate.R
import com.example.jammate.data.MediaManager
import com.example.jammate.databinding.FragmentProfileBinding
import com.example.jammate.model.User
import com.example.jammate.utilities.Constants
import com.example.jammate.utilities.ImageLoader
import com.example.jammate.utilities.MediaPicker
import com.example.jammate.utilities.ThemeManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import androidx.core.net.toUri
import androidx.recyclerview.widget.GridLayoutManager
import com.example.jammate.adapters.ProfileGridAdapter
import com.example.jammate.data.PostManager
import com.example.jammate.data.UserManager
import com.example.jammate.databinding.ModalChangePhotoBinding
import com.example.jammate.databinding.ModalProfileOptionsBinding
import com.example.jammate.ui.activities.PostViewerActivity
import com.example.jammate.utilities.GridInnerSpacing

class ProfileFragment : Fragment() {

    private lateinit var binding: FragmentProfileBinding

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    private lateinit var gridAdapter: ProfileGridAdapter

    private val likedPostIds = mutableSetOf<String>()
    private val comingPostIds = mutableSetOf<String>()

    private lateinit var mediaPicker: MediaPicker
    private var currentUserModel: User? = null
    private var userId: String? = null

    companion object {
        private const val ARG_USER_ID = "arg_user_id"

        fun newInstance(userId: String? = null): ProfileFragment {
            return ProfileFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_USER_ID, userId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getString(ARG_USER_ID)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    override fun onResume() {
        super.onResume()
        loadProfileAndPosts()
    }

    private fun initViews() {
        initTabs()
        initRecycler()
        
        val isOwnProfile = userId == null || userId == auth.currentUser?.uid

        mediaPicker = MediaPicker(this) { uri, type ->
            if (type == "image") uploadNewProfilePhoto(uri)
        }

        if (isOwnProfile) {
            binding.profileBTNBack.visibility = View.GONE
            binding.profileIMGAvatar.setOnClickListener { openChangePhotoSheet() }
            binding.profileBADGEEditPhoto.setOnClickListener { openChangePhotoSheet() }
            binding.profileBTNMenu.setIconResource(R.drawable.ic_more)
            binding.profileBTNMenu.setOnClickListener { openProfileMenuSheet() }
        } else {
            binding.profileBTNBack.visibility = View.VISIBLE
            binding.profileBTNBack.setOnClickListener { requireActivity().onBackPressed() }
            binding.profileBADGEEditPhoto.visibility = View.GONE
            binding.profileBTNMenu.visibility = View.VISIBLE
            binding.profileBTNMenu.setIconResource(R.drawable.ic_mail)
            binding.profileBTNMenu.setOnClickListener { sendEmail() }
            binding.profileBTNFollow.visibility = View.VISIBLE
            binding.profileBTNFollow.setOnClickListener { toggleFollow() }
        }
    }

    private fun initTabs() {
        binding.profileBTNTabPosts.isChecked = true
        showPosts(true)

        binding.profileBTNTabPosts.setOnClickListener { showPosts(true) }
        binding.profileBTNTabAbout.setOnClickListener { showPosts(false) }
    }

    private fun showPosts(show: Boolean) {
        binding.profileLAYPostsSection.visibility = if (show) View.VISIBLE else View.GONE
        binding.profileLAYAboutSection.visibility = if (show) View.GONE else View.VISIBLE
    }


    private fun initRecycler() {
        gridAdapter = ProfileGridAdapter(
            onClick = { ui ->
                PostViewerActivity.start(
                    context = requireContext(),
                    postId = ui.post.postId,
                    ownerId = ui.post.ownerId
                )
            }
        )

        val spanCount = 3
        binding.profileRVPosts.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.profileRVPosts.adapter = gridAdapter

        val spacingDp = 2
        val spacingPx = (spacingDp * resources.displayMetrics.density).toInt()

        if (binding.profileRVPosts.itemDecorationCount == 0) {
            binding.profileRVPosts.addItemDecoration(
                GridInnerSpacing(spanCount, spacingPx)
            )
        }
    }

    private fun loadProfileAndPosts() {
        val targetUid = userId ?: auth.currentUser?.uid
        if (targetUid == null) {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
            return
        }

        db.child("users").child(targetUid).get()
            .addOnSuccessListener { snap ->
                val firstName = snap.child("firstName").getValue(String::class.java).orEmpty()
                val lastName = snap.child("lastName").getValue(String::class.java).orEmpty()
                val stageName = snap.child("stageName").getValue(String::class.java).orEmpty()
                val location = snap.child("location").getValue(String::class.java).orEmpty()
                val skillLevel = snap.child("skillLevel").getValue(String::class.java).orEmpty()
                val about = snap.child("about").getValue(String::class.java).orEmpty()
                val email = snap.child("email").getValue(String::class.java).orEmpty()

                val instruments = readStringList(snap.child("instruments"))
                val genres = readStringList(snap.child("genres"))

                val dbPhotoUrl = snap.child("profilePhotoUrl").getValue(String::class.java).orEmpty()
                if (dbPhotoUrl.isNotBlank()) {
                    ImageLoader.getInstance().loadImage(dbPhotoUrl.toUri(), binding.profileIMGAvatar, R.drawable.ic_profile)
                } else {
                    binding.profileIMGAvatar.setImageResource(R.drawable.ic_profile)
                }

                val displayName = when {
                    stageName.isNotBlank() -> stageName
                    firstName.isNotBlank() || lastName.isNotBlank() -> "${firstName.trim()} ${lastName.trim()}".trim()
                    else -> "JamMate User"
                }

                binding.profileLBLName.text = displayName
                binding.profileLBLLocation.text = location.ifBlank { "—" }
                binding.profileLBLLevel.text = skillLevel.ifBlank { "—" }
                binding.profileLBLAbout.text = about

                binding.profileCHIPGROUPUserChips.removeAllViews()
                instruments.forEach { addInfoChip(it) }
                genres.forEach { addInfoChip(it) }

                if (targetUid != auth.currentUser?.uid) {
                    checkFollowStatus(targetUid)
                }

                currentUserModel = User(
                    profilePhotoUrl = dbPhotoUrl,
                    firstName = firstName,
                    lastName = lastName,
                    stageName = stageName,
                    location = location,
                    about = about,
                    skillLevel = skillLevel,
                    instruments = instruments,
                    genres = genres,
                    email = email
                )

                loadUserActions(targetUid) {
                    val owner = currentUserModel!!
                    PostManager.instance.fetchUserPostUis(targetUid, owner) { ok, list, err ->
                        if (!ok) {
                            toast(err ?: "Failed loading posts")
                            gridAdapter.submitList(emptyList())
                            updateStatsUI(targetUid)
                            return@fetchUserPostUis
                        }
                        gridAdapter.submitList(list)
                        updateStatsUI(targetUid)
                    }
                }
            }
            .addOnFailureListener { e ->
                toast("Failed loading profile: ${e.message}")
            }
    }

    private fun uploadNewProfilePhoto(uri: Uri) {
        toast("Uploading...")
        MediaManager.instance.uploadProfilePhoto(uri) { ok, url, err ->
            if (!ok || url.isNullOrBlank()) {
                toast(err ?: "Upload failed")
                return@uploadProfilePhoto
            }
            MediaManager.instance.saveProfilePhotoUrlToUser(url) { ok2, err2 ->
                if (!ok2) {
                    toast(err2 ?: "Failed saving url")
                    return@saveProfilePhotoUrlToUser
                }
                ImageLoader.getInstance().loadImage(url.toUri(), binding.profileIMGAvatar, R.drawable.ic_profile)
                toast("Photo updated")
            }
        }
    }

    private fun removeProfilePhoto() {
        MediaManager.instance.removeProfilePhoto { ok, err ->
            if (!ok) {
                toast(err ?: "Failed")
                return@removeProfilePhoto
            }
            binding.profileIMGAvatar.setImageResource(R.drawable.ic_profile)
            toast("Photo removed")
        }
    }

    private fun openChangePhotoSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val binding2 = ModalChangePhotoBinding.inflate(layoutInflater)
        dialog.setContentView(binding2.root)

        binding2.changePhotoBTNChoose.setOnClickListener {
            dialog.dismiss()
            mediaPicker.openImage()
        }

        binding2.changePhotoBTNRemove.setOnClickListener {
            dialog.dismiss()
            removeProfilePhoto()
        }

        binding2.changePhotoBTNCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun loadUserActions(uid: String, onDone: () -> Unit) {
        likedPostIds.clear()
        comingPostIds.clear()

        PostManager.instance.fetchMyPostIds("postLikes", uid) { likedIds ->
            likedPostIds.addAll(likedIds)

            PostManager.instance.fetchMyPostIds("jamArrivals", uid) { comingIds ->
                comingPostIds.addAll(comingIds)
                onDone()
            }
        }
    }


    private fun updateStatsUI(uid: String) {
        val list = gridAdapter.currentList
        binding.profileLBLPostsCount.text = list.size.toString()
        binding.profileLBLLikesCount.text = list.sumOf { it.post.likesCount }.toString()
        
        UserManager.instance.fetchFollowersCount(uid) { count ->
            binding.profileLBLFollowersCount.text = count.toString()
        }
    }

    private fun checkFollowStatus(targetUid: String) {
        val currentUid = auth.currentUser?.uid ?: return
        UserManager.instance.fetchFollowStatus(listOf(targetUid), currentUid) { followedSet ->
            updateFollowButton(followedSet.contains(targetUid))
        }
    }

    private fun toggleFollow() {
        val targetUid = userId ?: return
        binding.profileBTNFollow.isEnabled = false
        UserManager.instance.toggleFollow(targetUid) { ok, err, isFollowing ->
            binding.profileBTNFollow.isEnabled = true
            if (ok) {
                updateFollowButton(isFollowing)
                updateStatsUI(targetUid)
            } else {
                toast(err ?: "Action failed")
            }
        }
    }

    private fun updateFollowButton(isFollowing: Boolean) {
        binding.profileBTNFollow.text = if (isFollowing) "Following" else "Follow"
        if (isFollowing) {
            binding.profileBTNFollow.setBackgroundColor(resources.getColor(R.color.app_surface, null))
            binding.profileBTNFollow.setTextColor(resources.getColor(R.color.app_text_main, null))
            binding.profileBTNFollow.strokeWidth = (1 * resources.displayMetrics.density).toInt()
            binding.profileBTNFollow.setStrokeColorResource(R.color.app_outline)
        } else {
            binding.profileBTNFollow.icon = null
            binding.profileBTNFollow.setBackgroundColor(resources.getColor(R.color.app_accent, null))
            binding.profileBTNFollow.setTextColor(resources.getColor(R.color.white, null))
            binding.profileBTNFollow.strokeWidth = 0
        }
    }

    private fun addInfoChip(text: String) {
        val chip = Chip(requireContext()).apply {
            this.text = text
            isCheckable = false
            isClickable = false
            setChipBackgroundColorResource(R.color.offWhite)
            setChipStrokeColorResource(R.color.chip_border_light)
            setTextColor(resources.getColor(R.color.dark, null))
            chipStrokeWidth = 1f
        }
        binding.profileCHIPGROUPUserChips.addView(chip)
    }

    private fun readStringList(node: DataSnapshot): List<String> {
        val out = mutableListOf<String>()
        node.children.forEach { child ->
            child.getValue(String::class.java)?.let { s ->
                if (s.isNotBlank()) out.add(s)
            }
        }
        return out
    }

    private fun openProfileMenuSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val menuBinding = ModalProfileOptionsBinding.inflate(layoutInflater)
        dialog.setContentView(menuBinding.root)

        menuBinding.profileOptionsBTNEdit.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), CreateProfileActivity::class.java)
            intent.putExtra(Constants.Extras.IS_EDIT_MODE, true)
            startActivity(intent)
        }

        menuBinding.profileOptionsBTNLogout.setOnClickListener {
            dialog.dismiss()
            com.firebase.ui.auth.AuthUI.getInstance()
                .signOut(requireContext())
                .addOnCompleteListener {
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finish()
                }
        }

        // --- Persistent Theme Toggle ---
        val savedMode = ThemeManager.getTheme(requireContext())
        menuBinding.profileOptionsBTNToggle.check(
            if (savedMode == AppCompatDelegate.MODE_NIGHT_YES) R.id.profileOptions_BTN_darkTheme
            else R.id.profileOptions_BTN_lightTheme
        )

        menuBinding.profileOptionsBTNToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val newMode = if (checkedId == R.id.profileOptions_BTN_darkTheme) AppCompatDelegate.MODE_NIGHT_YES
                          else AppCompatDelegate.MODE_NIGHT_NO
            
            ThemeManager.saveTheme(requireContext(), newMode)
        }

        dialog.show()
    }

    private fun sendEmail() {
        val email = currentUserModel?.email
        if (email.isNullOrBlank()) {
            toast("No email available for this user")
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:$email".toUri()
            putExtra(Intent.EXTRA_SUBJECT, "Inquiry from JamMate")
        }
        startActivity(intent)
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}