package com.combadge.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.combadge.app.network.UdpAudioSocket
import kotlinx.coroutines.*
import java.util.concurrent.ArrayBlockingQueue

/**
 * Manages the full-duplex voice channel.
 *
 * Audio format: PCM 16-bit mono, 16 kHz sample rate.
 * Packet payload: 640 bytes = 320 shorts = 20 ms of audio at 16 kHz.
 *
 * Uses a small jitter buffer (~5 packets / 100 ms) for smooth playback.
 */
class VoiceStreamManager {

    companion object {
        private const val TAG = "VoiceStreamManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE_SAMPLES = 320       // 20 ms at 16 kHz
        const val FRAME_SIZE_BYTES = FRAME_SIZE_SAMPLES * 2  // 640 bytes
        const val JITTER_BUFFER_SIZE = 5         // 5 packets ≈ 100 ms
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var udpSocket: UdpAudioSocket? = null

    private val jitterBuffer = ArrayBlockingQueue<ShortArray>(JITTER_BUFFER_SIZE * 2)

    private var captureJob: Job? = null
    private var playbackJob: Job? = null

    @Volatile private var running = false

    /**
     * Start full-duplex voice channel.
     * @param udpSocket The socket to use for sending/receiving audio UDP packets.
     */
    fun start(udpSocket: UdpAudioSocket) {
        if (running) return
        running = true
        this.udpSocket = udpSocket
        jitterBuffer.clear()

        startCapture()
        startPlayback()
        startReceive()
    }

    fun stop() {
        if (!running) return
        running = false

        captureJob?.cancel()
        playbackJob?.cancel()

        recorder?.stop()
        recorder?.release()
        recorder = null

        player?.stop()
        player?.release()
        player = null

        jitterBuffer.clear()
        Log.d(TAG, "Voice stream stopped")
    }

    private fun startCapture() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufSize = maxOf(minBuf, FRAME_SIZE_BYTES * 4)

        recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                bufSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "RECORD_AUDIO permission denied", e)
            return
        }

        if (recorder?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            recorder?.release()
            recorder = null
            return
        }

        recorder?.startRecording()

        captureJob = scope.launch {
            val frame = ShortArray(FRAME_SIZE_SAMPLES)
            while (running && isActive) {
                val read = recorder?.read(frame, 0, FRAME_SIZE_SAMPLES) ?: -1
                if (read > 0) {
                    udpSocket?.send(frame.copyOf(read))
                }
            }
        }
    }

    private fun startPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val bufSize = maxOf(minBuf, FRAME_SIZE_BYTES * JITTER_BUFFER_SIZE * 2)

        player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        player?.play()

        playbackJob = scope.launch {
            // Pre-fill jitter buffer
            while (running && isActive && jitterBuffer.size < JITTER_BUFFER_SIZE) {
                delay(5)
            }

            while (running && isActive) {
                val frame = jitterBuffer.poll()
                if (frame != null) {
                    player?.write(frame, 0, frame.size)
                } else {
                    // Buffer underrun — write silence
                    player?.write(ShortArray(FRAME_SIZE_SAMPLES), 0, FRAME_SIZE_SAMPLES)
                }
            }
        }
    }

    private fun startReceive() {
        scope.launch {
            udpSocket?.receive { frame ->
                if (jitterBuffer.size < JITTER_BUFFER_SIZE * 2) {
                    jitterBuffer.offer(frame)
                }
                // else: drop packet to prevent buffer overflow
            }
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
