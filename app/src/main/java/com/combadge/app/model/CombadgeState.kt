package com.combadge.app.model

sealed class CombadgeState {
    /** App is idle, showing crew roster and standing-by status */
    data object Idle : CombadgeState()

    /** Actively listening for speech input */
    data class Listening(val partialTranscript: String = "") : CombadgeState()

    /** Hail has been sent; waiting for auto-accept from target */
    data class Hailing(val targetName: String) : CombadgeState()

    /** Voice channel is open with a remote peer */
    data class ChannelOpen(
        val peer: Peer,
        val sessionId: String,
        val startTime: Long = System.currentTimeMillis()
    ) : CombadgeState()

    /** Incoming hail from a remote peer; auto-accepts after 1 second */
    data class IncomingHail(
        val peer: Peer,
        val sessionId: String,
        val audioPort: Int
    ) : CombadgeState()

    /** Error state (unknown name, network failure, etc.) */
    data class Error(val message: String) : CombadgeState()

    /** Multiple peers matched the spoken name — user must disambiguate */
    data class Disambiguation(
        val spokenName: String,
        val candidates: List<Peer>
    ) : CombadgeState()

    /** App not yet configured — show registration screen */
    data object NotRegistered : CombadgeState()
}
