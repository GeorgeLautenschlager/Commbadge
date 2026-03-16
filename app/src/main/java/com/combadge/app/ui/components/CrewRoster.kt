package com.combadge.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combadge.app.model.Peer
import com.combadge.app.ui.theme.*

/**
 * Bottom row of LCARS pill labels showing each discovered peer.
 * Informational only — tapping not required (combadge uses voice).
 */
@Composable
fun CrewRoster(
    peers: List<Peer>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (peers.isEmpty()) {
            LcarsPill(
                label = "No crew on sensors",
                color = LcarsTextDim.copy(alpha = 0.3f),
                textColor = LcarsTextDim,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(peers, key = { it.name }) { peer ->
                    val color = when {
                        peers.indexOf(peer) % 3 == 0 -> LcarsAmber
                        peers.indexOf(peer) % 3 == 1 -> LcarsLavender
                        else -> LcarsPeriwinkle
                    }
                    LcarsPill(
                        label = peer.name,
                        color = color.copy(alpha = 0.8f),
                        textColor = DeepSpace
                    )
                }
            }
        }
    }
}
