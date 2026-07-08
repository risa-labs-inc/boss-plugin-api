package ai.rever.boss.plugin.api

/**
 * Provider interface for cache management.
 *
 * Allows plugins to manage their cached data and request
 * application-level cache operations.
 */
interface CacheProvider {

    /**
     * Clear the plugin's own cached data.
     *
     * @param pluginId The plugin ID whose cache should be cleared
     * @return true if cache was successfully cleared
     */
    fun clearPluginCache(pluginId: String): Boolean

    /**
     * Get the approximate size of the plugin's cache in bytes.
     *
     * @param pluginId The plugin ID to check
     * @return Cache size in bytes, or -1 if unknown
     */
    fun getPluginCacheSize(pluginId: String): Long

    /**
     * Get the path to the plugin's cache directory.
     * The directory will be created if it doesn't exist.
     *
     * @param pluginId The plugin ID
     * @return Absolute path to the cache directory
     */
    fun getPluginCacheDirectory(pluginId: String): String
}
