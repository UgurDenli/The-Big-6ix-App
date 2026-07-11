package com.invenium.thebig6ix.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.invenium.thebig6ix.MainActivity
import com.invenium.thebig6ix.R

class Big6ixMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .update("fcmToken", token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body  = message.notification?.body  ?: message.data["body"]  ?: return
        val type  = message.data["type"] ?: ""   // optional: "deadline" | "result" | "winner"
        showNotification(title, body, type)
    }

    private fun showNotification(title: String, body: String, type: String = "") {
        // Route to the appropriate channel based on notification type
        val channelId = when {
            type == "result" || type == "winner"     -> CHANNEL_RESULTS
            type == "deadline"                       -> CHANNEL_DEADLINES
            title.contains("result", ignoreCase = true) ||
            title.contains("winner", ignoreCase = true) ||
            title.contains("scored", ignoreCase = true) -> CHANNEL_RESULTS
            else                                     -> CHANNEL_DEADLINES
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannels(manager)

        // Stable ID derived from the notification CONTENT, not the clock. The title
        // is the unique scoreline ("Home X–Y Away") for results, so two pushes for the
        // same result share an ID and the second REPLACES the first (one visible
        // notification) instead of stacking. Different results still get distinct IDs.
        val notificationId = (type + "|" + title).hashCode()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Hint the app where to navigate based on notification type
            putExtra("navigate_to", if (channelId == CHANNEL_RESULTS) "profile" else "predictions")
        }
        val pending = PendingIntent.getActivity(
            this, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        // Stable ID → a duplicate of the same result collapses onto the existing one.
        manager.notify(notificationId, notification)
    }

    private fun ensureChannels(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEADLINES, "Deadline Reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Alerts before prediction deadlines close" }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, "Results & Winners", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "GW results, winner announcements, and score updates" }
        )
    }

    companion object {
        const val CHANNEL_DEADLINES = "big6ix_deadlines"
        const val CHANNEL_RESULTS   = "big6ix_results"
    }
}
