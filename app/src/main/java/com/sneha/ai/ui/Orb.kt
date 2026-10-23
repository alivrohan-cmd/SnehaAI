package com.sneha.ai.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Idle অবস্থায় কোনো অ্যানিমেশন চলে না (ব্যাটারি বাঁচাতে)। */
@Composable
fun Orb(phase: Phase, level: Float, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val color = when (phase) {
        Phase.Idle -> Color(0xFF8B7CFF)
        Phase.Listening -> Color(0xFF4DA3FF)
        Phase.Thinking -> Color(0xFFB9A8FF)
        Phase.Speaking -> Color(0xFFD08CFF)
        Phase.Executing -> Color(0xFF3DDBC0)
    }
    val pulse: Float = if (phase != Phase.Idle && !reduceMotion) {
        val t = rememberInfiniteTransition(label = "orb")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p").value
    } else 0f
    val lv by animateFloatAsState(level, label = "lv")
    Canvas(modifier) {
        val c = center
        val base = size.minDimension * 0.28f
        val r = base * (1f + 0.07f * pulse + 0.35f * lv)
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), center = c, radius = r * 2.2f),
            radius = r * 2.2f, center = c
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White.copy(alpha = 0.9f), color, color.copy(alpha = 0.6f)),
                center = c - Offset(r * 0.25f, r * 0.25f), radius = r * 1.3f
            ),
            radius = r, center = c
        )
    }
}
