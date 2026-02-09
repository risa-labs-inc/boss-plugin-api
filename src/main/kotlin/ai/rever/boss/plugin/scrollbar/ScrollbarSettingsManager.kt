package ai.rever.boss.plugin.scrollbar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Scrollbar settings manager for JVM/Desktop.
 * This provides compile-time API for plugins.
 * At runtime, BossConsole provides the actual implementation via sharedPackages.
 */
object ScrollbarSettingsManager {
    private val _currentSettings = MutableStateFlow(ScrollbarSettings())

    /**
     * Current settings state flow
     */
    val currentSettings: StateFlow<ScrollbarSettings> = _currentSettings.asStateFlow()

    /**
     * Update scrollbar settings and save to disk asynchronously.
     */
    suspend fun updateSettings(settings: ScrollbarSettings) {
        _currentSettings.value = settings
    }

    /**
     * Reset settings to defaults
     */
    suspend fun resetToDefault() {
        _currentSettings.value = getDefaultSettings()
    }

    /**
     * Get default settings
     */
    fun getDefaultSettings(): ScrollbarSettings = ScrollbarSettings()
}
