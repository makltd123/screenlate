package com.screenlate.app.model

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.screenlate.app.BuildConfig
import com.screenlate.app.R
import com.screenlate.app.translate.ModelManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Downloads the translation model once from the fixed release URL with the system download
 * manager, checks its SHA-256 and unpacks it into app storage. This is the app's only network use.
 */
class ModelManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onChanged: suspend () -> Unit,
) {
    sealed interface State {
        data object Missing : State

        data class Downloading(val downloaded: Long, val total: Long) : State

        data object Installing : State

        data class Ready(val bytes: Long) : State

        data class Failed(val reason: Reason) : State
    }

    enum class Reason { Network, NoSpace, Checksum, Storage }

    private class ChecksumMismatch : IOException("model checksum mismatch")

    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val root = File(context.filesDir, "model")
    private val installed = File(root, BuildConfig.MODEL_VERSION)
    private val stateFlow = MutableStateFlow(initialState())
    private var watcher: Job? = null

    val state: StateFlow<State> = stateFlow.asStateFlow()

    fun readyDir(): File? = installed.takeIf { isReady() }

    /** Picks up a download that finished or progressed while the app was closed. Cheap; call on every resume. */
    fun refresh() {
        if (isReady()) {
            stateFlow.value = State.Ready(sizeOf(installed))
            return
        }
        if (watcher?.isActive == true) return
        val id = findDownload()
        if (id != null) {
            watch(id)
        } else if (stateFlow.value !is State.Failed) {
            stateFlow.value = State.Missing
        }
    }

    fun download() {
        if (isReady() || watcher?.isActive == true) return
        findDownload()?.let { downloads.remove(it) }
        val request = DownloadManager.Request(Uri.parse(BuildConfig.MODEL_URL))
            .setTitle(context.getString(R.string.model_download_title))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, null, ZIP_NAME)
        val id = downloads.enqueue(request)
        stateFlow.value = State.Downloading(0, BuildConfig.MODEL_BYTES)
        watch(id)
    }

    /** Deletes the installed model and downloads it again. */
    fun reinstall() {
        scope.launch {
            withContext(Dispatchers.IO) { root.deleteRecursively() }
            onChanged()
            stateFlow.value = State.Missing
            download()
        }
    }

    private fun watch(id: Long) {
        watcher = scope.launch {
            while (true) {
                val row = query(id)
                when {
                    row == null -> {
                        stateFlow.value = State.Failed(Reason.Network)
                        return@launch
                    }
                    row.status == DownloadManager.STATUS_SUCCESSFUL -> {
                        install(id)
                        return@launch
                    }
                    row.status == DownloadManager.STATUS_FAILED -> {
                        downloads.remove(id)
                        val noSpace = row.reason == DownloadManager.ERROR_INSUFFICIENT_SPACE
                        stateFlow.value = State.Failed(if (noSpace) Reason.NoSpace else Reason.Network)
                        return@launch
                    }
                    else -> stateFlow.value = State.Downloading(row.downloaded, row.total.takeIf { it > 0 } ?: BuildConfig.MODEL_BYTES)
                }
                delay(POLL_MS)
            }
        }
    }

    private suspend fun install(id: Long) {
        stateFlow.value = State.Installing
        val result = withContext(Dispatchers.IO) { runCatching { unpack(zipFile()) } }
        downloads.remove(id) // also deletes the downloaded zip
        result.fold(
            onSuccess = {
                onChanged()
                stateFlow.value = State.Ready(sizeOf(installed))
            },
            onFailure = { error ->
                stateFlow.value = State.Failed(if (error is ChecksumMismatch) Reason.Checksum else Reason.Storage)
            },
        )
    }

    private fun unpack(zip: File) {
        if (sha256(zip) != BuildConfig.MODEL_SHA256) throw ChecksumMismatch()
        val staging = File(root, "${BuildConfig.MODEL_VERSION}.partial").apply {
            deleteRecursively()
            mkdirs()
        }
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val name = entry.name
                // The archive is flat; anything else means it is not ours.
                if (entry.isDirectory || name.isEmpty() || '/' in name || '\\' in name || name.startsWith(".")) {
                    throw IOException("unexpected entry in model archive")
                }
                File(staging, name).outputStream().use { input.copyTo(it) }
            }
        }
        ModelManifest.read(staging)
        File(staging, READY_MARKER).writeText(BuildConfig.MODEL_SHA256)
        installed.deleteRecursively()
        if (!staging.renameTo(installed)) throw IOException("cannot move the model into place")
        root.listFiles()?.filter { it != installed }?.forEach { it.deleteRecursively() }
    }

    private class Row(val status: Int, val reason: Int, val downloaded: Long, val total: Long)

    private fun query(id: Long): Row? =
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            Row(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }

    /** The download manager only lists this app's own downloads. */
    private fun findDownload(): Long? =
        downloads.query(DownloadManager.Query())?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val uriColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            while (cursor.moveToNext()) {
                if (cursor.getString(uriColumn) == BuildConfig.MODEL_URL) return cursor.getLong(idColumn)
            }
            null
        }

    private fun zipFile() = File(context.getExternalFilesDir(null), ZIP_NAME)

    private fun isReady() = File(installed, READY_MARKER).isFile

    private fun initialState(): State = if (isReady()) State.Ready(sizeOf(installed)) else State.Missing

    private fun sizeOf(dir: File) = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ZIP_NAME = "screenlate-model.zip"
        const val READY_MARKER = "installed"
        const val POLL_MS = 500L
    }
}
