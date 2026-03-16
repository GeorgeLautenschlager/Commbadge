package com.combadge.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.combadge.app.audio.SoundManager
import com.combadge.app.audio.VoiceStreamManager
import com.combadge.app.model.*
import com.combadge.app.network.*
import com.combadge.app.speech.CommandParser
import com.combadge.app.speech.SpeechManager
import com.combadge.app.util.PrefsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import java.util.UUID

class CombadgeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CombadgeViewModel"
        private const val AUTO_ACCEPT_DELAY_MS = 1000L
        private const val ERROR_DISPLAY_MS = 3000L
    }

    private val context: Context get() = getApplication()

    val prefs = PrefsStore(context)

    private val _state = MutableStateFlow<CombadgeState>(CombadgeState.Idle)
    val state: StateFlow<CombadgeState> = _state.asStateFlow()

    private val registry = PeerRegistry()
    val peers: StateFlow<List<Peer>> = registry.peers

    private var myName: String = ""
    private var myAliases: List<String> = emptyList()
    private var autoAccept: Boolean = true
    private var hapticEnabled: Boolean = true

    private val signalingServer = SignalingServer()
    private val signalingClient = SignalingClient()
    private var nsdController: NsdController? = null

    val soundManager = SoundManager(context)
    private val voiceManager = VoiceStreamManager()

    private var speechManager: SpeechManager? = null

    @Volatile private var activeSessionId: String? = null
    private var activeUdpSocket: UdpAudioSocket? = null

    init {
        observePreferences()
        setupSignalingServer()
    }

    // ------------------------------------------------------------------ //
    // Preferences
    // ------------------------------------------------------------------ //

    private fun observePreferences() {
        viewModelScope.launch {
            prefs.crewName.collect { name ->
                if (name == null) {
                    _state.value = CombadgeState.NotRegistered
                } else if (name.isNotBlank()) {
                    myName = name
                    restartNsd()
                    if (_state.value is CombadgeState.NotRegistered) {
                        _state.value = CombadgeState.Idle
                    }
                }
            }
        }
        viewModelScope.launch { prefs.aliases.collect { myAliases = it } }
        viewModelScope.launch { prefs.autoAccept.collect { autoAccept = it } }
        viewModelScope.launch { prefs.hapticEnabled.collect { hapticEnabled = it } }
        viewModelScope.launch { prefs.chirpVolume.collect { soundManager.setVolume(it) } }
    }

    suspend fun saveCrewName(name: String) = prefs.setCrewName(name)

    suspend fun saveSettings(
        name: String, aliases: List<String>,
        volume: Float, haptic: Boolean, autoAcc: Boolean
    ) {
        prefs.setCrewName(name)
        prefs.setAliases(aliases)
        prefs.setChirpVolume(volume)
        prefs.setHapticEnabled(haptic)
        prefs.setAutoAccept(autoAcc)
    }

    // ------------------------------------------------------------------ //
    // NSD
    // ------------------------------------------------------------------ //

    private fun restartNsd() {
        if (myName.isBlank()) return
        nsdController?.stop()
        nsdController = NsdController(context, registry).also {
            it.start(myName, signalingServer.port, myAliases)
        }
        Log.d(TAG, "NSD started as '$myName' on port ${signalingServer.port}")
    }

    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    // ------------------------------------------------------------------ //
    // Signaling server (incoming hails)
    // ------------------------------------------------------------------ //

    private fun setupSignalingServer() {
        signalingServer.start()

        signalingServer.onHailReceived = { hailMsg, udpSocket, callerAudioPort ->
            Log.d(TAG, "Incoming hail from ${hailMsg.from}")
            val peer = registry.getAll()
                .find { it.name.equals(hailMsg.from, ignoreCase = true) }
                ?: Peer(name = hailMsg.from, ipAddress = "", port = 0)

            viewModelScope.launch(Dispatchers.Main) {
                // Cancel any existing session
                tearDownActiveChannel(silent = true)

                activeSessionId = hailMsg.sessionId
                activeUdpSocket = udpSocket

                soundManager.playIncomingHail()
                vibrate(longArrayOf(0, 50, 100, 50))

                _state.value = CombadgeState.IncomingHail(
                    peer = peer,
                    sessionId = hailMsg.sessionId,
                    audioPort = udpSocket.localPort
                )

                if (autoAccept) {
                    delay(AUTO_ACCEPT_DELAY_MS)
                    val cur = _state.value
                    if (cur is CombadgeState.IncomingHail && cur.sessionId == hailMsg.sessionId) {
                        openChannel(peer, hailMsg.sessionId)
                    }
                }
            }
        }

        signalingServer.onCloseReceived = { closeMsg ->
            viewModelScope.launch(Dispatchers.Main) {
                if (activeSessionId == closeMsg.sessionId) {
                    closeChannel(fromRemote = true)
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Combadge tap
    // ------------------------------------------------------------------ //

    fun onCombadgeTap() {
        when (val current = _state.value) {
            is CombadgeState.Idle           -> startListening()
            is CombadgeState.Listening      -> stopListeningAndCancel()
            is CombadgeState.ChannelOpen    -> closeChannel(fromRemote = false)
            is CombadgeState.Error          -> _state.value = CombadgeState.Idle
            is CombadgeState.IncomingHail   -> openChannel(current.peer, current.sessionId)
            is CombadgeState.Disambiguation -> _state.value = CombadgeState.Idle
            else -> { /* ignore */ }
        }
    }

    fun onDisambiguationSelect(peer: Peer) {
        if (_state.value !is CombadgeState.Disambiguation) return
        _state.value = CombadgeState.Idle
        sendHail(peer)
    }

    // ------------------------------------------------------------------ //
    // Speech
    // ------------------------------------------------------------------ //

    fun attachSpeechManager(manager: SpeechManager) {
        speechManager = manager
        viewModelScope.launch {
            manager.events.collect { handleSpeechEvent(it) }
        }
    }

    private fun startListening() {
        if (!isOnWifi()) {
            showError("Combadge requires local network. Connect to Wi-Fi.")
            return
        }
        soundManager.playActivation()
        vibrate(longArrayOf(0, 30))
        _state.value = CombadgeState.Listening()
        speechManager?.startListening()
    }

    private fun stopListeningAndCancel() {
        speechManager?.stopListening()
        soundManager.playDeactivation()
        _state.value = CombadgeState.Idle
    }

    private fun handleSpeechEvent(event: SpeechManager.SpeechEvent) {
        when (event) {
            is SpeechManager.SpeechEvent.PartialResult -> {
                if (_state.value is CombadgeState.Listening)
                    _state.value = CombadgeState.Listening(event.text)
            }
            is SpeechManager.SpeechEvent.FinalResult -> processSpeechResult(event.text)
            is SpeechManager.SpeechEvent.EndOfSpeech -> { /* result follows */ }
            is SpeechManager.SpeechEvent.Error -> {
                if (_state.value !is CombadgeState.Listening) return
                soundManager.playError()
                val noSpeech = event.code == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                               event.code == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (noSpeech) _state.value = CombadgeState.Idle
                else showError("Speech error: ${event.message}")
            }
        }
    }

    private fun processSpeechResult(text: String) {
        val targetName = CommandParser.extractTarget(text) ?: run {
            showError("Unable to understand command")
            return
        }
        val matches = CommandParser.matchPeers(targetName, registry.getAll())
        when {
            matches.isEmpty() -> {
                soundManager.playError()
                showError("Unable to locate $targetName. Not on sensors.")
            }
            matches.size == 1 -> sendHail(matches[0])
            else -> {
                soundManager.playDisambiguation()
                _state.value = CombadgeState.Disambiguation(targetName, matches)
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Outgoing hail
    // ------------------------------------------------------------------ //

    private fun sendHail(peer: Peer) {
        if (myName.isBlank()) return
        val sessionId = UUID.randomUUID().toString()

        _state.value = CombadgeState.Hailing(peer.name)
        soundManager.playActivation()

        viewModelScope.launch(Dispatchers.IO) {
            val result = signalingClient.sendHail(
                peer = peer,
                from = myName,
                sessionId = sessionId
            )

            withContext(Dispatchers.Main) {
                if (result == null) {
                    showError("Unable to reach ${peer.name}. Not responding.")
                    return@withContext
                }

                val (accept, udpSocket) = result
                activeSessionId = sessionId
                activeUdpSocket = udpSocket

                // Point our UDP socket at the remote peer's audio port
                try {
                    udpSocket.setRemote(InetAddress.getByName(peer.ipAddress), accept.audioPort)
                } catch (e: Exception) {
                    Log.e(TAG, "UDP remote setup failed", e)
                    udpSocket.close()
                    activeUdpSocket = null
                    showError("Network error connecting to ${peer.name}")
                    return@withContext
                }

                // Wire up remote-close callback
                signalingClient.onRemoteClose = { closedSession ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (activeSessionId == closedSession) closeChannel(fromRemote = true)
                    }
                }

                openChannel(peer, sessionId)
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Channel open / close
    // ------------------------------------------------------------------ //

    private fun openChannel(peer: Peer, sessionId: String) {
        val udp = activeUdpSocket ?: run {
            Log.w(TAG, "openChannel: no UDP socket")
            return
        }
        soundManager.playActivation()
        vibrate(longArrayOf(0, 30))
        _state.value = CombadgeState.ChannelOpen(peer = peer, sessionId = sessionId)
        voiceManager.start(udp)
        Log.d(TAG, "Voice channel open with ${peer.name}")
    }

    fun closeChannel(fromRemote: Boolean = false) {
        val session = activeSessionId ?: return
        tearDownActiveChannel(silent = false)
        if (!fromRemote) {
            signalingClient.sendClose(session)
            signalingServer.sendClose(session)
        }
    }

    private fun tearDownActiveChannel(silent: Boolean) {
        val session = activeSessionId ?: return
        activeSessionId = null
        voiceManager.stop()
        activeUdpSocket?.close()
        activeUdpSocket = null
        if (!silent) {
            soundManager.playDeactivation()
            vibrate(longArrayOf(0, 30))
        }
        _state.value = CombadgeState.Idle
        Log.d(TAG, "Channel torn down, session=$session")
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    private fun showError(message: String) {
        _state.value = CombadgeState.Error(message)
        viewModelScope.launch {
            delay(ERROR_DISPLAY_MS)
            if (_state.value is CombadgeState.Error) _state.value = CombadgeState.Idle
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(pattern: LongArray) {
        if (!hapticEnabled) return
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration error", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager?.destroy()
        voiceManager.release()
        soundManager.release()
        signalingServer.stop()
        signalingClient.release()
        nsdController?.stop()
        activeUdpSocket?.close()
    }
}
