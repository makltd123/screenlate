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
import com.screenlate.app.AppServices
import com.screenlate.app.R
import com.screenlate.app.diagnostics.Diagnostics
import com.screenlate.app.diagnostics.StatusNotifier
import com.screenlate.app.model.ModelManager
import com.screenlate.app.overlay.BubbleAction
import com.screenlate.app.overlay.FloatingBubble
import com.screenlate.app.overlay.OverlayRenderer
import com.screenlate.app.overlay.OverlayStyle
import com.screenlate.app.overlay.Plaque
import com.screenlate.app.services
import com.screenlate.app.settings.AppSettings
import com.screenlate.app.settings.BubblePosition
import com.screenlate.app.settings.DisplayMode
import com.screenlate.app.settings.SettingsRepository
import com.screenlate.app.translate.TextSegments
import com.screenlate.app.translate.TranslationQueue
import com.screenlate.app.ui.MainActivity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

    private lateinit var app: AppServices
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var packages: PackagePolicy
    private lateinit var collector: ScreenTextCollector
    private lateinit var overlay: OverlayRenderer
    private lateinit var bubble: FloatingBubble
    private lateinit var debouncer: EventDebouncer
    private lateinit var queue: TranslationQueue

    private var connected = false
    private var settings = AppSettings()
    private var bubblePosition = BubblePosition()
    private var screenOn = true
    private var lastMotionAt = 0L
    private var screenItems: List<TextItem> = emptyList()
    private var refreshJob: Job? = null
    private var showJob: Job? = null

    /** The app in front, for "pause in this app" on the floating button. */
    private var foregroundPackage: String? = null
    private val pausedPackages = LinkedHashSet<String>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    stopWork()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    if (!settings.economy) requestRefresh()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = services
        settingsRepository = SettingsRepository.get(this)
        app.modelManager.refresh()
        queue = TranslationQueue(
            scope = scope,
            translate = { sentence ->
                app.repository.translate(sentence)
                renderAvailable()
            },
            onFailure = ::reportFailure,
        )
        packages = PackagePolicy.forDevice(this)
        collector = ScreenTextCollector(this, packages)
        overlay = OverlayRenderer(this)
        bubble = FloatingBubble(
            context = this,
            onTap = ::onBubbleTap,
            onLongPress = ::showBubbleMenu,
            onMoved = { position ->
                bubblePosition = position
                scope.launch { settingsRepository.setBubblePosition(position) }
            },
        )
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
            bubblePosition = settingsRepository.bubblePosition.first()
            settingsRepository.settings.collect(::applySettings)
        }
        scope.launch {
            app.modelManager.state.collect { state ->
                if (state is ModelManager.State.Ready && !settings.economy) requestRefresh()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!connected || !settings.enabled || !screenOn) return
        try {
            val packageName = event.packageName?.toString()
            if (packages.isSystem(packageName)) return
            val windowChanged = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            if (windowChanged) foregroundPackage = packageName
            if (packages.isIgnored(packageName)) {
                // An excluded or paused app came to the front: nothing of the previous screen may stay.
                if (windowChanged) stopWork()
                return
            }
            Diagnostics.onEvent(event.eventType, packageName)
            val motion = windowChanged || event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
            if (motion) onMotion()
            if (settings.economy) {
                // Economy mode translates only on a tap of the floating button; any movement ends it.
                if (motion) stopWork()
                return
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
        if (settings.economy) stopWork() else debouncer.onEvent()
        if (settings.showBubble) bubble.show(bubblePosition)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (connected) {
            connected = false
            unregisterReceiver(screenReceiver)
            overlay.clear()
            bubble.hide()
            ServiceStatus.setRunning(false)
            Diagnostics.log("Служба остановлена")
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun applySettings(new: AppSettings) {
        val old = settings
        settings = new
        packages.excluded = new.excludedPackages
        collector.translateEditable = new.translateEditable
        overlay.setStyle(OverlayStyle(new.displayMode, new.plaqueColor, new.overlayOpacity, new.minTextSp))
        scope.launch { app.engine.setThreads(new.threads) }
        if (new.showBubble) bubble.show(bubblePosition) else bubble.hide()
        bubble.setActive(new.enabled)
        when {
            !new.enabled -> stopWork()
            new.economy -> if (!old.economy) stopWork()
            else -> requestRefresh()
        }
    }

    /** Toggles translation; in economy mode translates the current screen once. */
    private fun onBubbleTap() {
        if (!settings.economy) {
            scope.launch { settingsRepository.setEnabled(!settings.enabled) }
            return
        }
        if (!settings.enabled) scope.launch { settingsRepository.setEnabled(true) }
        if (!screenOn) return
        lastMotionAt = 0L
        refreshJob?.cancel()
        refreshJob = scope.launch { refresh() }
    }

    private fun showBubbleMenu() {
        val actions = ArrayList<BubbleAction>()
        for (mode in DisplayMode.entries) {
            val label = getString(
                when (mode) {
                    DisplayMode.Replace -> R.string.mode_replace
                    DisplayMode.Overlay -> R.string.mode_overlay
                    DisplayMode.Hint -> R.string.mode_hint
                },
            )
            val mark = if (mode == settings.displayMode) "✓  " else "     "
            actions += BubbleAction(mark + label) { scope.launch { settingsRepository.update { it.copy(displayMode = mode) } } }
        }
        actions += BubbleAction(getString(R.string.bubble_ocr_later), enabled = false) {}
        foregroundPackage?.takeIf { !packages.isSystem(it) && it !in settings.excludedPackages }?.let { target ->
            val paused = target in pausedPackages
            val label = getString(if (paused) R.string.bubble_resume_app else R.string.bubble_pause_app, appLabel(target))
            actions += BubbleAction(label) { togglePause(target) }
        }
        val texts = overlay.visibleTexts
        actions += BubbleAction(getString(R.string.bubble_full_text), enabled = texts.isNotEmpty()) {
            bubble.showText(getString(R.string.bubble_full_text), overlay.visibleTexts)
        }
        actions += BubbleAction(getString(R.string.bubble_settings)) { openSettings() }
        bubble.showMenu(actions)
    }

    private fun togglePause(target: String) {
        if (!pausedPackages.remove(target)) pausedPackages += target
        packages.paused = pausedPackages.toSet()
        if (target in pausedPackages) stopWork() else if (!settings.economy) requestRefresh()
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_SCREEN, MainActivity.SCREEN_SETTINGS),
        )
    }

    private fun appLabel(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    /** Scrolling and window changes move text, so plaques hide at once and return once things settle. */
    private fun onMotion() {
        lastMotionAt = SystemClock.uptimeMillis()
        showJob?.cancel()
        overlay.hide()
    }

    private fun requestRefresh() {
        if (!connected || !settings.enabled || !screenOn) return
        refreshJob?.cancel()
        refreshJob = scope.launch { refresh() }
    }

    private suspend fun refresh() {
        if (app.modelManager.readyDir() == null) {
            stopWork()
            return
        }
        val started = SystemClock.uptimeMillis()
        val screen = withContext(collectDispatcher) { collector.collect() }
        screenItems = screen.items
        val sentences = screen.items.flatMap { TextSegments.sentences(it.text) }.distinct()
        app.repository.preload(sentences)
        val missing = sentences.filter { app.repository.cached(it) == null }
        renderAvailable()
        queue.replace(missing)
        Diagnostics.onRefresh(
            windows = screen.windowCount,
            texts = screen.items.size,
            occluded = screen.occluded,
            cached = sentences.size - missing.size,
            queued = missing.size,
            millis = SystemClock.uptimeMillis() - started,
            packageName = screen.items.firstOrNull()?.packageName,
        )
        scheduleShow()
    }

    /** Draws every item whose sentences are all translated; the rest appear as the queue gets to them. */
    private fun renderAvailable() {
        if (!connected) return
        overlay.render(
            screenItems.mapNotNull { item ->
                app.repository.assembled(item.text)?.let { translation ->
                    Plaque(item.key, translation, item.bounds, item.text.length, item.lineHeight)
                }
            },
        )
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
        screenItems = emptyList()
        queue.replace(emptyList())
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
