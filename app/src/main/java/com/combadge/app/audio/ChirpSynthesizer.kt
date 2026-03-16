package com.combadge.app.audio

import kotlin.math.*

/**
 * Generates combadge chirp sounds as raw 16-bit PCM buffers.
 *
 * All chirps are synthesized at 44100 Hz, mono, 16-bit signed PCM.
 * The resulting ShortArrays can be loaded into a SoundPool via AudioTrack.
 */
object ChirpSynthesizer {

    const val SAMPLE_RATE = 44100

    /**
     * Activation chirp: ascending two-tone
     * ~800 Hz for 80 ms → ~1200 Hz for 120 ms, smooth crossfade
     */
    fun activationChirp(): ShortArray = buildChirp(
        tones = listOf(
            ToneSegment(freq = 800.0, durationMs = 80, fadeInMs = 5, fadeOutMs = 10),
            ToneSegment(freq = 1200.0, durationMs = 120, fadeInMs = 10, fadeOutMs = 20)
        )
    )

    /**
     * Deactivation / close chirp: descending two-tone
     * ~1200 Hz for 80 ms → ~800 Hz for 100 ms
     */
    fun deactivationChirp(): ShortArray = buildChirp(
        tones = listOf(
            ToneSegment(freq = 1200.0, durationMs = 80, fadeInMs = 5, fadeOutMs = 10),
            ToneSegment(freq = 800.0, durationMs = 100, fadeInMs = 10, fadeOutMs = 20)
        )
    )

    /**
     * Incoming hail chirp: three-tone ascending
     * ~600 Hz → ~900 Hz → ~1200 Hz, each ~60 ms
     */
    fun incomingHailChirp(): ShortArray = buildChirp(
        tones = listOf(
            ToneSegment(freq = 600.0, durationMs = 60, fadeInMs = 5, fadeOutMs = 8),
            ToneSegment(freq = 900.0, durationMs = 60, fadeInMs = 8, fadeOutMs = 8),
            ToneSegment(freq = 1200.0, durationMs = 60, fadeInMs = 8, fadeOutMs = 15)
        )
    )

    /**
     * Error chirp: flat low tone
     * ~300 Hz for 200 ms
     */
    fun errorChirp(): ShortArray = buildChirp(
        tones = listOf(
            ToneSegment(freq = 300.0, durationMs = 200, fadeInMs = 5, fadeOutMs = 20)
        )
    )

    /**
     * Disambiguation tone: brief neutral chime
     * ~1000 Hz for 100 ms
     */
    fun disambiguationTone(): ShortArray = buildChirp(
        tones = listOf(
            ToneSegment(freq = 1000.0, durationMs = 100, fadeInMs = 5, fadeOutMs = 20)
        )
    )

    // ------------------------------------------------------------------ //

    private data class ToneSegment(
        val freq: Double,
        val durationMs: Int,
        val fadeInMs: Int = 0,
        val fadeOutMs: Int = 0,
        val amplitude: Double = 0.7
    )

    private fun buildChirp(tones: List<ToneSegment>): ShortArray {
        val buffers = tones.map { seg ->
            val numSamples = (SAMPLE_RATE * seg.durationMs / 1000.0).toInt()
            val fadeInSamples = (SAMPLE_RATE * seg.fadeInMs / 1000.0).toInt()
            val fadeOutSamples = (SAMPLE_RATE * seg.fadeOutMs / 1000.0).toInt()
            val buf = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                var sample = seg.amplitude * sin(2.0 * PI * seg.freq * t)

                // Fade in
                if (i < fadeInSamples) {
                    sample *= i.toDouble() / fadeInSamples
                }
                // Fade out
                val fromEnd = numSamples - 1 - i
                if (fromEnd < fadeOutSamples) {
                    sample *= fromEnd.toDouble() / fadeOutSamples
                }

                buf[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            buf
        }

        // Concatenate all segments with a tiny 5 ms gap between them
        val gapSamples = (SAMPLE_RATE * 5 / 1000.0).toInt()
        val totalSamples = buffers.sumOf { it.size } + (buffers.size - 1) * gapSamples
        val result = ShortArray(totalSamples)
        var pos = 0
        buffers.forEachIndexed { idx, buf ->
            buf.copyInto(result, pos)
            pos += buf.size
            if (idx < buffers.size - 1) pos += gapSamples
        }
        return result
    }
}
