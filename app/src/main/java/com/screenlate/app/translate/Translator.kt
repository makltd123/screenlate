package com.screenlate.app.translate

/** Translates English strings to Russian. Returns exactly one result per input, in the same order. */
interface Translator {
    suspend fun translate(texts: List<String>): List<String>
}

/** Stage 1 placeholder: marks the text instead of translating it, so plaque placement can be checked. */
class StubTranslator : Translator {
    override suspend fun translate(texts: List<String>): List<String> = texts.map { "RU: [$it]" }
}
