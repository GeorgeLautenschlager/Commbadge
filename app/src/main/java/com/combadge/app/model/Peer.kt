package com.combadge.app.model

data class Peer(
    val name: String,
    val aliases: List<String> = emptyList(),
    val ipAddress: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis()
) {
    /** All names this peer responds to (primary + aliases), lowercase for matching */
    val allNames: List<String>
        get() = (listOf(name) + aliases).map { it.trim().lowercase() }
}
