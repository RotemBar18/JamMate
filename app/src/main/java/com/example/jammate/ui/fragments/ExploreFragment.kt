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
import androidx.recyclerview.widget.RecyclerView
import com.example.jammate.adapters.ProfileGridAdapter
import com.example.jammate.data.PostManager
import com.example.jammate.databinding.FragmentExploreBinding
import com.example.jammate.interfaces.LocationCallback
import com.example.jammate.model.LocationData
import com.example.jammate.model.PostUi
import com.example.jammate.ui.activities.PostViewerActivity
import com.example.jammate.utilities.GeoHelper
import com.example.jammate.utilities.GridInnerSpacing
import com.example.jammate.utilities.LocationDetector
import com.example.jammate.utilities.UserLocationStore

class ExploreFragment : Fragment(), LocationCallback {

    private lateinit var binding: FragmentExploreBinding
    private lateinit var gridAdapter: ProfileGridAdapter

    private val postManager = PostManager.instance

    private var allUiPosts: MutableList<PostUi> = mutableListOf()
    private var userLocation: LocationData? = null

    private var isLoading = false
    private var canLoadMore = true
    private var lastPostTimestamp: Long? = null

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
            loadPosts(isInitialLoad = true)
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
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        binding.exploreRVPosts.layoutManager = layoutManager
        binding.exploreRVPosts.adapter = gridAdapter

        // Add scroll listener for pagination
        binding.exploreRVPosts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                // Load more items if we are at the end of the list
                if (!isLoading && canLoadMore && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                    loadPosts(isInitialLoad = false)
                }
            }
        })

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
        loadPosts(isInitialLoad = true)
    }

    override fun onLocationError(message: String) {
        toast(message)
        loadPosts(isInitialLoad = true)
    }

    private fun loadPosts(isInitialLoad: Boolean) {
        if (isLoading) return
        isLoading = true

        if (isInitialLoad) {
            allUiPosts.clear()
            lastPostTimestamp = null
            canLoadMore = true
            gridAdapter.submitList(emptyList()) // Clear adapter for a fresh load
        }

        postManager.fetchPostsPaginated(15, lastPostTimestamp) { ok, posts, err ->
            isLoading = false
            if (!ok) {
                toast(err ?: "Failed loading posts")
                return@fetchPostsPaginated
            }

            // If we received fewer posts than requested, we've reached the end
            if (posts.size < 15) {
                canLoadMore = false
            }

            if (posts.isEmpty()) return@fetchPostsPaginated

            // Update the timestamp for the next page request
            lastPostTimestamp = posts.last().createdAt

            val myUid = postManager.getCurrentUid()
            val visiblePosts = if (myUid.isNullOrBlank()) posts else posts.filter { it.ownerId != myUid }

            if (visiblePosts.isEmpty()) return@fetchPostsPaginated

            postManager.prepareViewerData(visiblePosts) { postUis ->
                buildAndShowUi(postUis)
            }
        }
    }

    private fun buildAndShowUi(newUiPosts: List<PostUi>) {
        val loc: LocationData? = userLocation

        // Calculate distance for each new post
        val postsWithDistance = newUiPosts.map { ui ->
            val p = ui.post
            val postLocation = p.location

            val distanceKm = if (loc?.lat != null && loc.lng != null && postLocation?.lat != null && postLocation.lng != null) {
                GeoHelper.distanceKm(loc.lat!!, loc.lng!!, postLocation.lat!!, postLocation.lng!!)
            } else {
                99999.0
            }
            ui.copy(distanceKm = distanceKm)
        }

        allUiPosts.addAll(postsWithDistance)
        allUiPosts = allUiPosts.distinctBy { it.post.postId }.toMutableList()

        // Sort by latest, then following, then distance
        allUiPosts.sortWith(
            compareByDescending<PostUi> { it.post.createdAt }
                .thenByDescending { it.isFollowingOwner }
                .thenBy { it.distanceKm }
        )

        applyFilter(binding.exploreINPUTSearch.text?.toString().orEmpty())
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        val filtered =
            if (q.isEmpty()) allUiPosts.toList() // Create a copy for the adapter
            else allUiPosts.filter { it.searchableText.contains(q) }.toList()

        gridAdapter.submitList(filtered)
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
}