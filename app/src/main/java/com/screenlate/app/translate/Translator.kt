package com.screenlate.app.translate

/**
 * Translates one English sentence to Russian. The rest of the app depends only on this, so the
 * ONNX engine can be swapped (for example for Bergamot) without touching anything else.
 */
interface Translator {
    suspend fun translate(sentence: String): String
}

/** Placeholder that marks text instead of translating it; used in tests. */
class StubTranslator : Translator {
    override suspend fun translate(sentence: String): String = "RU: [$sentence]"
}
