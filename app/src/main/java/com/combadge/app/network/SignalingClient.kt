package com.combadge.app.network

import android.util.Log
import com.combadge.app.model.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP client for sending hail and close signals to a remote peer.
 *
 * The caller:
 *   1. Opens a UDP socket (to get a random local audio port).
 *   2. Sends a hail that includes their local audio port.
 *   3. Receives an accept with the remote peer's audio port.
 *   4. Points the UDP socket at (peerIP, remoteAudioPort).
 *   5. Keeps the TCP connection open as a liveness check.
 */
class SignalingClient {

    companion object {
        private const val TAG = "SignalingClient"
        private const val CONNECT_TIMEOUT_MS = 5000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    var onRemoteClose: ((String) -> Unit)? = null

    /**
     * Send a hail and wait for accept.
     *
     * Opens a UDP socket internally and includes its local port in the hail.
     *
     * @return [Pair] of (AcceptMessage, openUdpSocket) on success, or null on failure.
     *   The caller must configure the UDP socket's remote endpoint and manage its lifecycle.
     */
    suspend fun sendHail(
        peer: com.combadge.app.model.Peer,
        from: String,
        sessionId: String
    ): Pair<AcceptMessage, UdpAudioSocket>? = withContext(Dispatchers.IO) {
        // Open our receiving UDP socket first so we know the port
        val udpSocket = UdpAudioSocket(sessionId)
        udpSocket.open()

        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(peer.ipAddress, peer.port), CONNECT_TIMEOUT_MS)
            socket = sock

            val w = PrintWriter(sock.getOutputStream(), true)
            writer = w

            val hailMsg = HailMessage(
                sessionId = sessionId,
                from = from,
                callerAudioPort = udpSocket.localPort
            )
            w.println(gson.toJson(hailMsg))
            w.flush()

            // Wait for accept
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val responseLine = withTimeoutOrNull(5000) { reader.readLine() }
            if (responseLine == null) {
                Log.w(TAG, "Timeout waiting for accept")
                udpSocket.close()
                sock.close()
                return@withContext null
            }

            val raw = gson.fromJson(responseLine, RawMessage::class.java)
            if (raw.type != "accept") {
                Log.w(TAG, "Unexpected response: ${raw.type}")
                udpSocket.close()
                sock.close()
                return@withContext null
            }

            val accept = AcceptMessage(sessionId = raw.sessionId, audioPort = raw.audioPort)
            Log.d(TAG, "Got accept: remoteAudioPort=${accept.audioPort}, ourPort=${udpSocket.localPort}")

            // Keep connection alive and listen for remote close
            listenForClose(sock, accept.sessionId)
            Pair(accept, udpSocket)

        } catch (e: Exception) {
            Log.e(TAG, "Hail failed", e)
            udpSocket.close()
            null
        }
    }

    /** Send a close signal on the open TCP connection. */
    fun sendClose(sessionId: String) {
        try {
            writer?.println(gson.toJson(CloseMessage(sessionId = sessionId)))
            writer?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "sendClose failed", e)
        } finally {
            disconnect()
        }
    }

    private fun listenForClose(sock: Socket, sessionId: String) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                while (isActive && !sock.isClosed) {
                    val line = reader.readLine() ?: break
                    val raw = try { gson.fromJson(line, RawMessage::class.java) } catch (e: Exception) { continue }
                    if (raw.type == "close") {
                        Log.d(TAG, "Remote close, session=${raw.sessionId}")
                        onRemoteClose?.invoke(raw.sessionId)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TCP lost: ${e.message}")
                onRemoteClose?.invoke(sessionId)
            }
        }
    }

    fun disconnect() {
        try { socket?.close() } catch (e: Exception) { }
        socket = null
        writer = null
    }

    fun release() {
        scope.cancel()
        disconnect()
    }
}
