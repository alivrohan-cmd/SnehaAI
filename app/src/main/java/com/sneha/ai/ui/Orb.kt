package com.sneha.ai.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Orb(phase: Phase, level: Float, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val primaryTarget = when (phase) {
        Phase.Idle -> Color(0xFF8B6BFF)
        Phase.Listening -> Color(0xFF00D2FF)
        Phase.Thinking -> Color(0xFFC084FC)
        Phase.Speaking -> Color(0xFFF472B6)
        Phase.Executing -> Color(0xFF10B981)
    }
    val secondaryTarget = when (phase) {
        Phase.Idle -> Color(0xFF4F46E5)
        Phase.Listening -> Color(0xFF3B82F6)
        Phase.Thinking -> Color(0xFFEC4899)
        Phase.Speaking -> Color(0xFF8B5CF6)
        Phase.Executing -> Color(0xFF06B6D4)
    }

    val coreColor by animateColorAsState(primaryTarget, tween(500), label = "coreColor")
    val accentColor by animateColorAsState(secondaryTarget, tween(500), label = "accentColor")
    val lv by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "voiceLevel")

    val active = phase != Phase.Idle && !reduceMotion
    val pulse: Float
    val angle: Float
    if (active) {
        val inf = rememberInfiniteTransition(label = "orbInfinite")
        pulse = inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse"
        ).value
        angle = inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(7200, easing = LinearEasing), RepeatMode.Restart),
            label = "angle"
        ).value
    } else {
        pulse = 0f
        angle = 25f
    }

    Canvas(modifier) {
        val c = center
        val minDim = size.minDimension
        val baseRadius = minDim * 0.25f
        val dynamicRadius = baseRadius * (1f + 0.08f * pulse + 0.30f * lv)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColor.copy(alpha = 0.32f + 0.12f * pulse),
                    accentColor.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = c,
                radius = dynamicRadius * 2.45f
            ),
            radius = dynamicRadius * 2.45f,
            center = c
        )

        if (phase == Phase.Listening || phase == Phase.Speaking) {
            val waveRadius = dynamicRadius * (1.35f + 0.28f * lv + 0.10f * pulse)
            drawCircle(
                color = coreColor.copy(alpha = (0.35f - 0.15f * pulse).coerceIn(0.08f, 0.45f)),
                radius = waveRadius,
                center = c,
                style = Stroke(width = minDim * 0.012f)
            )
        }

        val orbitRadius1 = dynamicRadius * 1.32f
        val arcSize1 = Size(orbitRadius1 * 2f, orbitRadius1 * 2f)
        val topLeft1 = Offset(c.x - orbitRadius1, c.y - orbitRadius1)
        rotate(degrees = angle, pivot = c) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        coreColor.copy(alpha = 0.70f),
                        accentColor.copy(alpha = 0.85f),
                        Color.Transparent
                    ),
                    center = c
                ),
                startAngle = 15f,
                sweepAngle = 135f,
                useCenter = false,
                topLeft = topLeft1,
                size = arcSize1,
                style = Stroke(width = minDim * 0.016f, cap = StrokeCap.Round)
            )
            drawArc(
                color = coreColor.copy(alpha = 0.45f),
                startAngle = 195f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft1,
                size = arcSize1,
                style = Stroke(width = minDim * 0.010f, cap = StrokeCap.Round)
            )
        }

        val orbitRadius2 = dynamicRadius * 1.15f
        val arcSize2 = Size(orbitRadius2 * 2f, orbitRadius2 * 2f)
        val topLeft2 = Offset(c.x - orbitRadius2, c.y - orbitRadius2)
        rotate(degrees = -angle * 1.4f, pivot = c) {
            drawArc(
                color = accentColor.copy(alpha = if (phase == Phase.Idle) 0.28f else 0.65f),
                startAngle = 60f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = topLeft2,
                size = arcSize2,
                style = Stroke(width = minDim * 0.011f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color.White.copy(alpha = if (phase == Phase.Idle) 0.18f else 0.45f),
                startAngle = 240f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = topLeft2,
                size = arcSize2,
                style = Stroke(width = minDim * 0.011f, cap = StrokeCap.Round)
            )
        }

        val rad = Math.toRadians(angle.toDouble())
        val dotOffset = Offset(
            x = c.x + (orbitRadius1 * cos(rad)).toFloat(),
            y = c.y + (orbitRadius1 * sin(rad)).toFloat()
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = minDim * 0.018f,
            center = dotOffset
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    coreColor,
                    accentColor.copy(alpha = 0.88f),
                    coreColor.copy(alpha = 0.35f)
                ),
                center = c - Offset(dynamicRadius * 0.28f, dynamicRadius * 0.28f),
                radius = dynamicRadius * 1.35f
            ),
            radius = dynamicRadius,
            center = c
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = c - Offset(dynamicRadius * 0.35f, dynamicRadius * 0.35f),
                radius = dynamicRadius * 0.55f
            ),
            radius = dynamicRadius * 0.55f,
            center = c - Offset(dynamicRadius * 0.35f, dynamicRadius * 0.35f)
        )
    }
}
