package com.example.jammate.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.jammate.adapters.NotificationAdapter
import com.example.jammate.data.NotificationManager
import com.example.jammate.databinding.FragmentNotificationBinding
import com.example.jammate.ui.activities.ProfileActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener

class NotificationFragment : Fragment() {

    private lateinit var binding: FragmentNotificationBinding

    private lateinit var adapter: NotificationAdapter
    private var notificationListener: ValueEventListener? = null
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNotificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeNotifications()
        markAllAsRead()
    }

    private fun markAllAsRead() {
        val uid = currentUid ?: return
        NotificationManager.instance.markAllAsRead(uid)
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(
            onNotificationClick = { notification ->
                ProfileActivity.start(requireContext(), notification.senderId)
            },
            onDeleteClick = { notification ->
                NotificationManager.instance.deleteNotification(currentUid ?: "", notification.notificationId)
            }
        )
        binding.notificationLSTItems.layoutManager = LinearLayoutManager(requireContext())
        binding.notificationLSTItems.adapter = adapter
    }

    private fun observeNotifications() {
        val uid = currentUid ?: return
        notificationListener = NotificationManager.instance.observeAndGetNotifications(uid) { list ->
            adapter.submitList(list)
            binding.notificationLAYEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

            if (list.any { !it.readStatus }) {
                markAllAsRead()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val uid = currentUid ?: return
        notificationListener?.let {
            NotificationManager.instance.removeNotificationListener(uid, it)
        }
    }
}
