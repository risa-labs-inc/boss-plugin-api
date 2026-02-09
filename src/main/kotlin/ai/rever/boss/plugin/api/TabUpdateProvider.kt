package ai.rever.boss.plugin.api

/**
 * Provider interface for dynamic plugins to request tab info updates.
 *
 * This allows tab-based plugins to update the title, icon, and other metadata
 * displayed in the tab bar without needing direct access to the host's
 * internal tab management classes.
 *
 * The provider is scoped to a specific tab instance and is provided to
 * the tab component at creation time via the factory.
 */
interface TabUpdateProvider {
    /**
     * The ID of the tab this provider is associated with.
     */
    val tabId: String

    /**
     * Update the tab's title displayed in the tab bar.
     *
     * @param title The new title to display
     */
    fun updateTitle(title: String)

    /**
     * Update the tab's favicon/icon.
     *
     * @param faviconUrl URL to the favicon image, or null to clear
     */
    fun updateFavicon(faviconUrl: String?)

    /**
     * Update the tab's URL (for browser tabs).
     *
     * @param url The current URL
     */
    fun updateUrl(url: String)

    /**
     * Close this tab.
     */
    fun closeTab()

    /**
     * Open a new tab with the specified URL.
     *
     * @param url The URL to open in the new tab
     * @return The ID of the newly created tab, or null if creation failed
     */
    fun openNewTab(url: String): String?
}

/**
 * Factory for creating TabUpdateProviders.
 *
 * The host provides this factory to allow tab components to get
 * update providers for their tabs.
 */
interface TabUpdateProviderFactory {
    /**
     * Create a TabUpdateProvider for the specified tab.
     *
     * @param tabId The ID of the tab
     * @param typeId The type ID of the tab
     * @return A provider for updating that tab, or null if the tab doesn't exist
     */
    fun createProvider(tabId: String, typeId: TabTypeId): TabUpdateProvider?
}
