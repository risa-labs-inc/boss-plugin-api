package ai.rever.boss.plugin.browser

import androidx.compose.runtime.Composable

/**
 * Types of form fields that can be detected.
 */
enum class FormFieldType {
    USERNAME,   // Username or login field
    PASSWORD,   // Password field
    EMAIL,      // Email field
    TEXT,       // Generic text field
    UNKNOWN     // Cannot determine
}

/**
 * Information about a form field detected in the browser.
 * Used for secret auto-fill integration.
 */
data class FormFieldInfo(
    /** Type of the form field (username, password, email, etc.) */
    val fieldType: FormFieldType,
    /** Field name attribute */
    val fieldName: String,
    /** Field id attribute */
    val fieldId: String,
    /** Field placeholder text */
    val fieldPlaceholder: String,
    /** Current field value */
    val fieldValue: String,
    /** Parent form's action URL if in a form */
    val parentFormAction: String?,
    /** HTML input type attribute */
    val inputType: String,
    /** Autocomplete attribute value */
    val autocomplete: String
) {
    fun isPasswordField(): Boolean = fieldType == FormFieldType.PASSWORD
    fun isUsernameField(): Boolean = fieldType == FormFieldType.USERNAME || fieldType == FormFieldType.EMAIL
}

/**
 * Information about the context where a right-click occurred in the browser.
 */
data class BrowserContextMenuInfo(
    /** URL of link if right-clicked on a link, null otherwise */
    val linkUrl: String? = null,
    /** Selected text if any text was selected, null otherwise */
    val selectedText: String? = null,
    /** Whether the click was on an editable element (input field, textarea, etc.) */
    val isEditable: Boolean = false,
    /** Whether there's a video element at the click position */
    val hasVideo: Boolean = false,
    /** Whether there's an image at the click position */
    val hasImage: Boolean = false,
    /** Image URL if right-clicked on an image, null otherwise */
    val imageUrl: String? = null,
    /** Current page URL */
    val pageUrl: String = "",
    /** Current page title */
    val pageTitle: String = "",
    /** Form field info if right-clicked on a form field (for secret auto-fill) */
    val formFieldInfo: FormFieldInfo? = null
)

/**
 * Callback for handling context menu requests from the browser.
 */
typealias ContextMenuCallback = (info: BrowserContextMenuInfo) -> Unit

interface BrowserHandle {
    /**
     * Unique identifier for this browser handle.
     */
    val id: String

    /**
     * Whether this browser handle is still valid.
     *
     * Returns false if:
     * - The browser has been disposed
     * - The browser was closed externally
     * - The browser engine was reinitialized
     */
    val isValid: Boolean

    /**
     * Load a URL in the browser.
     *
     * @param url The URL to load
     */
    suspend fun loadUrl(url: String)

    /**
     * Get the current URL.
     *
     * @return The current URL, or empty string if invalid
     */
    fun getCurrentUrl(): String

    /**
     * Get the current page title.
     *
     * @return The current title, or empty string if invalid
     */
    fun getTitle(): String

    /**
     * Add a listener for navigation events.
     *
     * Called when the browser navigates to a new URL.
     *
     * @param listener Callback receiving the new URL
     */
    fun addNavigationListener(listener: (String) -> Unit)

    /**
     * Remove a navigation listener.
     */
    fun removeNavigationListener(listener: (String) -> Unit)

    /**
     * Add a listener for title changes.
     *
     * @param listener Callback receiving the new title
     */
    fun addTitleListener(listener: (String) -> Unit)

    /**
     * Remove a title listener.
     */
    fun removeTitleListener(listener: (String) -> Unit)

    /**
     * Add a listener for favicon changes.
     *
     * @param listener Callback receiving the favicon URL (or null)
     */
    fun addFaviconListener(listener: (String?) -> Unit)

    /**
     * Remove a favicon listener.
     */
    fun removeFaviconListener(listener: (String?) -> Unit)

    /**
     * Navigate back in history.
     */
    fun goBack()

    /**
     * Navigate forward in history.
     */
    fun goForward()

    /**
     * Reload the current page.
     */
    fun reload()

    /**
     * Stop the current page load.
     */
    fun stop()

    /**
     * Check if back navigation is possible.
     */
    fun canGoBack(): Boolean

    /**
     * Check if forward navigation is possible.
     */
    fun canGoForward(): Boolean

    // ============================================================
    // ZOOM CONTROLS
    // ============================================================

    /**
     * Get the current zoom level.
     *
     * @return Zoom level where 1.0 = 100%, 0.5 = 50%, 2.0 = 200%
     */
    fun getZoomLevel(): Double

    /**
     * Set the zoom level.
     *
     * @param level Zoom level where 1.0 = 100%, 0.5 = 50%, 2.0 = 200%
     */
    fun setZoomLevel(level: Double)

    /**
     * Zoom in by one step (typically 10%).
     */
    fun zoomIn()

    /**
     * Zoom out by one step (typically 10%).
     */
    fun zoomOut()

    /**
     * Reset zoom to 100%.
     */
    fun resetZoom()

