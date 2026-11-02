package io.cosgriff.waitlight.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.cosgriff.waitlight.R

// Tokens from the design handoff (design/README.md, "Theme tokens"). Dynamic (wallpaper) colour is off.

/** Light colours; also read by the home-screen widget, which has no MaterialTheme. */
internal val LightScheme = lightColorScheme(
    primary = Color(0xFF1F4E5F),
    onPrimary = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF8CC7D6),
    primaryContainer = Color(0xFFE2EDF0),
    onPrimaryContainer = Color(0xFF1F4E5F),
    secondary = Color(0xFF1F4E5F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2EDF0),
    onSecondaryContainer = Color(0xFF1F4E5F),
    background = Color(0xFFF6F7F4),
    onBackground = Color(0xFF17201B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17201B),
    surfaceVariant = Color(0xFFE3E6E1),
    onSurfaceVariant = Color(0xFF4A554F),
    // Every container role is named, so nothing falls back to Material's lavender.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7F4),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE3E6E1),
    outline = Color(0xFFC9CFCB),
    outlineVariant = Color(0xFFE3E6E1),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE3E1),
    onErrorContainer = Color(0xFF7A1A14),
)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFF8CC7D6),
    onPrimary = Color(0xFF0B2730),
    inversePrimary = Color(0xFF1F4E5F),
    primaryContainer = Color(0xFF1F3A43),
    onPrimaryContainer = Color(0xFFA9D6E2),
    secondary = Color(0xFF8CC7D6),
    onSecondary = Color(0xFF0B2730),
    secondaryContainer = Color(0xFF1F3A43),
    onSecondaryContainer = Color(0xFFA9D6E2),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFE4EBE7),
    surface = Color(0xFF1A2320),
    onSurface = Color(0xFFE4EBE7),
    surfaceVariant = Color(0xFF26302C),
    onSurfaceVariant = Color(0xFFA6B2AC),
    surfaceContainerLowest = Color(0xFF0F1513),
    surfaceContainerLow = Color(0xFF0F1513),
    surfaceContainer = Color(0xFF1A2320),
    surfaceContainerHigh = Color(0xFF222D29),
    surfaceContainerHighest = Color(0xFF26302C),
    outline = Color(0xFF3E4B46),
    outlineVariant = Color(0xFF26302C),
    error = Color(0xFFFFC9C3),
    onError = Color(0xFF3A1714),
    errorContainer = Color(0xFF3A1714),
    onErrorContainer = Color(0xFFFFC9C3),
)

/** A badge's fill, the text on it, and its status icon's colour (drawn on the surface). */
@Immutable
data class BadgeColors(val container: Color, val content: Color, val icon: Color)

/** A banner's fill and its text and icon. */
@Immutable
data class BannerColors(val container: Color, val content: Color)

/** The colours Material's scheme doesn't name: wait badges, the hero card, banners. */
@Immutable
data class WaitColors(
    val short: BadgeColors,
    val moderate: BadgeColors,
    val long: BadgeColors,
    /** "Station Open", no estimate. */
    val open: BadgeColors,
    val closed: BadgeColors,
    val stale: BadgeColors,
    val hero: Color,
    val warning: BannerColors,
    val info: BannerColors,
    val error: BannerColors,
)

private val LightWaitColors = WaitColors(
    short = BadgeColors(Color(0xFF1B7F3B), Color(0xFFFFFFFF), Color(0xFF1B7F3B)),
    moderate = BadgeColors(Color(0xFFF2B807), Color(0xFF2E2300), Color(0xFF8A5F00)),
    long = BadgeColors(Color(0xFFC62828), Color(0xFFFFFFFF), Color(0xFFC62828)),
    open = BadgeColors(Color(0xFF6B7570), Color(0xFFFFFFFF), Color(0xFF6B7570)),
    closed = BadgeColors(Color(0xFF6B7570), Color(0xFFFFFFFF), Color(0xFF6B7570)),
    stale = BadgeColors(Color(0xFFE3E6E1), Color(0xFF3E4843), Color(0xFF6B7570)),
    hero = Color(0xFF1F4E5F),
    warning = BannerColors(Color(0xFFFFF1C7), Color(0xFF4A3A00)),
    info = BannerColors(Color(0xFFE3E6E1), Color(0xFF17201B)),
    error = BannerColors(Color(0xFFFBE3E1), Color(0xFF7A1A14)),
)

private val DarkWaitColors = WaitColors(
    short = BadgeColors(Color(0xFF6FCF8A), Color(0xFF00391A), Color(0xFF6FCF8A)),
    moderate = BadgeColors(Color(0xFFF2C94C), Color(0xFF2E2300), Color(0xFFF2C94C)),
    long = BadgeColors(Color(0xFFFF8A80), Color(0xFF4A0000), Color(0xFFFF8A80)),
    open = BadgeColors(Color(0xFF8C9792), Color(0xFF101513), Color(0xFF8C9792)),
    closed = BadgeColors(Color(0xFF8C9792), Color(0xFF101513), Color(0xFF8C9792)),
    stale = BadgeColors(Color(0xFF2A3430), Color(0xFFC4CEC9), Color(0xFF8C9792)),
    hero = Color(0xFF16343E),
    warning = BannerColors(Color(0xFF2A2410), Color(0xFFF2E3AE)),
    info = BannerColors(Color(0xFF222D29), Color(0xFFE4EBE7)),
    error = BannerColors(Color(0xFF3A1714), Color(0xFFFFC9C3)),
)

/** The same, outside composition: the home-screen widget picks by the phone's own light or dark. */
fun waitColors(dark: Boolean): WaitColors = if (dark) DarkWaitColors else LightWaitColors

val LocalWaitColors = staticCompositionLocalOf { LightWaitColors }

/** Roboto Flex, bundled in `res/font` as one variable font; each weight is a setting of its weight axis. */
val RobotoFlex = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(R.font.roboto_flex, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

/** Material's type scale in Roboto Flex; titles and labels a step heavier, as the design draws them. */
private val WaitlightTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = RobotoFlex),
        displayMedium = displayMedium.copy(fontFamily = RobotoFlex),
        displaySmall = displaySmall.copy(fontFamily = RobotoFlex),
        headlineLarge = headlineLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = RobotoFlex),
        bodyLarge = bodyLarge.copy(fontFamily = RobotoFlex),
        bodyMedium = bodyMedium.copy(fontFamily = RobotoFlex),
        bodySmall = bodySmall.copy(fontFamily = RobotoFlex),
        labelLarge = labelLarge.copy(fontFamily = RobotoFlex, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = RobotoFlex),
        labelSmall = labelSmall.copy(fontFamily = RobotoFlex),
    )
}

/**
 * The design's corners: 16dp for rows and banners (`medium`), 20 for the hero card (`large`), 28 for the widget
 * (`extraLarge`). Chips and buttons are fully rounded, as Material draws them. Use `MaterialTheme.shapes`.
 */
val WaitlightShapes = Shapes(
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WaitlightTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = WaitlightTypography,
        shapes = WaitlightShapes,
    ) {
        CompositionLocalProvider(LocalWaitColors provides waitColors(dark), content = content)
    }
}

/** The badge and banner colours for the current theme. */
val MaterialTheme.waitColors: WaitColors
    @Composable @ReadOnlyComposable get() = LocalWaitColors.current
