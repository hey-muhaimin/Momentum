package com.momentum.browser.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.momentum.browser.ui.theme.MomentumLime
import com.momentum.browser.ui.theme.MomentumViolet

@Composable
fun AtmosphericMountainBackdrop(
    isIncognito: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val glowPrimary = if (isIncognito) MomentumViolet else MomentumLime

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        surfaceColor.copy(alpha = 0.85f),
                        bgColor
                    )
                )
            )

            // Soft horizon aurora glow behind the peaks
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowPrimary.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.72f, h * 0.18f),
                    radius = w * 0.65f
                ),
                radius = w * 0.65f,
                center = Offset(w * 0.72f, h * 0.18f)
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MomentumViolet.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.18f, h * 0.24f),
                    radius = w * 0.55f
                ),
                radius = w * 0.55f,
                center = Offset(w * 0.18f, h * 0.24f)
            )

            // Distant mountain ridge
            val ridgeFar = Path().apply {
                moveTo(0f, h * 0.34f)
                lineTo(w * 0.22f, h * 0.24f)
                lineTo(w * 0.44f, h * 0.30f)
                lineTo(w * 0.70f, h * 0.19f)
                lineTo(w, h * 0.29f)
                lineTo(w, h * 0.55f)
                lineTo(0f, h * 0.55f)
                close()
            }
            drawPath(
                path = ridgeFar,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E2621).copy(alpha = 0.78f),
                        bgColor.copy(alpha = 0.96f)
                    ),
                    startY = h * 0.18f,
                    endY = h * 0.48f
                )
            )

            // Foreground ridge
            val ridgeNear = Path().apply {
                moveTo(0f, h * 0.38f)
                lineTo(w * 0.16f, h * 0.31f)
                lineTo(w * 0.38f, h * 0.36f)
                lineTo(w * 0.64f, h * 0.26f)
                lineTo(w * 0.88f, h * 0.34f)
                lineTo(w, h * 0.32f)
                lineTo(w, h * 0.60f)
                lineTo(0f, h * 0.60f)
                close()
            }
            drawPath(
                path = ridgeNear,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF141816).copy(alpha = 0.92f),
                        bgColor
                    ),
                    startY = h * 0.26f,
                    endY = h * 0.48f
                )
            )
        }
    }
}
