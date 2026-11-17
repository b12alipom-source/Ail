package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val BayanDarkColorScheme = darkColorScheme(
    primary = SilverPure,
    onPrimary = MidnightVoid,
    primaryContainer = MidnightSurfaceHigh,
    onPrimaryContainer = SilverPure,
    secondary = IceSapphire,
    onSecondary = MidnightVoid,
    secondaryContainer = MidnightSurface,
    onSecondaryContainer = IceSapphireGlow,
    tertiary = SilverMetallic,
    onTertiary = MidnightVoid,
    background = MidnightNavy,
    onBackground = SilverPure,
    surface = MidnightSurface,
    onSurface = SilverPure,
    surfaceVariant = MidnightSurfaceHigh,
    onSurfaceVariant = SilverMuted,
    outline = MidnightBorder,
    outlineVariant = MidnightBorderSubtle,
    error = CoralError,
    onError = MidnightVoid
)

val BayanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BayanTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = BayanDarkColorScheme,
            typography = BayanTypography,
            shapes = BayanShapes,
            content = content
        )
    }
}
