package com.combadge.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable LCARS UI decoration components.
 */

/** Horizontal LCARS decorative bar — full-width with rounded ends */
@Composable
fun LcarsBar(
    modifier: Modifier = Modifier,
    color: Color = LcarsAmber,
    height: Dp = 6.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(color)
    )
}

/** LCARS pill label — colored rounded-rect with text */
@Composable
fun LcarsPill(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = LcarsAmber,
    textColor: Color = DeepSpace
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/** LCARS header bar with title text on right and decorative left bracket */
@Composable
fun LcarsHeader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left rounded tab
        Box(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 26.dp, bottomStart = 0.dp, topEnd = 0.dp, bottomEnd = 0.dp))
                .background(LcarsAmber)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Thin accent bar
        Box(
            modifier = Modifier
                .width(12.dp)
                .fillMaxHeight(0.4f)
                .clip(RoundedCornerShape(50))
                .background(LcarsLavender)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Thinner accent bar
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxHeight(0.6f)
                .clip(RoundedCornerShape(50))
                .background(LcarsPeriwinkle)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Title + subtitle
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = title.uppercase(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Light,
                color = LcarsAmber,
                letterSpacing = 4.sp
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = LcarsTextDim,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/** LCARS footer bar with thin decorative lines */
@Composable
fun LcarsFooter(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(bottomStart = 20.dp))
                .background(LcarsLavender)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(0.5f)
                .clip(RoundedCornerShape(50))
                .background(LcarsAmber)
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(0.5f)
                .clip(RoundedCornerShape(50))
                .background(LcarsAmber)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(bottomEnd = 20.dp))
                .background(LcarsPeriwinkle)
        )
    }
}

/** Left-side LCARS vertical accent strip */
@Composable
fun LcarsLeftStrip(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(6) { i ->
            val color = when (i % 3) {
                0 -> LcarsAmber
                1 -> LcarsLavender
                else -> LcarsPeriwinkle
            }
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.6f))
            )
        }
    }
}
