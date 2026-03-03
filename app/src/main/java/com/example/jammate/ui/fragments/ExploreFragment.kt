package com.example.jammate.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.jammate.adapters.ProfileGridAdapter
import com.example.jammate.data.PostManager
import com.example.jammate.data.UserManager
import com.example.jammate.databinding.FragmentExploreBinding
import com.example.jammate.interfaces.LocationCallback
import com.example.jammate.model.LocationData
import com.example.jammate.model.Post
import com.example.jammate.model.PostUi
import com.example.jammate.model.User
import com.example.jammate.ui.activities.PostViewerActivity
import com.example.jammate.utilities.GeoHelper
import com.example.jammate.utilities.GridInnerSpacing
import com.example.jammate.utilities.LocationDetector
import com.example.jammate.utilities.UserLocationStore

class ExploreFragment : Fragment(), LocationCallback {

    private lateinit var binding: FragmentExploreBinding
    private lateinit var gridAdapter: ProfileGridAdapter

    private val postManager = PostManager.instance

    private var allUiPosts: List<PostUi> = emptyList()
    private var userLocation: LocationData? = null

    private val usersCache = mutableMapOf<String, User>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecycler()
        initSearch()

        userLocation = UserLocationStore.lastLocation

        val shouldRefresh = userLocation == null || !UserLocationStore.isFresh(30 * 60 * 1000L)
        if (shouldRefresh && hasLocationPermission()) {
            fetchUserLocation()
        } else {
            loadPosts()
        }
    }

    private fun initRecycler() {
        gridAdapter = ProfileGridAdapter(
            onClick = { ui ->
                val list = gridAdapter.currentList
                val startIndex = list.indexOfFirst { it.post.postId == ui.post.postId }.coerceAtLeast(0)
                val ids = ArrayList(list.map { it.post.postId })

                PostViewerActivity.startFeed(
                    context = requireContext(),
                    postIds = ids,
                    startIndex = startIndex
                )
            }
        )

        val spanCount = 3
        binding.exploreRVPosts.layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.exploreRVPosts.adapter = gridAdapter

        // 🔥 Add spacing
        val spacingDp = 2
        val spacingPx = (spacingDp * resources.displayMetrics.density).toInt()

        binding.exploreRVPosts.addItemDecoration(
            GridInnerSpacing(spanCount, spacingPx)
        )
    }

    private fun initSearch() {
        binding.exploreINPUTSearch.doOnTextChanged { text, _, _, _ ->
            applyFilter(text?.toString().orEmpty())
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun fetchUserLocation() {
        val detector = LocationDetector(requireActivity() as AppCompatActivity, this)
        detector.fetchLastLocation()
    }

    override fun onLocation(location: LocationData) {
        userLocation = location
        UserLocationStore.save(requireContext().applicationContext, location)
        loadPosts()
    }

    override fun onLocationError(message: String) {
        toast(message)
        loadPosts()
    }

    private fun loadPosts() {
        postManager.fetchAllPosts { ok, posts, err ->
            if (!ok) {
                toast(err ?: "Failed loading posts")
                gridAdapter.submitList(emptyList())
                return@fetchAllPosts
            }

            val myUid = postManager.getCurrentUid()

            // Hide my own posts from Explore
            val visiblePosts = if (myUid.isNullOrBlank()) posts else posts.filter { it.ownerId != myUid }

            if (visiblePosts.isEmpty()) {
                gridAdapter.submitList(emptyList())
                return@fetchAllPosts
            }

            fetchUsersForPosts(visiblePosts) { usersById ->
                buildAndShowUi(visiblePosts, usersById)
            }
        }
    }

    private fun fetchUsersForPosts(posts: List<Post>, onDone: (Map<String, User>) -> Unit) {
        val ownerIds = posts.map { it.ownerId }.distinct()

        val result = mutableMapOf<String, User>()
        ownerIds.forEach { uid ->
            usersCache[uid]?.let { result[uid] = it }
        }

        val missing = ownerIds.filter { !result.containsKey(it) }
        if (missing.isEmpty()) {
            onDone(result)
            return
        }

        val userManager = UserManager()
        var remaining = missing.size

        missing.forEach { uid ->
            userManager.fetchUser(uid) { ok, user, _ ->
                if (ok && user != null) {
                    usersCache[uid] = user
                    result[uid] = user
                }
                remaining--
                if (remaining == 0) onDone(result)
            }
        }
    }

    private fun buildAndShowUi(posts: List<Post>, usersById: Map<String, User>) {
        val loc = userLocation

        // Build PostUi list
        allUiPosts = posts.mapNotNull { p ->
            val owner = usersById[p.ownerId] ?: return@mapNotNull null

            val distanceKm =
                if (loc?.lat != null && loc.lng != null && p.location?.lat != null && p.location!!.lng != null) {
                    GeoHelper.distanceKm(loc.lat!!, loc.lng!!, p.location!!.lat!!, p.location!!.lng!!)
                } else {
                    99999.0
                }

            PostUi(
                post = p,
                distanceKm = distanceKm,
                owner = owner,
                ownerPhotoUrl = owner.profilePhotoUrl
            )
        }

            .sortedBy { it.distanceKm }

        applyFilter(binding.exploreINPUTSearch.text?.toString().orEmpty())
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val filtered =
            if (q.isEmpty()) allUiPosts
            else allUiPosts.filter { it.searchableText.contains(q) }

        gridAdapter.submitList(filtered)
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}