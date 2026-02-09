package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

/**
 * Provider interface for screen capture functionality.
 *
 * This interface provides external browser tab plugins with:
 * 1. Access to internal browser tabs for screen capture picker UI
 * 2. macOS screen capture permission APIs
 *
 * The actual screen capture flow (JxBrowser StartCaptureSessionCallback, picker UI,
 * source selection) is handled by the tab plugin itself.
 *
 * Usage in external plugin:
 * ```kotlin
 * browser.set(StartCaptureSessionCallback::class.java) { params, tell ->
 *     // Check permission
 *     if (!screenCaptureProvider.hasPermission()) {
 *         if (!screenCaptureProvider.requestPermission()) {
 *             tell.cancel()
 *             return@StartCaptureSessionCallback
 *         }
 *     }
 *
 *     // Get internal tabs for picker
 *     val internalTabs = screenCaptureProvider.getInternalBrowserTabs()
 *
 *     // Show custom picker UI and call tell.selectSource() when user selects
 * }
 * ```
 */
interface ScreenCaptureProvider {
    /**
     * Get all internal browser tabs that can be captured.
     *
     * These are tabs from the host application that should be shown
     * in the screen capture picker's "Tab" section alongside JxBrowser's
     * browser sources.
     *
     * @return List of internal browser tab data for the picker
     */
    fun getInternalBrowserTabs(): List<InternalBrowserTabData>

    /**
     * Check if the system has screen capture permission.
     *
     * On macOS, this checks CGPreflightScreenCaptureAccess.
     * On other platforms, this returns true (permission assumed).
     *
     * @return true if screen capture is permitted
     */
    fun hasPermission(): Boolean

    /**
     * Request screen capture permission from the system.
     *
     * On macOS, this triggers CGRequestScreenCaptureAccess which shows the system dialog.
     * On other platforms, this is a no-op that returns true.
     *
     * @return true if permission was granted or already exists
     */
    fun requestPermission(): Boolean
}

/**
 * Data for an internal browser tab that can be shown in the screen capture picker.
 *
 * The plugin should match these with JxBrowser's CaptureSources.browsers() by title
 * to enrich the picker display with favicon information.
 */
@Serializable
data class InternalBrowserTabData(
    /**
     * Tab title for display in the picker.
     * Used to match with JxBrowser's browser source name.
     */
    val title: String,

    /**
     * Current URL of the tab (used for high-quality favicon loading).
     */
    val url: String?,

    /**
     * Favicon cache key for loading the tab's favicon.
     */
    val faviconCacheKey: String?
)
