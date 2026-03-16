package com.combadge.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.combadge.app.model.CombadgeState
import com.combadge.app.ui.theme.*
import kotlinx.coroutines.delay

/**
 * LCARS-styled status text area displayed below the combadge.
 */
@Composable
fun StatusDisplay(
    state: CombadgeState,
    peerCount: Int,
    modifier: Modifier = Modifier
) {
    val (text, color) = statusTextAndColor(state, peerCount)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DeepSpaceDark)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = color,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun statusTextAndColor(
    state: CombadgeState,
    peerCount: Int
): Pair<String, androidx.compose.ui.graphics.Color> {
    return when (state) {
        is CombadgeState.Idle -> {
            val crew = when (peerCount) {
                0 -> "No crew members on sensors"
                1 -> "1 crew member on sensors"
                else -> "$peerCount crew members on sensors"
            }
            "Standing by — $crew" to LcarsTextDim
        }

        is CombadgeState.Listening -> {
            val text = if (state.partialTranscript.isNotBlank()) {
                "\"${state.partialTranscript}\""
            } else {
                "Listening…"
            }
            text to LcarsAmber
        }

        is CombadgeState.Hailing ->
            "Hailing ${state.targetName}…" to LcarsAmber

        is CombadgeState.ChannelOpen -> {
            val elapsed = remember { mutableLongStateOf(0L) }
            LaunchedEffect(state.startTime) {
                while (true) {
                    elapsed.longValue = System.currentTimeMillis() - state.startTime
                    delay(1000)
                }
            }
            val seconds = elapsed.longValue / 1000
            val mins = seconds / 60
            val secs = seconds % 60
            val timer = "%d:%02d".format(mins, secs)
            "Channel open — ${state.peer.name}   $timer" to LcarsTeal
        }

        is CombadgeState.IncomingHail ->
            "Incoming hail — ${state.peer.name}" to LcarsTeal

        is CombadgeState.Error ->
            state.message to LcarsAlert

        is CombadgeState.Disambiguation ->
            "Multiple matches: ${state.candidates.joinToString(", ") { it.name }}" to LcarsLavender

        is CombadgeState.NotRegistered ->
            "Crew registration required" to LcarsTextDim
    }
}
