package com.screenlate.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.screenlate.app.R
import com.screenlate.app.accessibility.ServiceStatus
import com.screenlate.app.settings.SettingsRepository
import com.screenlate.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Quick Settings tile that switches translation on and off. */
class TranslateTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings by lazy { SettingsRepository.get(this) }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { render(settings.enabled.first()) }
    }

    override fun onClick() {
        super.onClick()
        if (!ServiceStatus.isEnabled(this)) {
            openApp()
            return
        }
        scope.launch {
            val next = !settings.enabled.first()
            settings.setEnabled(next)
            render(next)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(enabled: Boolean) {
        val tile = qsTile ?: return
        val serviceOn = ServiceStatus.isEnabled(this)
        tile.state = if (serviceOn && enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when {
                    !serviceOn -> R.string.tile_subtitle_service_off
                    enabled -> R.string.tile_subtitle_on
                    else -> R.string.tile_subtitle_off
                },
            )
        }
        tile.updateTile()
    }

    /** Without the accessibility service the tile can't do anything, so it opens the setup screen instead. */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
