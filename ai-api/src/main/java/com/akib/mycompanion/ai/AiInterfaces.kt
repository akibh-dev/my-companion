package com.akib.mycompanion.ai

import java.io.File
import java.util.Locale

/**
 * A model's answer, or "not ready" (not installed, still loading, switched off). On [NotReady] the
 * feature uses its fallback (plan §2: "every call may answer not ready").
 */
sealed interface AiResult<out T> {
    data class Ready<T>(val value: T) : AiResult<T>

    data class NotReady(val reason: String) : AiResult<Nothing>
}

fun <T> AiResult<T>.valueOrNull(): T? = (this as? AiResult.Ready)?.value

/** Banglish ("ami bhalo achi") to Bangla script ("আমি ভালো আছি"). */
interface Transliterator {
    suspend fun toBangla(text: String): AiResult<String>
}

/** Speaks text. [speak] returns when the text has been spoken, or [stop] was called. */
interface SpeechSynth {
    suspend fun speak(text: String, language: Locale = Locale.getDefault()): AiResult<Unit>

    fun stop()
}

/** A voice note to text. */
interface SpeechRecognizer {
    suspend fun transcribe(audio: File, language: Locale = Locale.getDefault()): AiResult<String>
}

data class Summary(val title: String, val summary: String, val actionItems: List<String>)

/** Title, short summary and action items for a note. */
interface Summarizer {
    suspend fun summarize(text: String): AiResult<Summary>
}
