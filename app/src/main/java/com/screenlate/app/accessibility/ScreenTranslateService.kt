package com.screenlate.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.screenlate.app.diagnostics.Diagnostics
import com.screenlate.app.diagnostics.StatusNotifier
import com.screenlate.app.overlay.OverlayRenderer
import com.screenlate.app.overlay.Plaque
import com.screenlate.app.settings.SettingsRepository
import com.screenlate.app.translate.StubTranslator
import com.screenlate.app.translate.Translator
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reads English text from the screen and draws translations over it.
 * State lives on the main thread; node traversal runs on one background thread.
 */
class ScreenTranslateService : AccessibilityService() {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error -> reportFailure(error) },
    )
    private val collectDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val translator: Translator = StubTranslator()

    private lateinit var packages: PackagePolicy
    private lateinit var collector: ScreenTextCollector
    private lateinit var overlay: OverlayRenderer
    private lateinit var debouncer: EventDebouncer

    private var connected = false
    private var enabled = false
    private var screenOn = true
    private var lastMotionAt = 0L
    private var refreshJob: Job? = null
    private var showJob: Job? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    stopWork()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    requestRefresh()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        packages = PackagePolicy.forDevice(this)
        collector = ScreenTextCollector(this, packages)
        overlay = OverlayRenderer(this)
        debouncer = EventDebouncer(scope, QUIET_MS, MAX_WAIT_MS, SystemClock::uptimeMillis, ::requestRefresh)
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(this, screenReceiver, screenFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        connected = true
        ServiceStatus.setRunning(true)
        Diagnostics.log("Служба подключена")

        scope.launch {
            SettingsRepository.get(this@ScreenTranslateService).enabled.collect { value ->
                enabled = value
                if (value) requestRefresh() else stopWork()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!connected || !enabled || !screenOn) return
        try {
            val packageName = event.packageName?.toString()
            if (packages.isIgnored(packageName)) return
            Diagnostics.onEvent(event.eventType, packageName)
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) {
                onMotion()
            }
            debouncer.onEvent()
        } catch (e: RuntimeException) {
            reportFailure(e)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!connected) return
        overlay.onConfigurationChanged(newConfig)
        onMotion()
        debouncer.onEvent()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (connected) {
            connected = false
            unregisterReceiver(screenReceiver)
            overlay.clear()
            ServiceStatus.setRunning(false)
            Diagnostics.log("Служба остановлена")
        }
        scope.cancel()
        super.onDestroy()
    }

    /** Scrolling and window changes move text, so plaques hide at once and return once things settle. */
    private fun onMotion() {
        lastMotionAt = SystemClock.uptimeMillis()
        showJob?.cancel()
        overlay.hide()
    }

    private fun requestRefresh() {
        if (!connected || !enabled || !screenOn) return
        refreshJob?.cancel()
        refreshJob = scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val started = SystemClock.uptimeMillis()
        val screen = withContext(collectDispatcher) { collector.collect() }
        val translations = translator.translate(screen.items.map { it.text })
        overlay.render(screen.items.mapIndexed { i, item -> Plaque(item.key, translations[i], item.bounds) })
        Diagnostics.onRefresh(
            windows = screen.windowCount,
            texts = screen.items.size,
            occluded = screen.occluded,
            millis = SystemClock.uptimeMillis() - started,
            packageName = screen.items.firstOrNull()?.packageName,
        )
        scheduleShow()
    }

    private fun scheduleShow() {
        showJob?.cancel()
        val wait = lastMotionAt + SHOW_DELAY_MS - SystemClock.uptimeMillis()
        showJob = scope.launch {
            if (wait > 0) delay(wait)
            overlay.show()
        }
    }

    private fun stopWork() {
        if (!connected) return
        debouncer.cancel()
        refreshJob?.cancel()
        showJob?.cancel()
        overlay.clear()
    }

    private fun reportFailure(error: Throwable) {
        Diagnostics.onError(error)
        StatusNotifier.showError(this)
    }

    private companion object {
        const val QUIET_MS = 250L
        const val MAX_WAIT_MS = 1_000L
        const val SHOW_DELAY_MS = 300L
    }
}
