package com.combadge.app.network

import android.util.Log
import com.combadge.app.model.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * TCP server that listens for incoming hail/close signal messages.
 *
 * When a hail arrives:
 *   1. Opens a UDP socket (to get a local audio port).
 *   2. Sends an accept back to the caller with that port.
 *   3. Fires [onHailReceived] with the hail metadata and the open UdpAudioSocket.
 *      The caller is responsible for calling udpSocket.close() when the channel ends.
 *
 * The TCP connection stays open as a liveness check; if it drops, the channel closes.
 */
class SignalingServer {

    companion object {
        private const val TAG = "SignalingServer"
        private const val BACKLOG = 5
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()

    private var serverSocket: ServerSocket? = null
    private var activeConnection: Socket? = null
    private var activeWriter: PrintWriter? = null

    /** Called on hail: (hailMessage, openUdpSocket, callerAudioPort) */
    var onHailReceived: ((HailMessage, UdpAudioSocket, Int) -> Unit)? = null
    var onCloseReceived: ((CloseMessage) -> Unit)? = null

    val port: Int get() = serverSocket?.localPort ?: 0

    fun start() {
        serverSocket = ServerSocket(0, BACKLOG)  // OS picks a free port
        Log.d(TAG, "Signaling server listening on port $port")
        acceptLoop()
    }

    fun stop() {
        scope.cancel()
        try { activeConnection?.close() } catch (e: Exception) { }
        try { serverSocket?.close() } catch (e: Exception) { }
        serverSocket = null
        activeConnection = null
        activeWriter = null
    }

    /** Send a close message on the currently active TCP connection. */
    fun sendClose(sessionId: String) {
        val json = gson.toJson(CloseMessage(sessionId = sessionId))
        try {
            activeWriter?.println(json)
            activeWriter?.flush()
        } catch (e: Exception) {
            Log.w(TAG, "sendClose failed", e)
        }
    }

    private fun acceptLoop() {
        scope.launch {
            val server = serverSocket ?: return@launch
            while (isActive && !server.isClosed) {
                try {
                    val socket = server.accept()
                    Log.d(TAG, "Incoming TCP from ${socket.inetAddress.hostAddress}")
                    handleConnection(socket)
                } catch (e: java.net.SocketException) {
                    if (!server.isClosed) Log.w(TAG, "Accept error", e)
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Accept error", e)
                }
            }
        }
    }

    private fun handleConnection(socket: Socket) {
        scope.launch {
            activeConnection?.close()
            activeConnection = socket
            val writer = PrintWriter(socket.getOutputStream(), true)
            activeWriter = writer
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            try {
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    processMessage(line, socket, writer)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Connection error: ${e.message}")
            } finally {
                if (activeConnection === socket) {
                    activeConnection = null
                    activeWriter = null
                }
                try { socket.close() } catch (e: Exception) { }
            }
        }
    }

    private fun processMessage(json: String, socket: Socket, writer: PrintWriter) {
        try {
            val raw = gson.fromJson(json, RawMessage::class.java)
            when (raw.type) {
                "hail" -> {
                    val msg = HailMessage(
                        sessionId = raw.sessionId,
                        from = raw.from ?: "Unknown",
                        callerAudioPort = raw.callerAudioPort,
                        phrase = raw.phrase
                    )

                    // Open a UDP socket to receive the caller's audio on
                    val udpSocket = UdpAudioSocket(raw.sessionId)
                    udpSocket.open()

                    // Also set the remote end so we can send audio to the caller
                    if (raw.callerAudioPort > 0) {
                        udpSocket.setRemote(socket.inetAddress, raw.callerAudioPort)
                    }

                    // Reply with our audio port
                    val accept = AcceptMessage(sessionId = raw.sessionId, audioPort = udpSocket.localPort)
                    writer.println(gson.toJson(accept))
                    writer.flush()

                    Log.d(TAG, "Hail from ${msg.from}, our UDP port=${udpSocket.localPort}, their port=${msg.callerAudioPort}")
                    onHailReceived?.invoke(msg, udpSocket, raw.callerAudioPort)
                }
                "close" -> {
                    val msg = CloseMessage(sessionId = raw.sessionId)
                    onCloseReceived?.invoke(msg)
                }
                else -> Log.w(TAG, "Unknown message type: ${raw.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse: $json", e)
        }
    }
}
