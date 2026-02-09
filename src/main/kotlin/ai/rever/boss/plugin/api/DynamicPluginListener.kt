package ai.rever.boss.plugin.api

/**
 * Listener interface for dynamic plugin lifecycle events.
 *
 * This follows the IntelliJ IDEA pattern for DynamicPluginListener,
 * allowing components to react to plugin load/unload events.
 *
 * Implementations can register with the DynamicPluginManager to receive
 * notifications about plugin lifecycle changes.
 */
interface DynamicPluginListener {
    /**
     * Called before a plugin is loaded.
     *
     * Use this to prepare any infrastructure the plugin might need.
     * This is called before the plugin's [Plugin.register] method.
     *
     * @param manifest The manifest of the plugin being loaded
     */
    fun beforePluginLoaded(manifest: PluginManifest) {}

    /**
     * Called after a plugin has been successfully loaded and registered.
     *
     * Use this to perform any post-load actions, such as refreshing UI
     * to show new panels or tab types.
     *
     * @param manifest The manifest of the loaded plugin
     */
    fun pluginLoaded(manifest: PluginManifest) {}

    /**
     * Called before a plugin begins unloading.
     *
     * Use this to perform cleanup that depends on the plugin still being
     * available. This is called before the plugin's [Plugin.dispose] method.
     *
     * @param manifest The manifest of the plugin being unloaded
     */
    fun beforePluginUnload(manifest: PluginManifest) {}

    /**
     * Called after a plugin has been fully unloaded.
     *
     * Use this to refresh UI or clean up any references to the plugin.
     * At this point, the plugin's classloader may have been disposed.
     *
     * @param manifest The manifest of the unloaded plugin
     */
    fun pluginUnloaded(manifest: PluginManifest) {}

    /**
     * Called when a plugin fails to load.
     *
     * @param manifest The manifest of the plugin that failed (if available)
     * @param error The error that occurred during loading
     */
    fun pluginLoadFailed(manifest: PluginManifest?, error: Throwable) {}

    /**
     * Called when a plugin fails to unload cleanly.
     *
     * This may indicate resource leaks or other issues.
     *
     * @param manifest The manifest of the plugin that failed to unload
     * @param error The error that occurred during unloading
     */
    fun pluginUnloadFailed(manifest: PluginManifest, error: Throwable) {}
}

/**
 * Result of checking whether a plugin can be unloaded.
 */
sealed class CanUnloadResult {
    /**
     * Plugin can be unloaded without issues.
     */
    data object Ok : CanUnloadResult()

    /**
     * Plugin cannot be unloaded due to the specified reasons.
     */
    data class NotAllowed(
        /**
         * Human-readable reasons why the plugin cannot be unloaded.
         */
        val reasons: List<String>
    ) : CanUnloadResult() {
        constructor(reason: String) : this(listOf(reason))
    }

    /**
     * Check if unloading is allowed.
     */
    val isAllowed: Boolean get() = this is Ok
}

/**
 * Interface for components that need to perform actions before plugin unload.
 *
 * Components implementing this interface can veto plugin unloading if they
 * have active references or state that cannot be cleanly released.
 */
interface PluginUnloadAware {
    /**
     * Check if this component allows the specified plugin to be unloaded.
     *
     * @param pluginId The ID of the plugin being unloaded
     * @return Result indicating whether unloading is allowed
     */
    fun checkCanUnload(pluginId: String): CanUnloadResult

    /**
     * Perform cleanup before the plugin is unloaded.
     *
     * This is only called if [checkCanUnload] returned [CanUnloadResult.Ok].
     *
     * @param pluginId The ID of the plugin being unloaded
     */
    fun prepareForUnload(pluginId: String)
}
