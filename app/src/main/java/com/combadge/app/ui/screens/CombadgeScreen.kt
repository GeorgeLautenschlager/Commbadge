package com.combadge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.combadge.app.model.CombadgeState
import com.combadge.app.model.Peer
import com.combadge.app.ui.components.*
import com.combadge.app.ui.theme.*
import com.combadge.app.util.StardateCalculator

/**
 * Main combadge screen. Full-screen LCARS-styled UI centered on the combadge graphic.
 *
 * Long-press navigates to Settings.
 */
@Composable
fun CombadgeScreen(
    state: CombadgeState,
    peers: List<Peer>,
    myName: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDisambiguationSelect: (Peer) -> Unit,
    modifier: Modifier = Modifier
) {
    val stardate = remember { StardateCalculator.current() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // LCARS Header
            LcarsHeader(
                title = "COMBADGE",
                subtitle = "STARDATE $stardate",
                modifier = Modifier.fillMaxWidth()
            )

            LcarsBar(color = LcarsAmber, height = 2.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Wi-Fi warning if shown via state
            if (state is CombadgeState.Error && state.message.contains("Wi-Fi", ignoreCase = true)) {
                WifiWarningBanner()
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // Combadge — centered, tappable + long-pressable
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(260.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onTap() },
                            onLongPress = { onLongPress() }
                        )
                    }
            ) {
                CombadgeButton(
                    state = state,
                    onTap = onTap,
                    size = 240.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status text
            StatusDisplay(
                state = state,
                peerCount = peers.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            // Disambiguation picker (when multiple peers match)
            if (state is CombadgeState.Disambiguation) {
                Spacer(modifier = Modifier.height(12.dp))
                DisambiguationPicker(
                    candidates = state.candidates,
                    onSelect = onDisambiguationSelect
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Crew roster pills
            CrewRoster(
                peers = peers,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )

            // LCARS Footer
            LcarsBar(color = LcarsLavender, height = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
            LcarsFooter(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(4.dp))

            // My crew name label
            Text(
                text = if (myName.isNotBlank()) "CREW: ${myName.uppercase()}" else "",
                fontSize = 10.sp,
                color = LcarsTextDim,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DisambiguationPicker(
    candidates: List<Peer>,
    onSelect: (Peer) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSpaceDark, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Multiple matches — select:",
            fontSize = 11.sp,
            color = LcarsLavender,
            letterSpacing = 1.sp
        )
        candidates.forEach { peer ->
            TextButton(
                onClick = { onSelect(peer) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = LcarsAmber)
            ) {
                Text(
                    peer.name.uppercase(),
                    fontSize = 14.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun WifiWarningBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LcarsAlert.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "⚠  Combadge requires local network. Connect to Wi-Fi.",
            fontSize = 12.sp,
            color = LcarsAlert,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
