package ai.rever.boss.plugin.api

/**
 * Entry representing a visited URL in the browser history.
 *
 * Used for URL autocomplete suggestions in the browser URL bar.
 */
data class UrlHistoryEntry(
    val url: String,
    val title: String,
    val domain: String,
    val visitCount: Int,
    val lastVisited: Long
)

/**
 * Provider for browser URL history and autocomplete suggestions.
 *
 * This interface allows plugins to record visited URLs and retrieve
 * suggestions for URL bar autocomplete functionality.
 *
 * The implementation stores history in ~/.boss/browser-history.json
 */
interface UrlHistoryProvider {
    /**
     * Add or update a URL in the history.
     * If the URL already exists, its visit count is incremented and title updated.
     *
     * @param url The visited URL
     * @param title The page title
     */
    fun addUrl(url: String, title: String)

    /**
     * Get URL suggestions matching a query string.
     * Searches against URL, domain, and title fields.
     *
     * Results are sorted by relevance:
     * 1. Domain starts with query
     * 2. URL starts with query
     * 3. Visit count and recency
     *
     * @param query The search query
     * @param limit Maximum number of suggestions to return (default 10)
     * @return List of matching history entries
     */
    fun getSuggestions(query: String, limit: Int = 10): List<UrlHistoryEntry>

    /**
     * Delete a specific URL from history.
     *
     * @param url The URL to delete
     */
    fun deleteUrl(url: String)

    /**
     * Save the history to disk.
     * Call this periodically or when important to persist changes.
     */
    suspend fun saveHistory()
}
