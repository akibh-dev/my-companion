package com.akib.mycompanion.ai.models

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class WordPreview(val source: String, val candidates: List<String>)

sealed interface TransliterationPreview {
    data class Ready(
        val text: String,
        val words: List<WordPreview>,
        val loadMillis: Long,
        val inferenceMillis: Long,
    ) : TransliterationPreview

    data class Unavailable(val reason: String) : TransliterationPreview
}

/** Explicit evaluation only: every Latin word is treated as Banglish, including English. */
@Singleton
class IndicXlitPrototype @Inject constructor(@ApplicationContext private val context: Context) {
    private val modelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = IdleModelSession(
        modelScope,
        idleMillis = 30_000,
    ) { Session(context) }

    suspend fun warmUp() = withContext(Dispatchers.Default) { sessions.use { _, _ -> Unit } }

    suspend fun close() = withContext(Dispatchers.Default) { sessions.close() }
    suspend fun preview(text: String): TransliterationPreview = withContext(Dispatchers.Default) {
        withTimeoutOrNull(30_000) { evaluate(text) }
            ?: TransliterationPreview.Unavailable("Conversion took too long. Try a shorter message.")
    }

    private suspend fun evaluate(text: String): TransliterationPreview {
        if (text.isBlank()) return TransliterationPreview.Unavailable("Enter a Banglish message")
        if (text.length > MAX_INPUT) {
            return TransliterationPreview.Unavailable("Use at most $MAX_INPUT characters")
        }
        val words = LATIN_WORD.findAll(text).toList()
        if (words.any { it.value.length > MAX_WORD }) {
            return TransliterationPreview.Unavailable("Use words of at most $MAX_WORD letters")
        }
        if (words.isEmpty()) return TransliterationPreview.Ready(text, emptyList(), 0, 0)
        val coroutine = currentCoroutineContext()
        return try {
            val start = System.nanoTime()
            sessions.use { model, cold ->
                val loaded = System.nanoTime()
                val cache = mutableMapOf<String, List<String>>()
                val previews = words.map { match ->
                    coroutine.ensureActive()
                    val key = match.value.lowercase(Locale.ROOT)
                    WordPreview(match.value, cache.getOrPut(key) { model.candidates(key) { coroutine.ensureActive() } })
                }
                var index = 0
                val converted = LATIN_WORD.replace(text) { previews[index++].candidates.first() }
                TransliterationPreview.Ready(
                    converted,
                    previews,
                    if (cold) (loaded - start) / NANOS_PER_MILLI else 0,
                    (System.nanoTime() - loaded) / NANOS_PER_MILLI,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Input text is intentionally excluded from diagnostics.
            Log.w("IndicXlit", "Offline model preview failed", failure)
            TransliterationPreview.Unavailable("Offline model unavailable. Check the model files and try again.")
        }
    }

    private class Session(context: Context) : Closeable {
        private val environment = OrtEnvironment.getEnvironment()
        private val vocab =
            JSONObject(context.assets.open("indicxlit/vocab.json").bufferedReader().use { it.readText() })
        private val source = vocab.getJSONArray("src").let { array ->
            (0 until array.length()).associate { array.getString(it) to it.toLong() }
        }
        private val target = vocab.getJSONArray("tgt").let { array ->
            (0 until array.length()).map { array.getString(it) }
        }
        private val special = vocab.getJSONObject("special_tokens")
        private val eos = special.getLong("eos")
        private val forbidden = setOf("unk", "pad", "bos").map { special.getLong(it) }.toSet()
        private val encoder: OrtSession
        private val decoder: OrtSession

        init {
            OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setInterOpNumThreads(1)
                encoder = context.assets.open("indicxlit/indicxlit_encoder.onnx").use {
                    environment.createSession(it.readBytes(), options)
                }
                try {
                    decoder = context.assets.open("indicxlit/indicxlit_decoder_v2.onnx").use {
                        environment.createSession(it.readBytes(), options)
                    }
                } catch (failure: Throwable) {
                    encoder.close()
                    throw failure
                }
            }
        }

        fun candidates(word: String, checkCancellation: () -> Unit): List<String> {
            val input = listOf(source.getValue("__bn__")) + word.map { source.getValue(it.toString()) } + eos
            OnnxTensor.createTensor(environment, arrayOf(input.toLongArray())).use { tokens ->
                encoder.run(mapOf("src_tokens" to tokens)).use { encoded ->
                    val hidden = encoded.get("encoder_out").get() as OnnxTensor
                    val result = TransliterationBeam.decode(
                        eos,
                        forbidden,
                        checkCancellation = checkCancellation
                    ) { previous ->
                        OnnxTensor.createTensor(environment, arrayOf(previous.toLongArray())).use { prefix ->
                            decoder.run(mapOf("prev_tokens" to prefix, "encoder_out" to hidden)).use { decoded ->
                                val output = decoded.get("decoder_out").get() as OnnxTensor
                                val buffer = output.floatBuffer
                                val offset = (previous.size - 1) * target.size
                                FloatArray(target.size) { buffer.get(offset + it) }
                            }
                        }
                    }
                    return result.map { candidate ->
                        candidate.tokens.drop(1).takeWhile { it != eos }.joinToString("") { target[it.toInt()] }
                    }.filter { wordOut -> wordOut.isNotEmpty() && wordOut.all { it in '\u0980'..'\u09ff' } }
                        .distinct().also { require(it.isNotEmpty()) { "No complete Bengali candidate" } }
                }
            }
        }

        override fun close() {
            try {
                decoder.close()
            } finally {
                encoder.close()
            }
        }
    }

    private companion object {
        val LATIN_WORD = Regex("[A-Za-z]+")
        const val MAX_INPUT = 600
        const val MAX_WORD = 48
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
