package ai.rever.boss.plugin.bundled.api

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext

/**
 * BOSS Plugin API - Core provider APIs for BOSS plugins.
 *
 * This is a system/bundled plugin that ships with BossConsole and provides
 * the core provider APIs that other plugins depend on. It loads first
 * (loadPriority=0) and cannot be unloaded (canUnload=false).
 *
 * ## Phase 1 (Current)
 * In Phase 1, this plugin is a no-op placeholder. The provider interfaces
 * and implementations still reside in plugin-api and DefaultPlugin respectively.
 * This establishes the bundled plugin infrastructure for future phases.
 *
 * ## Future Phases
 * - Phase 2: Move provider implementations from DefaultPlugin to this plugin
 * - Phase 3: Split plugin-api into plugin-api-core (minimal) and this plugin (providers)
 * - Phase 4+: Extract main tab plugin APIs (Editor, Terminal, Browser)
 *
 * ## Manifest
 * The plugin manifest is located at META-INF/boss-plugin/plugin.json and declares:
 * - systemPlugin: true - This is a system/bundled plugin
 * - loadPriority: 0 - Loads first before any other plugins
 * - canUnload: false - Cannot be unloaded at runtime
 *
 * @see ai.rever.boss.plugin.api.PluginContext for the provider APIs
 */
class BossPluginAPIPlugin : DynamicPlugin {

    override val pluginId: String = PLUGIN_ID
    override val displayName: String = "BOSS Plugin API"
    override val version: String = VERSION
    override val description: String = "Core provider APIs for BOSS plugins"
    override val author: String = "Risa Labs"
    override val url: String = "https://github.com/risa-labs-inc/boss-plugin-api"

    /**
     * Register the plugin with the host application.
     *
     * Phase 1: No-op - providers still come from DefaultPlugin.
     * Future phases will register provider implementations here.
     *
     * @param context The plugin context providing access to registries and APIs
     */
    override fun register(context: PluginContext) {
        // Phase 1: No-op
        // The provider interfaces are defined in plugin-api and implementations
        // are in DefaultPlugin. This plugin establishes the infrastructure for
        // future phases where providers will be registered here.
        //
        // Future implementation:
        // context.registerPluginAPI(FileSystemDataProviderImpl())
        // context.registerPluginAPI(GitDataProviderImpl())
        // ... etc.
    }

    /**
     * Called when the plugin is being disposed.
     *
     * Note: This plugin has canUnload=false, so dispose() should rarely be called
     * except during application shutdown.
     */
    override fun dispose() {
        // Phase 1: No resources to clean up
    }

    companion object {
        /**
         * Unique identifier for this plugin.
         */
        const val PLUGIN_ID = "ai.rever.boss.plugin.api"

        /**
         * Current version of the plugin (matches plugin-api version).
         */
        const val VERSION = "1.0.15"

        /**
         * Load priority - system plugins use 0-10, this uses 0 as the core API.
         */
        const val LOAD_PRIORITY = 0
    }
}
