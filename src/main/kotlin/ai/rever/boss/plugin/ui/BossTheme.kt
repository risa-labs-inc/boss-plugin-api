package ai.rever.boss.plugin.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared UI theme constants for BOSS plugin panels.
 *
 * Provides consistent styling across all plugin panels,
 * aligned with the main BOSS application design language.
 */
object BossThemeColors {
    // Background colors
    val SurfaceColor: Color = BossColors.darkBackground      // Card/sidebar background (#2B2B2B)
    val BackgroundColor: Color = BossColors.darkContentBackground // Content area background (#1E1E1E)
    val BorderColor: Color = BossColors.darkBorder           // Border/divider color (#4D4D4D)

    // Text colors
    val TextPrimary: Color = BossColors.darkTextPrimary      // Primary text color
    val TextSecondary: Color = BossColors.darkTextSecondary  // Secondary text color
    val TextMuted: Color = BossColors.darkTextMuted          // Muted text color (#707070)

    // Accent colors
    val AccentColor: Color = BossColors.darkAccent           // Selection/highlight color
    val SecondaryColor: Color = BossColors.darkSecondary     // Secondary accent

    // Status colors
    val ErrorColor: Color = BossColors.darkError             // Error states
    val SuccessColor: Color = BossColors.darkSuccess         // Success states
    val WarningColor: Color = BossColors.darkWarning         // Warning states
}

/**
 * BOSS application theme.
 *
 * This theme only supports dark mode and ignores system theme settings.
 * The dark theme is mandatory for all screens and components in the app.
 *
 * @param content The content to be styled with this theme
 */
@Composable
fun BossTheme(content: @Composable () -> Unit) {
    val darkColorPalette = darkColors(
        primary = BossColors.darkAccent,
        primaryVariant = BossColors.darkAccent.copy(alpha = 0.8f),
        secondary = BossColors.darkSecondary,
        secondaryVariant = BossColors.darkSecondary.copy(alpha = 0.8f),
        background = BossColors.darkBackground,
        surface = BossColors.darkSurface,
        error = BossColors.darkError,
        onPrimary = BossColors.darkTextPrimary,
        onSecondary = BossColors.darkTextPrimary,
        onBackground = BossColors.darkTextPrimary,
        onSurface = BossColors.darkTextPrimary,
        onError = BossColors.darkTextPrimary
    )

    MaterialTheme(
        colors = darkColorPalette
    ) {
        content()
    }
}
