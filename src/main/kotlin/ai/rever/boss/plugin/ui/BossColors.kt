package ai.rever.boss.plugin.ui

import androidx.compose.ui.graphics.Color

/**
 * BOSS application color palette.
 * JetBrains-inspired dark theme colors.
 */
object BossColors {
    // Background colors
    val darkBackground = Color(0xFF2B2B2B)
    val darkSurface = Color(0xFF3C3F41)
    val darkContentBackground = Color(0xFF1E1E1E)
    val darkBorder = Color(0xFF4D4D4D)

    // Text colors
    val darkTextPrimary = Color(0xFFF2F2F2)
    val darkTextSecondary = Color(0xFFAAAAAA)
    val darkTextMuted = Color(0xFF707070)

    // Accent colors
    val darkAccent = Color(0xFF3592C4)
    val darkSecondary = Color(0xFF43A047)

    // Status colors
    val darkError = Color(0xFFE53935)
    val darkSuccess = Color(0xFF4CAF50)
    val darkWarning = Color(0xFFFFA726)

    // Context menu colors
    val contextMenuBackground = Color(0xFF2B2B2B)
    val contextMenuBorder = Color(0xFF3C3F41)
    val contextMenuHover = Color(0xFF3A3D40)
}

// Convenience aliases for backward compatibility
val BossDarkBackground = BossColors.darkBackground
val BossDarkSurface = BossColors.darkSurface
val BossDarkContentBackground = BossColors.darkContentBackground
val BossDarkBorder = BossColors.darkBorder
val BossDarkTextPrimary = BossColors.darkTextPrimary
val BossDarkTextSecondary = BossColors.darkTextSecondary
val BossDarkTextMuted = BossColors.darkTextMuted
val BossDarkAccent = BossColors.darkAccent
val BossDarkSecondary = BossColors.darkSecondary
val BossDarkError = BossColors.darkError
val BossDarkSuccess = BossColors.darkSuccess
val BossDarkWarning = BossColors.darkWarning
val ContextMenuBackground = BossColors.contextMenuBackground
val ContextMenuBorder = BossColors.contextMenuBorder
val ContextMenuHover = BossColors.contextMenuHover
