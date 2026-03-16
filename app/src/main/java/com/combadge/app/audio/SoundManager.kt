package com.combadge.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages low-latency chirp playback.
 *
 * Chirps are pre-generated PCM buffers played via AudioTrack in static mode
 * for the lowest possible latency (< 20 ms on modern hardware).
 */
class SoundManager(context: Context) {

    private val tag = "SoundManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.IO)

    private var volume: Float = 1.0f

    // Pre-built PCM buffers
    private val activationPcm: ShortArray = ChirpSynthesizer.activationChirp()
    private val deactivationPcm: ShortArray = ChirpSynthesizer.deactivationChirp()
    private val incomingHailPcm: ShortArray = ChirpSynthesizer.incomingHailChirp()
    private val errorPcm: ShortArray = ChirpSynthesizer.errorChirp()
    private val disambiguationPcm: ShortArray = ChirpSynthesizer.disambiguationTone()

    fun setVolume(v: Float) { volume = v.coerceIn(0f, 1f) }

    fun playActivation()     = play(activationPcm)
    fun playDeactivation()   = play(deactivationPcm)
    fun playIncomingHail()   = play(incomingHailPcm)
    fun playError()          = play(errorPcm)
    fun playDisambiguation() = play(disambiguationPcm)

    private fun play(pcm: ShortArray) {
        scope.launch {
            try {
                val bufferSize = pcm.size * 2  // shorts → bytes
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(ChirpSynthesizer.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.setVolume(volume)
                track.write(pcm, 0, pcm.size)
                track.play()

                // Wait for playback to finish then release
                val durationMs = (pcm.size.toLong() * 1000L) / ChirpSynthesizer.SAMPLE_RATE
                Thread.sleep(durationMs + 50)
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.e(tag, "Chirp playback error", e)
            }
        }
    }

    fun release() {
        // Nothing persistent to release — each play() creates/destroys its own AudioTrack
    }
}
