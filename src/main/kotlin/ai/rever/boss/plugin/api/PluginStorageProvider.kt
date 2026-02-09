package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow

/**
 * Provider interface for persistent plugin storage.
 *
 * This allows plugins to save preferences, state, and data
 * that persists across application restarts.
 *
 * Storage is scoped per-plugin to prevent conflicts between plugins.
 */
interface PluginStorageProvider {

    /**
     * Get the plugin ID this storage is scoped to.
     *
     * @return The plugin ID
     */
    fun getPluginId(): String

    // ============ String Storage ============

    /**
     * Store a string value.
     *
     * @param key The key to store under
     * @param value The value to store
     */
    suspend fun putString(key: String, value: String)

    /**
     * Retrieve a string value.
     *
     * @param key The key to retrieve
     * @param defaultValue Default value if key doesn't exist
     * @return The stored value or default
     */
    suspend fun getString(key: String, defaultValue: String? = null): String?

    // ============ Int Storage ============

    /**
     * Store an integer value.
     *
     * @param key The key to store under
     * @param value The value to store
     */
    suspend fun putInt(key: String, value: Int)

    /**
     * Retrieve an integer value.
     *
     * @param key The key to retrieve
     * @param defaultValue Default value if key doesn't exist
     * @return The stored value or default
     */
    suspend fun getInt(key: String, defaultValue: Int = 0): Int

    // ============ Long Storage ============

    /**
     * Store a long value.
     *
     * @param key The key to store under
     * @param value The value to store
     */
    suspend fun putLong(key: String, value: Long)

    /**
     * Retrieve a long value.
     *
     * @param key The key to retrieve
     * @param defaultValue Default value if key doesn't exist
     * @return The stored value or default
     */
    suspend fun getLong(key: String, defaultValue: Long = 0L): Long

    // ============ Boolean Storage ============

    /**
     * Store a boolean value.
     *
     * @param key The key to store under
     * @param value The value to store
     */
    suspend fun putBoolean(key: String, value: Boolean)

    /**
     * Retrieve a boolean value.
     *
     * @param key The key to retrieve
     * @param defaultValue Default value if key doesn't exist
     * @return The stored value or default
     */
    suspend fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    // ============ Float/Double Storage ============

    /**
     * Store a float value.
     *
     * @param key The key to store under
     * @param value The value to store
     */
    suspend fun putFloat(key: String, value: Float)

    /**
     * Retrieve a float value.
     *
     * @param key The key to retrieve
     * @param defaultValue Default value if key doesn't exist
     * @return The stored value or default
     */
    suspend fun getFloat(key: String, defaultValue: Float = 0f): Float

    // ============ JSON Storage ============

    /**
     * Store a JSON string. Use this for complex objects.
     *
     * @param key The key to store under
     * @param jsonValue The JSON string to store
     */
    suspend fun putJson(key: String, jsonValue: String)

    /**
     * Retrieve a JSON string.
     *
     * @param key The key to retrieve
     * @return The stored JSON string or null
     */
    suspend fun getJson(key: String): String?

    // ============ Utility Methods ============

    /**
     * Check if a key exists.
     *
     * @param key The key to check
     * @return True if the key exists
     */
    suspend fun contains(key: String): Boolean

    /**
     * Remove a specific key.
     *
     * @param key The key to remove
     */
    suspend fun remove(key: String)

    /**
     * Get all keys stored by this plugin.
     *
     * @return Set of all keys
     */
    suspend fun getAllKeys(): Set<String>

    /**
     * Clear all data for this plugin.
     */
    suspend fun clear()

    /**
     * Observe changes to a specific key.
     *
     * @param key The key to observe
     * @return Flow that emits when the key value changes
     */
    fun observeString(key: String): Flow<String?>

    /**
     * Observe changes to any key.
     *
     * @return Flow that emits the key name when any value changes
     */
    fun observeChanges(): Flow<String>

    // ============ Batch Operations ============

    /**
     * Store multiple key-value pairs atomically.
     *
     * @param entries Map of key-value pairs to store
     */
    suspend fun putAll(entries: Map<String, Any>) {
        entries.forEach { (key, value) ->
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Long -> putLong(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                else -> putJson(key, value.toString())
            }
        }
    }
}

/**
 * Factory interface for creating plugin-scoped storage providers.
 *
 * The host application implements this to provide storage instances
 * to individual plugins.
 */
interface PluginStorageFactory {
    /**
     * Create a storage provider scoped to a specific plugin.
     *
     * @param pluginId The plugin ID to scope storage to
     * @return A storage provider for that plugin
     */
    fun createStorage(pluginId: String): PluginStorageProvider
}
