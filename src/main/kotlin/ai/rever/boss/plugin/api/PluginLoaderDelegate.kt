package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

/**
 * Information about a loaded plugin.
 * Used by PluginLoaderDelegate to provide plugin information to dynamic plugins.
 */
@Serializable
data class LoadedPluginInfo(
    val pluginId: String,
    val displayName: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val url: String = "",
    val type: String = "panel",
    val apiVersion: String = "",
    val minBossVersion: String = "",
    val isSystemPlugin: Boolean = false,
    val canUnload: Boolean = true,
    val loadPriority: Int = 100,
    val isEnabled: Boolean = true,
    val healthy: Boolean = true,
    val jarPath: String = "",
    val installedAt: Long = 0L,
    val requiresAdmin: Boolean = false,
    val isIncompatible: Boolean = false
)

/**
 * An installed plugin the current user cannot see because they lack its required
 * permissions. Surfaced so the UI can explain *why* a plugin is hidden and *what*
 * to ask an admin to grant. Empty for admins (they bypass the permission gate).
 */
@Serializable
data class InaccessiblePluginInfo(
    val pluginId: String,
    val displayName: String,
    val missingPermissions: List<String>
)

/**
 * Delegate interface for plugin loading/unloading operations.
 *
 * BossConsole implements this interface and registers it via:
 * ```kotlin
 * context.registerPluginAPI(pluginLoaderDelegate)
 * ```
 *
 * Dynamic plugins can retrieve it via:
 * ```kotlin
 * val loader = context.getPluginAPI(PluginLoaderDelegate::class.java)
 * ```
 *
 * This allows dynamic plugins (like plugin-manager) to:
 * - Get list of currently loaded plugins
 * - Trigger plugin load/unload operations
 * - Check admin status
 */
interface PluginLoaderDelegate {

    /**
     * Load a plugin from a JAR file.
     *
     * @param jarPath Absolute path to the plugin JAR
     * @return LoadedPluginInfo if successful, null if loading failed
     */
    suspend fun loadPlugin(jarPath: String): LoadedPluginInfo?

    /**
     * Unload a currently loaded plugin.
     *
     * @param pluginId The plugin ID to unload
     * @return true if successfully unloaded, false otherwise
     */
    suspend fun unloadPlugin(pluginId: String): Boolean

    /**
     * Reload a plugin (unload then load).
     *
     * @param pluginId The plugin ID to reload
     * @return LoadedPluginInfo if successful, null if reload failed
     */
    suspend fun reloadPlugin(pluginId: String): LoadedPluginInfo?

    /**
     * Get list of currently loaded plugins from the runtime.
     * This returns plugins that are actually loaded in memory.
     */
    fun getLoadedPlugins(): List<LoadedPluginInfo>

    /**
     * Check if a plugin is currently loaded in memory.
     */
    fun isPluginLoaded(pluginId: String): Boolean

    /**
     * Get the plugins directory path.
     */
    fun getPluginsDirectory(): String

    /**
     * Get the bundled plugins directory path.
     */
    fun getBundledPluginsDirectory(): String

    /**
     * Check if the current user is a store admin.
     * Store admins can publish plugins and delete plugins from the store.
     */
    fun isCurrentUserAdmin(): Boolean

    /**
     * Enable a plugin.
     *
     * @param pluginId The plugin ID to enable
     * @return true if successfully enabled
     */
    suspend fun enablePlugin(pluginId: String): Boolean

    /**
     * Disable a plugin.
     *
     * @param pluginId The plugin ID to disable
     * @return true if successfully disabled
     */
    suspend fun disablePlugin(pluginId: String): Boolean

    /**
     * Get the current user's access token for authenticated API calls.
     *
     * @return The access token if authenticated, null otherwise
     */
    fun getAccessToken(): String?

    /**
     * Number of currently-open instances (tabs/panels) of a plugin.
     * Used to decide whether to prompt the user to reset running instances
     * after an update. Returns 0 if none are open.
     */
    fun getRunningInstanceCount(pluginId: String): Int = 0

    /**
     * Reset a plugin's running instances so a freshly-installed version takes
     * effect: reloads the plugin and closes its open tabs/panels (the user
     * reopens them on the new version).
     *
     * @param pluginId The plugin ID to reset
     * @return the number of instances that were closed
     */
    suspend fun resetPluginInstances(pluginId: String): Int = 0

    /**
     * Restart the BOSS application. Used to apply updates for plugins that can
     * only take effect after a full restart (system/locked or JAR-swap updates).
     */
    fun restartApplication() {}

    /**
     * Installed plugins the current user cannot access because they lack required
     * permissions, each with the specific missing permissions. Lets a plugin (e.g.
     * the Plugin Manager) show an "ask an admin to grant X" banner instead of the
     * plugin silently not appearing. Default empty for back-compat with older hosts.
     */
    fun getInaccessiblePlugins(): List<InaccessiblePluginInfo> = emptyList()
}
