package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = DeepBurgundy,
    onPrimary = PureWhite,
    primaryContainer = BurgundyContainer,
    onPrimaryContainer = OnBurgundyContainer,
    secondary = AntiqueGold,
    onSecondary = PureWhite,
    secondaryContainer = GoldContainer,
    onSecondaryContainer = OnGoldContainer,
    tertiary = MutedGreen,
    onTertiary = PureWhite,
    tertiaryContainer = GreenContainer,
    onTertiaryContainer = Color(0xFF0E3823),
    background = WarmIvory,
    onBackground = DarkCharcoal,
    surface = PureWhite,
    onSurface = DarkCharcoal,
    surfaceVariant = SoftCreamSurface,
    onSurfaceVariant = MutedGrey,
    outline = SoftBeigeBorder,
    outlineVariant = Color(0xFFF0ECE4),
    error = MutedRed,
    onError = PureWhite,
    errorContainer = RedContainer,
    onErrorContainer = Color(0xFF5D1B1B)
)

private val DarkColorScheme = darkColorScheme(
    primary = BurgundySoftLight,
    onPrimary = Color(0xFF3F0C18),
    primaryContainer = DarkBurgundyContainer,
    onPrimaryContainer = Color(0xFFFAD9E0),
    secondary = AntiqueGoldLight,
    onSecondary = Color(0xFF35260A),
    secondaryContainer = Color(0xFF473618),
    onSecondaryContainer = GoldContainer,
    tertiary = Color(0xFF70C999),
    onTertiary = Color(0xFF0B3821),
    tertiaryContainer = Color(0xFF174B30),
    onTertiaryContainer = GreenContainer,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = Color(0xFF33272B),
    error = Color(0xFFE58A8A),
    onError = Color(0xFF4D1515),
    errorContainer = Color(0xFF5C2121),
    onErrorContainer = RedContainer
)

val JewelleryShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun JewelleryDeskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JewelleryTypography,
        shapes = JewelleryShapes,
        content = content
    )
}
