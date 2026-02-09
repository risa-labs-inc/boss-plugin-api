package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for plugins to provide search results to GlobalSearchService.
 *
 * Plugins implement this and register via context.registerSearchProvider().
 * This enables a decentralized search architecture where any plugin can
 * contribute to global search results.
 *
 * Example usage:
 * ```kotlin
 * class MySearchProvider : SearchProvider {
 *     override val providerId = "my-plugin"
 *     override val displayName = "My Plugin"
 *
 *     override suspend fun search(query: String, limit: Int): List<SearchResult> {
 *         // Return search results matching the query
 *     }
 * }
 *
 * // In plugin registration:
 * override fun register(context: PluginContext) {
 *     context.registerSearchProvider(MySearchProvider())
 * }
 * ```
 */
interface SearchProvider {
    /**
     * Unique identifier for this search provider.
     * Used for tracking and deduplication.
     */
    val providerId: String

    /**
     * Display name shown in search results category headers.
     */
    val displayName: String

    /**
     * Search and return results matching the query.
     *
     * @param query The search query string
     * @param limit Maximum number of results to return
     * @return List of search results, sorted by relevance
     */
    suspend fun search(query: String, limit: Int = 50): List<PluginSearchResult>

    /**
     * Optional: Provide results as a flow for real-time updates.
     *
     * If implemented, GlobalSearchService may use this for live search
     * as the user types. Return null to use the standard search() method.
     *
     * @param query The search query string
     * @param limit Maximum number of results to return
     * @return Flow of search results, or null to use standard search
     */
    fun searchFlow(query: String, limit: Int = 50): Flow<List<PluginSearchResult>>? = null
}

/**
 * Search result returned by SearchProvider.
 *
 * This is a generic result type that can represent any searchable item.
 * The action field determines what happens when the result is selected.
 */
data class PluginSearchResult(
    /** Unique identifier for this result within the provider */
    val id: String,

    /** Primary display text */
    val title: String,

    /** Secondary display text (e.g., URL, path, description) */
    val subtitle: String? = null,

    /** Icon to display for this result */
    val icon: SearchResultIcon? = null,

    /** Category name for grouping (e.g., "Bookmarks", "Git", "Files") */
    val category: String,

    /** ID of the provider that returned this result */
    val providerId: String,

    /** Action to perform when this result is selected */
    val action: SearchResultAction,

    /** Relevance score (higher is better, used for sorting) */
    val score: Int = 0,

    /** Match ranges for highlighting (optional) */
    val matchRanges: List<SearchMatchRange> = emptyList(),

    /** Additional metadata for custom handling */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Character range for highlighting matched text.
 */
data class SearchMatchRange(
    val start: Int,
    val end: Int
)

/**
 * Action to perform when a search result is selected.
 */
sealed class SearchResultAction {
    /**
     * Open a URL in the browser.
     */
    data class OpenUrl(val url: String) : SearchResultAction()

    /**
     * Open a file in the editor.
     */
    data class OpenFile(
        val path: String,
        val line: Int? = null,
        val column: Int? = null
    ) : SearchResultAction()

    /**
     * Execute a callback registered with the search provider.
     */
    data class ExecuteCallback(val callbackId: String) : SearchResultAction()

    /**
     * Custom action with arbitrary data.
     */
    data class Custom(
        val actionType: String,
        val data: Map<String, String> = emptyMap()
    ) : SearchResultAction()
}

/**
 * Icon types for search results.
 */
sealed class SearchResultIcon {
    /**
     * Icon loaded from a URL (e.g., favicon).
     */
    data class Url(val url: String) : SearchResultIcon()

    /**
     * Icon loaded from an app resource.
     */
    data class Resource(val name: String) : SearchResultIcon()

    /**
     * Emoji icon.
     */
    data class Emoji(val emoji: String) : SearchResultIcon()

    /**
     * Material icon by name.
     */
    data class MaterialIcon(val iconName: String) : SearchResultIcon()

    /**
     * Favicon cache key (for browser bookmarks).
     */
    data class FaviconCache(val cacheKey: String) : SearchResultIcon()
}

/**
 * Registry for managing search providers.
 *
 * GlobalSearchService uses this to collect and query all registered providers.
 */
interface SearchRegistry {
    /**
     * All registered search providers.
     */
    val providers: StateFlow<List<SearchProvider>>

    /**
     * Register a search provider.
     *
     * @param provider The provider to register
     */
    fun registerProvider(provider: SearchProvider)

    /**
     * Unregister a search provider.
     *
     * @param providerId The ID of the provider to unregister
     */
    fun unregisterProvider(providerId: String)

    /**
     * Get a provider by ID.
     *
     * @param providerId The provider ID
     * @return The provider, or null if not found
     */
    fun getProvider(providerId: String): SearchProvider?
}
