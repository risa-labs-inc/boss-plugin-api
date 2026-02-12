package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable

/**
 * Provider interface for panel events.
 * Allows plugins to trigger panel operations.
 */
interface PanelEventProvider {
    /**
     * Close the panel.
     */
    suspend fun closePanel(panelId: PanelId, windowId: String)
}

/**
 * Provider interface for opening settings.
 * Allows plugins to open the settings dialog.
 */
interface SettingsProvider {
    /**
     * Open settings at specific section.
     */
    fun openSettings(windowId: String, section: String)
}

/**
 * Provider interface for the Boss Console dashboard content.
 * Allows browser plugins to display the host's dashboard for about:blank pages.
 */
interface DashboardContentProvider {
    /**
     * Display the Boss Console dashboard.
     *
     * @param onNavigate Callback when user wants to navigate to a URL (from search or quick links)
     */
    @Composable
    fun DashboardContent(
        onNavigate: (String) -> Unit
    )
}
