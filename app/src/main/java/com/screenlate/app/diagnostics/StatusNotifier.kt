package com.screenlate.app.diagnostics

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.screenlate.app.R
import com.screenlate.app.ui.MainActivity

/** Tells the user the service hit an error and kept running. At most one notification a minute. */
object StatusNotifier {
    private const val CHANNEL_ID = "status"
    private const val ERROR_ID = 1
    private const val MIN_INTERVAL_MS = 60_000L

    @Volatile private var lastShownAt = -MIN_INTERVAL_MS

    fun showError(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastShownAt < MIN_INTERVAL_MS) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        lastShownAt = now

        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.notification_channel_status))
                .build(),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(context.getString(R.string.notification_error_title))
            .setContentText(context.getString(R.string.notification_error_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(ERROR_ID, notification)
    }
}
