package ai.rever.boss.plugin.api

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Provider interface for window ID access.
 */
interface WindowIdProvider {
    /**
     * Get the current window ID.
     */
    fun getWindowId(): String?
}

/**
 * Provider interface for window project state access.
 */
interface WindowProjectStateProvider {
    /**
     * Get the selected project path.
     */
    fun getSelectedProjectPath(): String?
}

/**
 * CompositionLocal for WindowIdProvider.
 */
val LocalWindowIdProvider = staticCompositionLocalOf<WindowIdProvider?> { null }

/**
 * CompositionLocal for WindowProjectStateProvider.
 */
val LocalWindowProjectStateProvider = staticCompositionLocalOf<WindowProjectStateProvider?> { null }
