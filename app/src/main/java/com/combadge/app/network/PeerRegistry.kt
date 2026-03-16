package com.combadge.app.network

import android.util.Log
import com.combadge.app.model.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live roster of discovered combadge peers on the local network.
 *
 * Thread-safe; backed by a StateFlow for reactive UI updates.
 */
class PeerRegistry {

    companion object {
        private const val TAG = "PeerRegistry"
        private const val STALE_THRESHOLD_MS = 90_000L  // 90 seconds
    }

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    @Synchronized
    fun addOrUpdate(peer: Peer) {
        val current = _peers.value.toMutableList()
        val idx = current.indexOfFirst { it.name.equals(peer.name, ignoreCase = true) }
        if (idx >= 0) {
            current[idx] = peer
        } else {
            current.add(peer)
            Log.d(TAG, "Peer added: ${peer.name} @ ${peer.ipAddress}:${peer.port}")
        }
        _peers.value = current
    }

    @Synchronized
    fun remove(name: String) {
        val updated = _peers.value.filter { !it.name.equals(name, ignoreCase = true) }
        if (updated.size != _peers.value.size) {
            Log.d(TAG, "Peer removed: $name")
            _peers.value = updated
        }
    }

    @Synchronized
    fun removeStale() {
        val threshold = System.currentTimeMillis() - STALE_THRESHOLD_MS
        val updated = _peers.value.filter { it.lastSeen >= threshold }
        if (updated.size != _peers.value.size) {
            Log.d(TAG, "Removed ${_peers.value.size - updated.size} stale peer(s)")
            _peers.value = updated
        }
    }

    fun getAll(): List<Peer> = _peers.value

    fun findByName(query: String): List<Peer> {
        val q = query.trim().lowercase()
        return _peers.value.filter { peer ->
            peer.allNames.any { it == q }
        }
    }
}
