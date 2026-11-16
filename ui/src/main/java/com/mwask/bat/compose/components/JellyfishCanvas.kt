package com.mwask.bat.compose.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import com.mwask.bat.compose.theme.EncoreColors
import kotlin.math.PI
import kotlin.math.sin

/**
 *
 * Renders 3 dynamic, undulating Bézier spline wave layers (Top, Mid, Bottom) that smoothly oscillate
 */
@Composable
fun JellyfishCanvas(
    dominantColor: Color,
    modifier: Modifier = Modifier,
    accentColor: Color = dominantColor.copy(alpha = 0.7f),
    deepBackgroundColor: Color = EncoreColors.Background,
    speedFactor: Float = 1.0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jellyfishMotion")

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = (14000 / speedFactor).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "jellyfishPhase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(deepBackgroundColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            drawSplineWave(
                width = width,
                height = height,
                baseY = height * 0.42f,
                amplitude = height * 0.08f,
                frequency = 1.2f,
                phase = phase * 0.7f,
                fillColor = dominantColor.copy(alpha = 0.35f)
            )

            drawSplineWave(
                width = width,
                height = height,
                baseY = height * 0.32f,
                amplitude = height * 0.06f,
                frequency = 1.8f,
                phase = phase * 1.1f + 1.5f,
                fillColor = accentColor.copy(alpha = 0.50f)
            )

            drawSplineWave(
                width = width,
                height = height,
                baseY = height * 0.22f,
                amplitude = height * 0.05f,
                frequency = 2.2f,
                phase = phase * 1.4f + 3.0f,
                fillColor = dominantColor.copy(alpha = 0.65f)
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.40f to dominantColor.copy(alpha = 0.20f),
                        0.75f to deepBackgroundColor.copy(alpha = 0.85f),
                        1.0f to deepBackgroundColor
                    )
                ),
                size = size
            )
        }
    }
}

/**
 * Helper drawing a smooth cubic Bézier spline wave across the screen.
 * Mirrors `JellyfishView.smali` Path.cubicTo implementation.
 */
private fun DrawScope.drawSplineWave(
    width: Float,
    height: Float,
    baseY: Float,
    amplitude: Float,
    frequency: Float,
    phase: Float,
    fillColor: Color
) {
    val path = Path()
    val segments = 4
    val segmentWidth = width / segments

    val startY = baseY + amplitude * sin(phase)
    path.moveTo(0f, startY)

    for (i in 0 until segments) {
        val x0 = i * segmentWidth
        val x1 = (i + 1) * segmentWidth
        val midX = (x0 + x1) / 2f

        val t0 = (i.toFloat() / segments) * frequency * (2 * PI).toFloat() + phase
        val t1 = ((i + 1).toFloat() / segments) * frequency * (2 * PI).toFloat() + phase
        val y1 = baseY + amplitude * sin(t1)

        val cp1x = x0 + segmentWidth * 0.5f
        val cp1y = baseY + amplitude * sin(t0 + 0.3f)
        val cp2x = midX
        val cp2y = baseY + amplitude * sin(t1 - 0.3f)

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, x1, y1)
    }

    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()

    drawPath(
        path = path,
        color = fillColor,
        style = Fill
    )
}
