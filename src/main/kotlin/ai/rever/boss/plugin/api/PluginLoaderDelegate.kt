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
    val requiresAdmin: Boolean = false
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
}
