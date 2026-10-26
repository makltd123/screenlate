package com.screenlate.app.ocr

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.screenlate.app.R
import com.screenlate.app.accessibility.ScreenRect
import com.screenlate.app.accessibility.TextFilter
import com.screenlate.app.diagnostics.Diagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Foreground service for the one-shot OCR capture (spec F6). It holds the MediaProjection,
 * waits until the screen stops changing, takes a single screenshot, recognises Latin text
 * and hands the English lines to the accessibility service through [OcrBus]. A new capture
 * needs a new tap on the floating button.
 */
class OcrCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var run: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        if (data == null) {
            OcrBus.post(OcrEvent.Failed("no grant"))
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        run?.cancel()
        run = scope.launch { capture(resultCode, data) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.ocr_notification_channel))
                .build(),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.ocr_notification_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun capture(resultCode: Int, data: Intent) {
        val startedAt = SystemClock.elapsedRealtime()
        var projection: MediaProjection? = null
        var display: VirtualDisplay? = null
        var reader: ImageReader? = null
        var thread: HandlerThread? = null
        // Exactly one outcome per run; the projection's own stop in `finally` must not add a failure.
        val settled = AtomicBoolean(false)
        fun settle(event: OcrEvent) {
            if (settled.compareAndSet(false, true)) OcrBus.post(event)
        }
        try {
            // Android 14 requires the foreground service of this type to run before this call.
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
            projection.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        settle(OcrEvent.Failed("projection stopped"))
                    }
                },
                Handler(mainLooper),
            )

            val (width, height, dpi) = screenSize()
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            val frames = Channel<Unit>(Channel.CONFLATED)
            val latest = LatestFrame()
            thread = HandlerThread("screenlate-ocr").apply { start() }
            reader.setOnImageAvailableListener({ imageReader ->
                try {
                    val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    synchronized(latest) {
                        val grid = FrameLogic.gridOf(image.planes[0].buffer, width, height, image.planes[0].rowStride)
                        latest.previousGrid = latest.grid
                        latest.image?.close()
                        latest.image = image
                        latest.grid = grid
                    }
                    frames.trySend(Unit)
                } catch (e: IllegalStateException) {
                    // The reader was closed while a frame arrived; the capture is over anyway.
                }
            }, Handler(thread.looper))

            @Suppress("DEPRECATION")
            display = projection.createVirtualDisplay("screenlate-ocr", width, height, dpi, reader.surface, 0)

            // Wait for the first frame, then for a gap with no new frames: the screen stopped changing.
            withTimeoutOrNull(FIRST_FRAME_MS) { frames.receive() } ?: error("no frame")
            val deadline = SystemClock.elapsedRealtime() + MAX_EXTRA_MS
            while (SystemClock.elapsedRealtime() < deadline) {
                val quiet = withTimeoutOrNull(QUIET_MS) { frames.receive() } == null
                if (quiet && latest.isStable()) break
            }

            val image = synchronized(latest) {
                val current = latest.image ?: error("no frame")
                latest.image = null
                latest.grid?.let { FrameLogic.isBlank(it) }?.let { blank ->
                    if (blank) {
                        current.close()
                        throw SecureScreenException()
                    }
                }
                current
            }
            val bitmap = try {
                image.toBitmap(width, height)
            } finally {
                image.close()
            }
            val found = recognize(bitmap)
            bitmap.recycle()
            val lines = OcrLines.prepare(found.map { it.first to it.second }, TextFilter::shouldTranslate)
            Diagnostics.log("OCR: снимок ${width}x$height, строк ${found.size}, английских ${lines.size}, ${SystemClock.elapsedRealtime() - startedAt} мс")
            if (lines.isEmpty()) {
                settle(OcrEvent.NoText)
            } else {
                settle(OcrEvent.Lines(lines, SystemClock.elapsedRealtime() - startedAt))
            }
        } catch (e: CancellationException) {
            // A new capture replaced this one; keep it from posting anything in `finally`.
            settled.set(true)
            throw e
        } catch (e: SecureScreenException) {
            Diagnostics.log("OCR: чёрный снимок, экран запрещает захват")
            settle(OcrEvent.SecureScreen)
        } catch (e: Exception) {
            Diagnostics.onError(e)
            settle(OcrEvent.Failed(e.javaClass.simpleName))
        } finally {
            runCatching { display?.release() }
            runCatching { reader?.close() }
            runCatching { thread?.quitSafely() }
            runCatching { projection?.stop() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Screen size in physical pixels at the current rotation, with the display's density. */
    private fun screenSize(): Triple<Int, Int, Int> {
        val windowManager = getSystemService(WindowManager::class.java)
        val dpi = resources.displayMetrics.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), dpi)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Triple(metrics.widthPixels, metrics.heightPixels, dpi)
        }
    }

    private fun Image.toBitmap(width: Int, height: Int): Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixels = IntArray(width * height)
        var index = 0
        for (y in 0 until height) {
            var pixel = y * plane.rowStride
            for (x in 0 until width) {
                val red = buffer.get(pixel).toInt() and 0xFF
                val green = buffer.get(pixel + 1).toInt() and 0xFF
                val blue = buffer.get(pixel + 2).toInt() and 0xFF
                pixels[index++] = -0x1000000 or (red shl 16) or (green shl 8) or blue
                pixel += 4
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private suspend fun recognize(bitmap: Bitmap): List<Pair<String, ScreenRect>> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            text.textBlocks.flatMap { block: Text.TextBlock ->
                block.lines.mapNotNull { line: Text.Line ->
                    line.boundingBox?.let { box ->
                        line.text to ScreenRect(box.left, box.top, box.right, box.bottom)
                    }
                }
            }
        } finally {
            recognizer.close()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }

    /** The newest frame the reader delivered, kept open until it is replaced or consumed. */
    private class LatestFrame {
        var image: Image? = null
        var grid: IntArray? = null
        var previousGrid: IntArray? = null

        /** True when the last two delivered frames showed the same picture. */
        fun isStable(): Boolean {
            val previous = previousGrid
            val current = grid
            return previous == null || current == null ||
                FrameLogic.difference(previous, current) < FrameLogic.STABLE_DIFFERENCE
        }
    }

    private class SecureScreenException : Exception("protected screen")

    companion object {
        private const val CHANNEL_ID = "ocr"
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val FIRST_FRAME_MS = 1_500L
        private const val QUIET_MS = 350L
        private const val MAX_EXTRA_MS = 1_500L

        fun start(context: Context, resultCode: Int, data: Intent) {
            context.startForegroundService(
                Intent(context, OcrCaptureService::class.java)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data),
            )
        }
    }
}
