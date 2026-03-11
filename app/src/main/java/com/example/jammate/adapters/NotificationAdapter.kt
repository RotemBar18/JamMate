package com.example.jammate.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.jammate.R
import com.example.jammate.databinding.ItemNotificationBinding
import com.example.jammate.model.Notification
import com.example.jammate.utilities.ImageLoader
import com.example.jammate.utilities.TimeFormatter

class NotificationAdapter(
    private val onNotificationClick: (Notification) -> Unit,
    private val onDeleteClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notification: Notification) {
            binding.notificationLBLMessage.text = "${notification.senderName} ${notification.message}"
            binding.notificationLBLTime.text = TimeFormatter.formatElapsedTime(notification.timestamp)
            
            ImageLoader.load(notification.senderPhotoUrl, binding.notificationIMGSender)
            
            val typeIcon = when (notification.type) {
                "like" -> R.drawable.ic_heart
                "comment" -> R.drawable.ic_comment
                "apply" -> R.drawable.ic_apply
                "coming" -> R.drawable.ic_approve
                "follow" -> R.drawable.ic_profile
                else -> R.drawable.ic_notification
            }
            binding.notificationIMGType.setImageResource(typeIcon)
            
            binding.root.alpha = if (notification.isRead) 0.6f else 1.0f
            
            binding.root.setOnClickListener { onNotificationClick(notification) }
            binding.notificationBTNDelete.setOnClickListener { onDeleteClick(notification) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem.notificationId == newItem.notificationId
        }

        override fun areContentsTheSame(oldItem: Notification, newItem: Notification): Boolean {
            return oldItem == newItem
        }
    }
}
