package com.screenlate.app.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceStatus {
    private val runningState = MutableStateFlow(false)

    /** True while the accessibility service is connected in this process. */
    val running: StateFlow<Boolean> = runningState.asStateFlow()

    internal fun setRunning(value: Boolean) {
        runningState.value = value
    }

    /** Whether the user has switched the service on in the system accessibility settings. */
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ScreenTranslateService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
