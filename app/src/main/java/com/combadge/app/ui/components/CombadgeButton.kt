package com.combadge.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.combadge.app.R
import com.combadge.app.model.CombadgeState
import com.combadge.app.ui.theme.*

/**
 * The main combadge button. Renders the vector drawable and applies
 * animated glow overlays based on the current [CombadgeState].
 */
@Composable
fun CombadgeButton(
    state: CombadgeState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp
) {
    val glowColor = glowColorForState(state)
    val glowAlpha = animatedGlowAlpha(state)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap
            )
            .drawBehind {
                // Draw glow halo behind the badge
                if (glowAlpha > 0f) {
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            asFrameworkPaint().apply {
                                isAntiAlias = true
                                color = android.graphics.Color.TRANSPARENT
                                setShadowLayer(
                                    size.width.value * 0.25f,
                                    0f, 0f,
                                    android.graphics.Color.argb(
                                        (glowAlpha * 200).toInt(),
                                        (glowColor.red * 255).toInt(),
                                        (glowColor.green * 255).toInt(),
                                        (glowColor.blue * 255).toInt()
                                    )
                                )
                            }
                        }
                        canvas.drawCircle(
                            center = this.center,
                            radius = this.size.minDimension * 0.4f,
                            paint = paint
                        )
                    }
                }
            }
    ) {
        Image(
            painter = painterResource(id = R.drawable.combadge_vector),
            contentDescription = "Combadge",
            modifier = Modifier.size(size)
        )
    }
}

@Composable
private fun glowColorForState(state: CombadgeState): Color = when (state) {
    is CombadgeState.Listening  -> LcarsAmber
    is CombadgeState.Hailing    -> LcarsAmber
    is CombadgeState.ChannelOpen -> LcarsAmber
    is CombadgeState.IncomingHail -> LcarsTeal
    is CombadgeState.Error      -> LcarsAlert
    else -> Color.Transparent
}

@Composable
private fun animatedGlowAlpha(state: CombadgeState): Float {
    return when (state) {
        is CombadgeState.Listening, is CombadgeState.IncomingHail -> {
            // Pulsing
            val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glow_alpha"
            )
            alpha
        }
        is CombadgeState.ChannelOpen, is CombadgeState.Hailing -> 0.75f
        is CombadgeState.Error -> {
            val infiniteTransition = rememberInfiniteTransition(label = "error_flash")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "error_alpha"
            )
            alpha
        }
        else -> 0f
    }
}