    /**
     * Add a listener for zoom level changes.
     *
     * @param listener Callback receiving the new zoom level (1.0 = 100%)
     */
    fun addZoomListener(listener: (Double) -> Unit)

    /**
     * Remove a zoom listener.
     */
    fun removeZoomListener(listener: (Double) -> Unit)

    // ============================================================
    // LOADING STATE
    // ============================================================

    /**
     * Check if the browser is currently loading a page.
     *
     * @return true if loading, false otherwise
     */
    fun isLoading(): Boolean

    /**
     * Add a listener for loading state changes.
     *
     * @param listener Callback receiving true when loading starts, false when loading finishes
     */
    fun addLoadingListener(listener: (Boolean) -> Unit)

    /**
     * Remove a loading listener.
     */
    fun removeLoadingListener(listener: (Boolean) -> Unit)

    // ============================================================
    // SECURITY
    // ============================================================

    /**
     * Check if the current page is served over HTTPS.
     *
     * @return true if the URL scheme is "https", false otherwise
     */
    fun isSecure(): Boolean

    // ============================================================
    // CONTEXT MENU
    // ============================================================

    /**
     * Set a callback to be invoked when the user right-clicks in the browser.
     *
     * The callback receives information about the click context (link URL, selected text, etc.)
     * and should display an appropriate context menu.
     *
     * @param callback The callback to invoke on right-click, or null to use default behavior
     */
    fun setContextMenuCallback(callback: ContextMenuCallback?)

    // ============================================================
    // SECRET AUTO-FILL
    // ============================================================

    /**
     * Fill credentials into form fields on the current page.
     *
     * This finds username and password fields and fills them with the provided values.
     * Used for secret manager auto-fill integration.
     *
     * @param username Username to fill
     * @param password Password to fill
     * @param fillBoth If true, fills both username and password. If false, only fills
     *                 the currently focused field based on its type.
     * @return true if credentials were filled successfully, false otherwise
     */
    suspend fun fillCredentials(username: String, password: String, fillBoth: Boolean = true): Boolean

    // ============================================================
    // CLIPBOARD OPERATIONS
    // ============================================================

    /**
     * Copy the currently selected text to the clipboard.
     */
    fun copySelection()

    /**
     * Paste text from the clipboard at the current cursor position.
     */
    fun paste()

    /**
     * Cut the currently selected text to the clipboard.
     */
    fun cut()

    /**
     * Select all text on the page.
     */
    fun selectAll()

    // ============================================================
    // POPUP AND NEW TAB HANDLING
    // ============================================================

    /**
     * Set a callback to be invoked when a link should open in a new tab.
     *
     * This handles:
     * - Cmd+Click (Mac) / Ctrl+Click (Windows/Linux) on links
     * - Links with target="_blank"
     * - window.open() calls from JavaScript
     *
     * @param callback Receives the URL to open in a new tab
     */
    fun setOpenInNewTabCallback(callback: (String) -> Unit)

    // ============================================================
    // PICTURE IN PICTURE
    // ============================================================

    /**
     * Request Picture-in-Picture mode for videos on the current page.
     *
     * This finds the most appropriate video element and toggles PiP mode:
     * - On YouTube: uses the main video player
     * - Single video: uses that video
     * - Multiple videos: uses the largest visible one
     *
     * If PiP is already active, this exits PiP mode.
     */
    fun requestPictureInPicture()

    // ============================================================
    // FULLSCREEN VIDEO SUPPORT
    // ============================================================

    /**
     * Set up fullscreen handling for video content.
     *
     * When web content requests fullscreen (e.g., clicking fullscreen button on a YouTube video),
     * the browser content is moved to a separate fullscreen window. The plugin should display
     * a placeholder in the tab while in fullscreen mode.
     *
     * @param tabId Unique identifier for this tab (used for state tracking)
     * @param onEnterFullscreen Called when the browser enters fullscreen mode.
     *                          The plugin should show a placeholder UI.
     * @param onExitFullscreen Called when the browser exits fullscreen mode.
     *                         The plugin should restore normal browser display.
     */
    fun setFullscreenHandler(
        tabId: String,
        onEnterFullscreen: () -> Unit,
        onExitFullscreen: () -> Unit
    )

    /**
     * Request exit from fullscreen mode.
     *
     * Call this when the user clicks the fullscreen placeholder to return
     * the browser content to the tab.
     */
    fun requestExitFullscreen()

    // ============================================================
    // DEVELOPER TOOLS
    // ============================================================

    /**
     * Show the browser's developer tools (DevTools).
     *
     * This opens JxBrowser's built-in DevTools window for debugging,
     * inspecting elements, network requests, console, etc.
     */
    fun showDevTools()

    /**
     * Composable content that renders the browser.
     *
     * This should be called within a Compose hierarchy to display
     * the browser content. The browser will fill the available space.
     */
    @Composable
    fun Content()

    /**
     * Dispose this browser handle and release resources.
     *
     * After calling this, [isValid] will return false and
     * all other methods will be no-ops.
     */
    fun dispose()
}
