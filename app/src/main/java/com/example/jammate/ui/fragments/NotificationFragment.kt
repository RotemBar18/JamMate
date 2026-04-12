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

    private var _binding: FragmentNotificationBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: NotificationAdapter
    private var notificationListener: ValueEventListener? = null
    private val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationBinding.inflate(inflater, container, false)
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
                if (!notification.isRead) {
                    NotificationManager.instance.markAsRead(currentUid ?: "", notification.notificationId)
                }
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
        notificationListener = NotificationManager.instance.observeNotifications(uid) { list ->
            // Safety check: Fragment might be detached or view destroyed
            if (_binding == null) return@observeNotifications
            
            adapter.submitList(list)
            binding.notificationLAYEmptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

            // Automatically mark any new unread notifications as read while the user is viewing this screen
            if (list.any { !it.isRead }) {
                markAllAsRead()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val uid = currentUid
        val listener = notificationListener
        if (uid != null && listener != null) {
            NotificationManager.instance.stopObserving(uid, listener)
        }
        _binding = null
    }
}
