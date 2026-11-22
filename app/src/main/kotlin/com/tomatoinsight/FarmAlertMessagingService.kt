package com.tomatoinsight

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

const val FARM_ALERT_CHANNEL_ID = "farm_alerts"

class FarmAlertMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "Farm update"
        val body = message.notification?.body ?: message.data["body"] ?: return
        showFarmAlert(this, title, body)
    }

    override fun onNewToken(token: String) {
        Log.d("TomatoInsight", "FCM token: $token")
    }
}

fun ensureFarmAlertChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
        FARM_ALERT_CHANNEL_ID,
        "Farm alerts",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Scan and care messages from Tomato Insight"
    }
    manager.createNotificationChannel(channel)
}

fun showFarmAlert(
    context: Context,
    title: String,
    body: String,
    summary: String = "",
    dueDateLabel: String = ""
): Boolean {
    ensureFarmAlertChannel(context)
    val openApp = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("open_from_notification", true)
            if (dueDateLabel.isNotBlank()) {
                putExtra("notification_due_label", dueDateLabel)
            }
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val cardTitle = title.trim().takeUnless { it.isBlank() || it.equals("Information!", ignoreCase = true) } ?: "Farm update"
    val cardBody = body.trim().ifBlank { return false }
    val cardSummary = summary.trim().ifBlank {
        cardBody.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().ifBlank { cardBody }
    }
    val notification = NotificationCompat.Builder(context, FARM_ALERT_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(context.getColor(R.color.farm_notification_accent))
        .setContentTitle(cardTitle)
        .setContentText(cardSummary)
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle(cardTitle)
                .bigText(cardBody)
        )
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(openApp)
        .setOnlyAlertOnce(true)
        .build()

    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return runCatching {
        manager.notify((cardTitle + cardBody).hashCode(), notification)
        true
    }.getOrDefault(false)
}
