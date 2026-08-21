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
    /**
     * The plugin's homepage, straight from its manifest. Display metadata, and NOT a download
     * source: every plugin declares one and it is almost always the repo it is developed in, which
     * says nothing about where this copy was fetched from. Reading it as a source is what [sourceUrl]
     * exists to stop - see there.
     */
    val url: String = "",
    /**
     * Where this copy was actually installed FROM, or "" for a plugin store install.
     *
     * The host records this when it installs a plugin and it is the only field that answers "fetch
     * an update from where". [url] cannot answer it: a store plugin whose repo is private still
     * declares that repo as its homepage, and treating the two as interchangeable sent store
     * updates to an unauthenticated GitHub API call that answers 404 for every private repo.
     *
     * Blank has one meaning, deliberately: ask the store. That covers both a store install and a
     * host too old to populate this field, and both want the same answer.
     */
    val sourceUrl: String = "",
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
 * Why a plugin is being unloaded, so the host can word its prompt and decide what to do with
 * the plugins that depend on it.
 *
 * The distinction matters because an update ends with the plugin present again at a newer
 * version and a removal does not. The host refuses neither outright any more - it asks - but
 * "your Flow tabs will reopen on the new version" and "Flow will stop working" are not the same
 * sentence, and the dependents are restarted at different moments.
 */
enum class PluginUnloadIntent {
    /** The plugin will be reinstalled immediately afterwards, at a different version. */
    UPDATE,

    /** The plugin is going away. */
    REMOVE,

    /**
     * The caller did not say. This is what [PluginLoaderDelegate.unloadPlugin] reports: it
     * predates this enum and cannot tell which button was pressed, so the host words its prompt
     * neutrally and treats the timing as [UPDATE] - by far the more common reason a plugin asks
     * the host to unload something.
     */
    UNSPECIFIED
}

/**
 * A loaded plugin that declares a dependency on some other plugin.
 *
 * [optional] mirrors the manifest flag and is not cosmetic here: an optional dependent does not
 * veto an unload, but it is still restarted, because "works without it" describes a cold start,
 * not a handle already resolved against a classloader that has since closed.
 */
@Serializable
data class DependentPluginInfo(
    val pluginId: String,
    val displayName: String,
    val optional: Boolean,
    /** Open tabs/panels this dependent currently has, which a restart would close. */
    val runningInstances: Int = 0
)

/**
 * The outcome of an unload the user may have been asked about.
 *
 * [PluginLoaderDelegate.unloadPlugin] returns a bare `Boolean`, which conflates three answers a
 * caller needs to word differently: it worked, the user declined, and the host refused. The
 * refusal reasons name the plugins standing in the way and previously never left the host, so a
 * plugin could only guess at them in prose.
 */
@Serializable
data class PluginUnloadResult(
    val unloaded: Boolean,
    /**
     * The user answered Cancel in the host's dependent-restart prompt.
     *
     * Not a failure, and must not be reported as one: nothing was downloaded, nothing was
     * unloaded, and the plugin is still running the version it was.
     */
    val cancelledByUser: Boolean = false,
    /**
     * Why the host refused, e.g. `Plugin 'Flow' depends on this plugin`.
     *
     * Empty when [unloaded] is true and when [cancelledByUser] is - a refusal and a decline are
     * different answers. Also empty on a host predating this type, whose default implementation
     * has no reasons to hand back.
     */
    val reasons: List<String> = emptyList()
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
     * Equivalent to [unloadPluginForIntent] with [PluginUnloadIntent.UNSPECIFIED], reduced to a
     * `Boolean`. Prefer that overload: it says whether the user declined and, when the host
     * refused, which plugins were in the way.
     *
     * @param pluginId The plugin ID to unload
     * @return true if successfully unloaded, false otherwise
     */
    suspend fun unloadPlugin(pluginId: String): Boolean

    /**
     * Unload a plugin, telling the host why.
     *
     * When other loaded plugins depend on [pluginId], the host asks the user before proceeding
     * and restarts those dependents afterwards; see [getDependentPlugins]. A declined prompt
     * comes back as [PluginUnloadResult.cancelledByUser], which is not an error.
     *
     * The default delegates to [unloadPlugin] so a host predating this method still behaves,
     * losing only the intent and the reasons. **Call it defensively.** This interface is
     * implemented by the host, so on a host built against an older api pin the call site
     * resolves against the host's own older copy of the interface and throws a
     * [LinkageError]; keep the call in its own function and fall back to [unloadPlugin].
     *
     * @param pluginId The plugin ID to unload
     * @param intent why, so the host can word its prompt and time the dependents' restart
     */
    suspend fun unloadPluginForIntent(
        pluginId: String,
        intent: PluginUnloadIntent
    ): PluginUnloadResult = PluginUnloadResult(unloaded = unloadPlugin(pluginId))

    /**
     * Loaded, enabled plugins whose manifest declares a dependency on [pluginId].
     *
     * Includes optional declarations, which [unloadPluginForIntent] also prompts about: those
     * are the ones the AI Gateway's consumers use, and an optional dependent left running
     * across its dependency's update holds a handle into a closed classloader.
     *
     * Empty on a host predating this method - which is indistinguishable from "nothing depends
     * on it", so do not treat an empty list as proof that unloading is safe.
     */
    fun getDependentPlugins(pluginId: String): List<DependentPluginInfo> = emptyList()

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
