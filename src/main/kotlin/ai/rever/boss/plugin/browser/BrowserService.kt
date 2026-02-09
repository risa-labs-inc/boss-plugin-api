package ai.rever.boss.plugin.browser

/**
 * Service interface for creating and managing browser instances.
 *
 * This service provides plugins with access to the host application's
 * JxBrowser infrastructure without exposing JxBrowser types directly.
 *
 * Browser instances are created on-demand and should be disposed when
 * no longer needed to conserve resources.
 *
 * ## Usage Example
 *
 * ```kotlin
 * class MyBrowserPlugin : Plugin {
 *     private var browserService: BrowserService? = null
 *     private var browserHandle: BrowserHandle? = null
 *
 *     override fun register(context: PluginContext) {
 *         browserService = context.browserService
 *     }
 *
 *     suspend fun createBrowser() {
 *         browserHandle = browserService?.createBrowser(
 *             BrowserConfig(url = "https://example.com")
 *         )
 *     }
 *
 *     override fun dispose() {
 *         browserHandle?.dispose()
 *         browserHandle = null
 *         browserService = null
 *     }
 * }
 * ```
 */
interface BrowserService {
    /**
     * Check if the browser service is available.
     *
     * This may return false if:
     * - JxBrowser license is not configured
     * - Browser engine failed to initialize
     * - Platform doesn't support embedded browsers
     *
     * @return true if browsers can be created
     */
    fun isAvailable(): Boolean

    /**
     * Create a new browser instance with the given configuration.
     *
     * This is a suspend function as browser creation may involve
     * async initialization.
     *
     * @param config Configuration for the browser
     * @return A browser handle, or null if creation failed
     */
    suspend fun createBrowser(config: BrowserConfig): BrowserHandle?

    /**
     * Dispose a browser instance.
     *
     * This is equivalent to calling [BrowserHandle.dispose] but allows
     * batch disposal operations.
     *
     * @param handle The browser handle to dispose
     */
    suspend fun disposeBrowser(handle: BrowserHandle)

    /**
     * Get the current number of active browsers.
     *
     * Useful for debugging and resource monitoring.
     */
    fun getActiveBrowserCount(): Int
}
