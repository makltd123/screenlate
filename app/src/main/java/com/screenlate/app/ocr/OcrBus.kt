package com.screenlate.app.ocr

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

/** The outcome of one OCR run, delivered to the accessibility service that started it. */
sealed interface OcrEvent {
    /** English lines found on screen; [millis] covers the whole run from the tap. */
    data class Lines(val lines: List<OcrLine>, val millis: Long) : OcrEvent

    /** The screen forbids capture (FLAG_SECURE), so the shot came out black. */
    data object SecureScreen : OcrEvent

    /** The shot was taken, but no English text was found on it. */
    data object NoText : OcrEvent

    /** The translation model is not installed. */
    data object NoModel : OcrEvent

    /** The user closed the capture permission dialog. */
    data object Cancelled : OcrEvent

    /** The run failed; [error] names the exception class only, never screen text. */
    data class Failed(val error: String) : OcrEvent
}

/** Same-process bridge from the OCR foreground service back to the accessibility service. */
object OcrBus {
    val events = MutableSharedFlow<OcrEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun post(event: OcrEvent) {
        events.tryEmit(event)
    }
}
