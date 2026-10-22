package com.screenlate.app.diagnostics

import android.view.accessibility.AccessibilityEvent

/**
 * In-memory log for the hidden diagnostics screen. It keeps event types, package names,
 * counts and timings only, never the text read from the screen.
 */
object Diagnostics {
    class Entry(val timeMillis: Long, val message: String)

    class Snapshot(
        val eventCount: Long,
        val refreshCount: Long,
        val events: List<Entry>,
        val refreshes: List<Entry>,
        val errors: List<Entry>,
    )

    private val events = Ring(50)
    private val refreshes = Ring(20)
    private val errors = Ring(10)

    @Volatile private var eventCount = 0L
    @Volatile private var refreshCount = 0L

    fun log(message: String) = events.add(message)

    fun onEvent(type: Int, packageName: String?) {
        eventCount++
        events.add("${AccessibilityEvent.eventTypeToString(type)} · ${packageName ?: "?"}")
    }

    fun onRefresh(windows: Int, texts: Int, occluded: Int, millis: Long, packageName: String?) {
        refreshCount++
        refreshes.add("окон $windows · текстов $texts · под окнами $occluded · $millis мс · ${packageName ?: "—"}")
    }

    fun onError(error: Throwable) {
        // Exception messages can quote screen text, so only the type and code locations are kept.
        val frames = error.stackTrace
        val top = frames.firstOrNull()
        val ours = frames.firstOrNull { it.className.startsWith("com.screenlate") }
        val where = listOfNotNull(top, ours).distinct().joinToString(" / ") { "${it.fileName}:${it.lineNumber}" }
        errors.add("${error.javaClass.simpleName} $where")
    }

    fun snapshot() = Snapshot(
        eventCount = eventCount,
        refreshCount = refreshCount,
        events = events.newestFirst(),
        refreshes = refreshes.newestFirst(),
        errors = errors.newestFirst(),
    )

    private class Ring(private val capacity: Int) {
        private val entries = ArrayDeque<Entry>(capacity)

        @Synchronized
        fun add(message: String) {
            if (entries.size == capacity) entries.removeFirst()
            entries.addLast(Entry(System.currentTimeMillis(), message))
        }

        @Synchronized
        fun newestFirst(): List<Entry> = entries.toList().asReversed()
    }
}
