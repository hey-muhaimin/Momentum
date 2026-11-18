package com.momentum.browser.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LuminousDarkColorScheme = darkColorScheme(
    primary = MomentumLime,
    onPrimary = GraphiteBg,
    primaryContainer = Color(0xFF253214),
    onPrimaryContainer = MomentumLime,
    secondary = MomentumViolet,
    onSecondary = GraphiteBg,
    secondaryContainer = Color(0xFF272244),
    onSecondaryContainer = Color(0xFFD9D2FF),
    tertiary = MomentumEmerald,
    onTertiary = GraphiteBg,
    background = GraphiteBg,
    onBackground = GraphiteTextPrimary,
    surface = GraphiteSurface,
    onSurface = GraphiteTextPrimary,
    surfaceVariant = GraphiteSurfaceElevated,
    onSurfaceVariant = GraphiteTextSecondary,
    outline = GraphiteOutline,
    error = MomentumCoral,
    onError = GraphiteBg
)

private val LuminousLightColorScheme = lightColorScheme(
    primary = LightLimeAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F7C8),
    onPrimaryContainer = Color(0xFF243B05),
    secondary = Color(0xFF6353E8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE9FE),
    onSecondaryContainer = Color(0xFF2C1E78),
    tertiary = Color(0xFF15803D),
    onTertiary = Color.White,
    background = LightGraphiteBg,
    onBackground = LightTextPrimary,
    surface = LightGraphiteSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightGraphiteElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightGraphiteOutline,
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val MomentumShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MomentumTheme(
    darkTheme: Boolean = true,
    amoledBlack: Boolean = true,
    textScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val baseScheme = if (darkTheme) {
        if (amoledBlack) {
            LuminousDarkColorScheme.copy(background = GraphiteBg)
        } else {
            LuminousDarkColorScheme.copy(background = Color(0xFF121514))
        }
    } else {
        LuminousLightColorScheme
    }

    MaterialTheme(
        colorScheme = baseScheme,
        typography = momentumTypography(textScale.coerceIn(0.85f, 1.3f)),
        shapes = MomentumShapes,
        content = content
    )
}
