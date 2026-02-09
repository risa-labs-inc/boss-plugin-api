package ai.rever.boss.plugin.api

/**
 * Interface for interacting with browser instances.
 *
 * This interface allows plugins to execute JavaScript in browser tabs
 * and interact with web content. It provides a safe, sandboxed way
 * for dynamic plugins to interact with existing browser tabs.
 *
 * ## Usage Example
 *
 * ```kotlin
 * class MyPlugin : Plugin {
 *     override fun register(context: PluginContext) {
 *         val activeTabsProvider = context.activeTabsProvider
 *         val tabs = activeTabsProvider?.activeTabs?.value ?: return
 *
 *         // Get browser integration for a specific tab
 *         val browserTab = tabs.firstOrNull() ?: return
 *         val browser = activeTabsProvider.getBrowserIntegration(browserTab.tabId)
 *
 *         // Execute JavaScript
 *         browser?.executeJavaScript("document.title")
 *     }
 * }
 * ```
 *
 * ## Security Notes
 *
 * - JavaScript execution is limited to the browser context
 * - Scripts cannot access local file system directly
 * - Cross-origin restrictions apply
 */
interface BrowserIntegration {
    /**
     * Execute JavaScript in the browser context.
     *
     * @param script The JavaScript code to execute
     * @return The result of the JavaScript execution, or null if execution failed
     */
    suspend fun executeJavaScript(script: String): Any?

    /**
     * Check if the browser is currently available and accessible.
     *
     * This may return false if:
     * - The browser tab has been closed
     * - The browser is being disposed
     * - The browser engine encountered an error
     *
     * @return true if the browser is available for interaction
     */
    fun isBrowserAvailable(): Boolean

    /**
     * Get the current URL of the browser tab.
     *
     * @return The current URL, or null if unavailable
     */
    suspend fun getCurrentUrl(): String?
}
