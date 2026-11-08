package com.akib.mycompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.akib.mycompanion.R

val CompanionRed = Color(0xFFFF3B3B)

private val Ink = Color(0xFF0B0B0B)
private val Paper = Color(0xFFF7F7F7)
private val DarkSurface = Color(0xFF1A1A1A)
private val LightSurface = Color(0xFFEDEDED)

// Every role is neutral so wallpaper colours never leak into feature modules or dialogs.
internal val CompanionLightColors = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    primaryContainer = LightSurface, onPrimaryContainer = Ink,
    inversePrimary = Color.White,
    secondary = Color(0xFF616161), onSecondary = Color.White,
    secondaryContainer = LightSurface, onSecondaryContainer = Ink,
    tertiary = Ink, onTertiary = Color.White,
    tertiaryContainer = LightSurface, onTertiaryContainer = Ink,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = LightSurface, onSurfaceVariant = Color(0xFF616161),
    surfaceTint = Color.Transparent,
    inverseSurface = DarkSurface, inverseOnSurface = Color.White,
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFFDEDEDE),
    error = Color(0xFFB32222), onError = Color.White,
    errorContainer = Color(0xFFFFE8E6), onErrorContainer = Color(0xFF661411),
    scrim = Color.Black,
    surfaceBright = Color.White, surfaceDim = Color(0xFFE5E5E5),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F1F1),
    surfaceContainer = LightSurface, surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE2E2E2),
)

internal val CompanionDarkColors = darkColorScheme(
    primary = Color.White, onPrimary = Ink,
    primaryContainer = DarkSurface, onPrimaryContainer = Color.White,
    inversePrimary = Ink,
    secondary = Color(0xFFB5B5B5), onSecondary = Ink,
    secondaryContainer = Color(0xFF242424), onSecondaryContainer = Color.White,
    tertiary = Color.White, onTertiary = Ink,
    tertiaryContainer = DarkSurface, onTertiaryContainer = Color.White,
    background = Ink, onBackground = Color.White,
    surface = Ink, onSurface = Color.White,
    surfaceVariant = Color(0xFF242424), onSurfaceVariant = Color(0xFFB5B5B5),
    surfaceTint = Color.Transparent,
    inverseSurface = Paper, inverseOnSurface = Ink,
    outline = Color(0xFF858585), outlineVariant = Color(0xFF353535),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF3B1818), onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
    surfaceBright = Color(0xFF353535), surfaceDim = Ink,
    surfaceContainerLowest = Color(0xFF050505), surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = DarkSurface, surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2D2D2D),
)

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)
private val BaseType = Typography()
private val CompanionTypography = Typography(
    displayLarge = BaseType.displayLarge.copy(fontFamily = Inter),
    displayMedium = BaseType.displayMedium.copy(fontFamily = Inter),
    displaySmall = BaseType.displaySmall.copy(fontFamily = Inter),
    headlineLarge = BaseType.headlineLarge.copy(fontFamily = Inter),
    headlineMedium = BaseType.headlineMedium.copy(fontFamily = Inter),
    headlineSmall = BaseType.headlineSmall.copy(fontFamily = Inter),
    titleLarge = BaseType.titleLarge.copy(fontFamily = Inter),
    titleMedium = BaseType.titleMedium.copy(fontFamily = Inter),
    titleSmall = BaseType.titleSmall.copy(fontFamily = Inter),
    bodyLarge = BaseType.bodyLarge.copy(fontFamily = Inter),
    bodyMedium = BaseType.bodyMedium.copy(fontFamily = Inter),
    bodySmall = BaseType.bodySmall.copy(fontFamily = Inter),
    labelLarge = BaseType.labelLarge.copy(fontFamily = Inter),
    labelMedium = BaseType.labelMedium.copy(fontFamily = Inter),
    labelSmall = BaseType.labelSmall.copy(fontFamily = Inter),
)

/** Follows system brightness, with the approved fixed monochrome palette. */
@Composable
fun CompanionTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) CompanionDarkColors else CompanionLightColors,
        typography = CompanionTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = content,
    )
}
