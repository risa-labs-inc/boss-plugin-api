package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Provider interface for accessing active tabs across the application.
 *
 * This interface allows the TopOfMind panel to display all open tabs
 * organized by workspace without direct coupling to SplitViewState.
 *
 * Implemented by the host and marked [HostImplemented] because the host compiles this in and
 * serves it parent-first: an older host's copy is what a plugin resolves, so a member added here
 * ships with a BossConsole release and is gated on `minBossVersion`, never `minApiVersion` alone.
 */
@HostImplemented
interface ActiveTabsProvider {
    /**
     * StateFlow of all active tabs across all workspaces.
     */
    val activeTabs: StateFlow<List<ActiveTabData>>

    /**
     * Refresh the active tabs list.
     */
    suspend fun refreshTabs()

    /**
     * Select/focus a specific tab.
     *
     * @param tabId The ID of the tab to select
     * @param panelId The panel containing the tab
     */
    fun selectTab(tabId: String, panelId: String)

    /**
     * Get the URL of a tab (if it's a browser tab).
     *
     * @param tabId The ID of the tab
     * @return The URL or null if not a browser tab
     */
    fun getTabUrl(tabId: String): String?

    /**
     * Get the favicon cache key for a tab (if it's a browser tab).
     *
     * @param tabId The ID of the tab
     * @return The favicon cache key or null
     */
    fun getFaviconCacheKey(tabId: String): String?

    /**
     * Load a favicon by cache key.
     * This is a composable function that loads and displays the favicon.
     *
     * @param cacheKey The favicon cache key
     * @return A composable painter or null if not found
     */
    @Composable
    fun loadFavicon(cacheKey: String?): Painter?

    /**
     * Get the fallback icon for a tab type.
     *
     * @param typeId The tab type identifier
     * @return The fallback icon vector
     */
    fun getFallbackIcon(typeId: String): ImageVector?

    /**
     * Get browser integration for a specific tab.
     *
     * This allows plugins to execute JavaScript and interact with browser tabs.
     * Only browser tabs (e.g., Fluck tabs) support browser integration.
     *
     * @param tabId The ID of the tab to get browser integration for
     * @return A BrowserIntegration instance, or null if:
     *         - The tab is not a browser tab
     *         - The browser is not available
     *         - The tab does not exist
     */
    fun getBrowserIntegration(tabId: String): BrowserIntegration?

    /**
     * Create a new browser tab with the given URL and title.
     *
     * This creates a new Fluck (browser) tab in the active panel and navigates to the URL.
     * The tab will be automatically selected after creation.
     *
     * @param url The initial URL to navigate to
     * @param title The tab title (displayed in the tab bar)
     * @return The ID of the created tab, or null if creation failed
     */
    fun createBrowserTab(url: String, title: String): String?

    /**
     * Create a browser tab in a new split to the **right** of the active panel
     * and return its tab id (drive it via [getBrowserIntegration]).
     *
     * Unlike [createBrowserTab] (which adds a tab to the active panel), this
     * splits the active panel left/right and places the browser in the new right
     * pane, so it sits beside the caller's tab rather than on top of it. Useful
     * for automation that wants to show a live browser next to its own UI.
     *
     * In-process plugins only (needs the host's split-view state). The default
     * is a no-op returning null, so hosts/proxies that don't support it are
     * unaffected — callers should fall back (e.g. to headless) on null.
     *
     * @param url The initial URL to navigate to
     * @param title The tab title (displayed in the tab bar)
     * @return The ID of the created tab, or null if unavailable
     */
    fun createBrowserTabInRightSplit(url: String, title: String): String? = null

    /**
     * Close a tab by its ID.
     *
     * @param tabId The ID of the tab to close
     * @return true if the tab was closed successfully
     */
    fun closeTab(tabId: String): Boolean

    /**
     * Whether this host implements [moveTabToWorkspace].
     *
     * Tells "this host has no implementation" apart from "it ran and refused", which the
     * defaulted `false` return cannot - same shape as `SplitViewOperations.supportsOpenPanelAsTab`
     * and `BookmarkDataProvider.supportsBulkAdd`. Out-of-process plugins get `false`: the IPC
     * proxy does not forward the move, so an affordance built on it would silently do nothing.
     *
     * Host-wide, not per-tab. `true` says the call is wired up, never that any particular tab or
     * workspace will resolve.
     */
    val supportsTabTransfer: Boolean get() = false

    /**
     * Every workspace this window is actually RUNNING - the one on screen plus the ones preserved
     * behind it - whether or not they currently hold any tabs.
     *
     * Switching workspaces does not tear the old one down, so a window runs several at once and
     * shows one. That is why [activeTabs] reports tabs whose `workspaceId` is not the current one.
     *
     * Not derivable from [activeTabs]: a workspace with no tabs contributes no rows there, so a
     * freshly created empty workspace would be invisible - and an empty workspace is a perfectly
     * good destination for [moveTabToWorkspace]. Nor is it `WorkspaceDataProvider.workspaces`,
     * which lists every workspace SAVED on disk, most of which are not running and cannot receive
     * a live tab.
     */
    val liveWorkspaceIds: Set<String> get() = emptySet()

    /**
     * Move a tab into another workspace running in this window, keeping it alive.
     *
     * The tab's component instance and its lifecycle transfer as-is, so a browser tab keeps its
     * page, its history and its playing media, and a terminal keeps its session. This is a MOVE of
     * a running thing, not a close-and-reopen from saved configuration.
     *
     * **Destinations are limited to [liveWorkspaceIds].** A workspace that exists only on disk has
     * no live panel to receive the tab; putting one there would mean serializing it into the saved
     * layout and destroying the component, which is a different operation and not this one. Passing
     * a workspace id that is not live returns `false`.
     *
     * **Which panel it lands in** is the destination workspace's active panel; the host picks it. A
     * caller cannot name one, because [ActiveTabData.panelId] is only ever populated for panels
     * that already hold tabs.
     *
     * Nothing else moves: the current workspace stays on screen, and the tab is NOT selected in its
     * new panel. Call [selectTab] afterwards if you want it foremost when the user next goes there.
     *
     * Suspending because the transfer must run on the UI thread; the implementation marshals.
     *
     * Gate on [supportsTabTransfer], and on the `minBossVersion` of the release that pins the api
     * adding this. The defaulted `false` covers a host that ships this api version without the
     * implementation; it does NOT make an older host safe, since the api package is served
     * parent-first and that host's copy has no such method at all.
     *
     * @param tabId The tab to move, from [activeTabs].
     * @param targetWorkspaceId A workspace id from [liveWorkspaceIds].
     * @return true if the tab was moved.
     */
    suspend fun moveTabToWorkspace(tabId: String, targetWorkspaceId: String): Boolean = false
}

/**
 * Data class representing an active tab.
 */
@Serializable
data class ActiveTabData(
    val tabId: String,
    val typeId: String,
    val title: String,
    val workspaceId: String,
    val workspaceName: String,
    val panelId: String,
    val windowId: String,
    val splitPosition: String? = null,
    val url: String? = null,
    val faviconCacheKey: String? = null
)

/**
 * Data class representing workspace layout info for TopOfMind display.
 */
@Serializable
sealed class WorkspaceLayoutData {
    @Serializable
    data class SinglePanel(val panelId: String) : WorkspaceLayoutData()

    @Serializable
    data class VerticalSplit(val leftPanelId: String, val rightPanelId: String) : WorkspaceLayoutData()

    @Serializable
    data class HorizontalSplit(val topPanelId: String, val bottomPanelId: String) : WorkspaceLayoutData()
}
