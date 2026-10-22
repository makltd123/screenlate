package com.screenlate.app.translate

import java.io.Closeable

/** A loaded SentencePiece model backed by the native library. Thread-safe; [close] frees it. */
class SentencePiece private constructor(private var handle: Long) : Closeable {

    fun encode(text: String): List<String> =
        SentencePieceJni.nativeEncode(checkedHandle(), text.toByteArray(Charsets.UTF_8)).map { String(it, Charsets.UTF_8) }

    fun decode(pieces: List<String>): String {
        val bytes = pieces.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray()
        return String(SentencePieceJni.nativeDecode(checkedHandle(), bytes), Charsets.UTF_8)
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            SentencePieceJni.nativeFree(handle)
            handle = 0L
        }
    }

    private fun checkedHandle(): Long = handle.also { check(it != 0L) { "SentencePiece model is closed" } }

    companion object {
        fun load(model: ByteArray) = SentencePiece(SentencePieceJni.nativeLoad(model))
    }
}

internal object SentencePieceJni {
    init {
        System.loadLibrary("spm_jni")
    }

    @JvmStatic external fun nativeLoad(model: ByteArray): Long

    @JvmStatic external fun nativeEncode(handle: Long, text: ByteArray): Array<ByteArray>

    @JvmStatic external fun nativeDecode(handle: Long, pieces: Array<ByteArray>): ByteArray

    @JvmStatic external fun nativeFree(handle: Long)
}
