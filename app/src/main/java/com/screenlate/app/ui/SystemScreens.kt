package com.screenlate.app.ui

import android.annotation.SuppressLint
import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import com.screenlate.app.R
import com.screenlate.app.accessibility.ServiceStatus
import com.screenlate.app.tile.TranslateTileService
import java.util.Locale

/** Setup state the app can detect by itself. Read again every time the screen resumes. */
data class SetupStatus(
    val serviceEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val batteryUnrestricted: Boolean,
) {
    companion object {
        fun read(context: Context) = SetupStatus(
            serviceEnabled = ServiceStatus.isEnabled(context),
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
        )
    }
}

/** Shortcuts into system settings screens used by the setup steps. */
object SystemScreens {
    private val vendorsWithBackgroundLimits = setOf("realme", "oppo", "oneplus")

    /** Realme and its sister brands stop background apps unless the user allows them by hand. */
    val needsVendorSteps: Boolean
        get() = Build.MANUFACTURER.lowercase(Locale.ROOT) in vendorsWithBackgroundLimits

    fun accessibilitySettings(context: Context) {
        open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun appDetails(context: Context) {
        open(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    fun notificationSettings(context: Context) {
        open(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }

    @SuppressLint("BatteryLife")
    fun batteryExemption(context: Context) {
        if (!open(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)))) {
            open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestTile(context: Context) {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(context, TranslateTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile),
            context.mainExecutor,
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                else -> null
            }
            message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }

    private fun packageUri(context: Context) = Uri.fromParts("package", context.packageName, null)

    private fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
