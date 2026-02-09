package ai.rever.boss.plugin.api

/**
 * Provider for per-domain zoom settings persistence.
 *
 * This interface allows plugins to get and set zoom levels for specific domains,
 * enabling zoom persistence across browser sessions.
 *
 * The implementation stores zoom settings in ~/.boss/browser-zoom-settings.json
 */
interface ZoomSettingsProvider {
    /**
     * Get the zoom level for a specific domain.
     *
     * @param domain The domain to get zoom for (e.g., "github.com")
     * @return The zoom level (1.0 = 100%), or null if no custom zoom is set
     */
    fun getZoomForDomain(domain: String): Double?

    /**
     * Set the zoom level for a specific domain.
     * If zoomLevel is 1.0 (100%), the setting may be removed.
     *
     * @param domain The domain to set zoom for
     * @param zoomLevel The zoom level (1.0 = 100%)
     */
    fun setZoomForDomain(domain: String, zoomLevel: Double)

    /**
     * Extract the domain from a URL.
     *
     * @param url The full URL
     * @return The extracted domain, or null if extraction fails
     */
    fun extractDomain(url: String): String?

    /**
     * Clear the zoom setting for a specific domain (reset to default).
     *
     * @param domain The domain to clear zoom for
     */
    fun clearZoomForDomain(domain: String)

    /**
     * Save settings to disk.
     * Call this after making changes to persist them.
     */
    suspend fun saveSettings()
}
