package com.invenium.thebig6ix.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.invenium.thebig6ix.MainActivity
import com.invenium.thebig6ix.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.TimeUnit

class DeadlineReminderWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return@withContext Result.success() // Not logged in, nothing to do

        val db = FirebaseFirestore.getInstance()

        return@withContext try {
            val now  = Date()
            val in24 = Date(now.time + 24 * 3_600_000L)

            // Find fixtures whose deadline falls within the next 24 hours
            val allFixtures = db.collection("fixtures").get().await()
            val nearDeadline = allFixtures.documents.filter { doc ->
                val deadline = doc.getTimestamp("deadline")?.toDate() ?: return@filter false
                deadline.after(now) && deadline.before(in24)
            }

            if (nearDeadline.isEmpty()) return@withContext Result.success()

            // Identify the gameweek with the nearest deadline
            val gw = nearDeadline.mapNotNull { it.getLong("gameweek")?.toInt() }.minOrNull()
                ?: return@withContext Result.success()

            val gwFixtureIds = nearDeadline.map { it.id }.toSet()

            // Count how many of these the user has already predicted
            val preds = db.collection("predictions")
                .whereEqualTo("userId", uid)
                .whereEqualTo("gameweek", gw)
                .get().await()
            val predictedIds  = preds.documents.mapNotNull { it.getString("fixtureId") }.toSet()
            val unpredictedCt = gwFixtureIds.size - gwFixtureIds.intersect(predictedIds).size

            if (unpredictedCt <= 0) return@withContext Result.success()

            // Compute hours remaining
            val earliestDeadline = nearDeadline.mapNotNull { it.getTimestamp("deadline")?.toDate() }.minOrNull()
            val hoursLeft = earliestDeadline?.let { ((it.time - now.time) / 3_600_000L).toInt() } ?: 24

            showNotification(ctx, gw, unpredictedCt, hoursLeft)
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun showNotification(context: Context, gw: Int, unpredicted: Int, hoursLeft: Int) {
        val channelId = "big6ix_deadlines"
        val manager   = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Deadline Reminders", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Alerts before prediction deadlines close"
            manager.createNotificationChannel(ch)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "predictions")
        }
        val pending = PendingIntent.getActivity(
            context, GW_DEADLINE_NOTIF_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val matchWord = if (unpredicted == 1) "match" else "matches"
        val body = "You have $unpredicted $matchWord left to predict for GW$gw. Deadline in ${hoursLeft}h ⏰"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications_black_24dp)
            .setContentTitle("⚽ Prediction Deadline Approaching!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        manager.notify(GW_DEADLINE_NOTIF_ID, notification)
    }

    companion object {
        const val GW_DEADLINE_NOTIF_ID = 1001

        /** Call once at app start (from MainNavigation or Application) */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DeadlineReminderWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(1, TimeUnit.HOURS) // Don't fire immediately on first launch
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "big6ix_deadline_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
