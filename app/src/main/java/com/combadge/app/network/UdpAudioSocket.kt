package com.combadge.app.network

import android.util.Log
import com.combadge.app.audio.VoiceStreamManager.Companion.FRAME_SIZE_BYTES
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * UDP socket for real-time voice streaming.
 *
 * Packet layout (12-byte header + PCM payload):
 *   [4 bytes] session ID hash (int, big-endian)
 *   [4 bytes] sequence number (int, big-endian)
 *   [4 bytes] timestamp millis truncated to int (big-endian)
 *   [N bytes] PCM payload (16-bit signed shorts, little-endian)
 *
 * Each packet carries FRAME_SIZE_BYTES (640 bytes) of PCM = 20 ms audio at 16 kHz.
 */
class UdpAudioSocket(private val sessionId: String) {

    companion object {
        private const val TAG = "UdpAudioSocket"
        const val HEADER_SIZE = 12
        private const val MAX_PACKET = HEADER_SIZE + FRAME_SIZE_BYTES + 16
        private const val SOCKET_TIMEOUT_MS = 500
    }

    private var socket: DatagramSocket? = null
    private var remoteAddress: InetAddress? = null
    private var remotePort: Int = 0
    private var sequenceNumber = 0

    val localPort: Int get() = socket?.localPort ?: 0

    fun open() {
        socket = DatagramSocket()
        socket?.soTimeout = SOCKET_TIMEOUT_MS
        Log.d(TAG, "UDP socket opened on port ${socket?.localPort}")
    }

    fun setRemote(address: InetAddress, port: Int) {
        remoteAddress = address
        remotePort = port
        Log.d(TAG, "UDP remote set to $address:$port")
    }

    /**
     * Send a PCM frame (ShortArray) to the configured remote endpoint.
     */
    fun send(frame: ShortArray) {
        val sock = socket ?: return
        val addr = remoteAddress ?: return
        if (remotePort == 0) return

        // Header: 4 + 4 + 4 = 12 bytes
        val buf = ByteBuffer.allocate(HEADER_SIZE + frame.size * 2)
            .order(ByteOrder.BIG_ENDIAN)
        buf.putInt(sessionId.hashCode())
        buf.putInt(sequenceNumber++)
        buf.putInt((System.currentTimeMillis() and 0xFFFFFFFFL).toInt())

        // PCM payload in little-endian
        buf.order(ByteOrder.LITTLE_ENDIAN)
        frame.forEach { buf.putShort(it) }

        val bytes = buf.array()
        try {
            sock.send(DatagramPacket(bytes, bytes.size, addr, remotePort))
        } catch (e: Exception) {
            Log.w(TAG, "UDP send error", e)
        }
    }

    /**
     * Blocking receive loop. Calls [onFrame] with each decoded ShortArray.
     * Runs until [close] is called.
     */
    suspend fun receive(onFrame: (ShortArray) -> Unit) = withContext(Dispatchers.IO) {
        val sock = socket ?: return@withContext
        val buf = ByteArray(MAX_PACKET)
        val packet = DatagramPacket(buf, buf.size)

        while (isActive) {
            try {
                sock.receive(packet)
                val payloadLen = packet.length - HEADER_SIZE
                if (payloadLen <= 0) continue
                val payload = ByteBuffer.wrap(packet.data, HEADER_SIZE, payloadLen)
                    .order(ByteOrder.LITTLE_ENDIAN)
                val numSamples = payloadLen / 2
                val frame = ShortArray(numSamples) { payload.short }
                onFrame(frame)
            } catch (e: java.net.SocketTimeoutException) {
                // Normal timeout — loop continues
            } catch (e: java.net.SocketException) {
                if (!sock.isClosed) Log.w(TAG, "UDP receive error", e)
                break
            } catch (e: Exception) {
                Log.w(TAG, "UDP receive error", e)
            }
        }
    }

    fun close() {
        socket?.close()
        socket = null
        Log.d(TAG, "UDP socket closed")
    }
}
