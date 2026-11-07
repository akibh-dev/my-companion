package com.akib.mycompanion.ai.models

import kotlin.math.exp
import kotlin.math.ln

/** Bounded character decoding. Only completed hypotheses can become spoken words. */
internal object TransliterationBeam {
    data class Candidate(val tokens: List<Long>, val score: Double)

    fun decode(
        eos: Long,
        forbidden: Set<Long>,
        width: Int = 4,
        maxSteps: Int = 48,
        checkCancellation: () -> Unit = {},
        logits: (List<Long>) -> FloatArray,
    ): List<Candidate> {
        var active = listOf(Candidate(listOf(eos), 0.0))
        val completed = mutableListOf<Candidate>()
        repeat(maxSteps) {
            checkCancellation()
            val next = mutableListOf<Candidate>()
            for (beam in active) {
                checkCancellation()
                val scores = logits(beam.tokens)
                require(scores.isNotEmpty() && scores.all { it.isFinite() }) { "Invalid model scores" }
                val maximum = scores.max().toDouble()
                val normalizer = maximum + ln(scores.sumOf { exp(it - maximum) })
                scores.indices.asSequence()
                    .filter { it.toLong() !in forbidden && (it.toLong() != eos || beam.tokens.size > 1) }
                    .sortedByDescending { scores[it] }
                    .take(width * 2)
                    .forEach { token ->
                        val candidate = Candidate(beam.tokens + token.toLong(), beam.score + scores[token] - normalizer)
                        if (token.toLong() == eos) completed += candidate else next += candidate
                    }
            }
            active = next.sortedByDescending { it.score }.take(width)
            if (active.isEmpty()) return rank(completed, width)
            // Future log probabilities cannot increase raw score. Bound the best possible
            // length-normalized score using the maximum allowed output length.
            val ranked = rank(completed, width)
            if (ranked.size >= width && active.first().score / penalty(maxSteps + 1) <= normalized(ranked.last())) {
                return ranked
            }
        }
        return rank(completed, width)
    }

    private fun penalty(length: Int) = (5.0 + length) / 6.0

    private fun normalized(candidate: Candidate) = candidate.score / penalty(candidate.tokens.size - 1)

    private fun rank(candidates: List<Candidate>, width: Int) =
        candidates.sortedByDescending(::normalized).take(width)
}
