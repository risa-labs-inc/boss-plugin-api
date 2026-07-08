package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A settings page contributed by a plugin, rendered inside the host settings
 * window under a "Plugins" divider in the settings sidebar.
 *
 * Register via [PluginContext.registerSettingsPage]; the host unregisters
 * automatically on disable/unload. Content is wrapped in the host's plugin
 * error boundary — a crashing page cannot take down the settings window.
 *
 * Persistence: use [PluginContext.pluginStorageFactory]; there is no separate
 * settings persistence helper.
 */
interface SettingsPageProvider {
    /**
     * Stable unique id, by convention `"<pluginId>:<page>"`. Also the deep
     * navigation key (e.g. opening settings directly to this page).
     */
    val pageId: String

    val displayName: String
    val description: String
    val icon: ImageVector

    /** Pages are sorted ascending; built-in sections come first. */
    val order: Int
        get() = 1000

    /** RBAC: the user must hold ALL of these permissions to see the page. */
    val requiredPermissions: Set<String>
        get() = emptySet()

    /** RBAC: only admins see this page. */
    val requiresAdmin: Boolean
        get() = false

    @Composable
    fun Content()
}
