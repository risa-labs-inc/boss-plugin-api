package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
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

    /**
     * Move a tab into a NAMED pane, of any workspace running in this window - including its own.
     *
     * [moveTabToWorkspace] lets the host pick the pane, which is right for a drop onto a workspace
     * and useless for a drop onto a pane: a panel listing every pane of every running workspace can
     * be pointed at one, and had no way to say so. It also could not express a move between two
     * panes of ONE workspace at all, because a workspace-only verb cannot tell that from a move to
     * where the tab already is.
     *
     * Everything [moveTabToWorkspace] says still holds: the live component and its lifecycle
     * transfer, so a browser tab keeps its page and a terminal its session; destinations are
     * limited to [liveWorkspaceIds]; the tab is NOT selected in its new pane; and the current
     * workspace stays on screen.
     *
     * **[targetPanelId] must be a pane of [targetWorkspaceId]**, as reported by
     * [ActiveTabData.panelId] for a tab already in it. A pane that workspace does not have is
     * refused rather than quietly redirected - landing the tab somewhere else would hide a caller's
     * wrong idea of the layout from the caller and from the user watching the tab move. So is the
     * pane the tab is already in.
     *
     * **[targetIndex] is where in that pane's list the tab should sit**, or null to append. It is
     * what makes dropping ABOVE or BELOW a particular tab expressible, and what makes a move within
     * the pane a tab is already in a real request - a reorder - where without it there is nothing to
     * do. The index is CLAMPED, not rejected: a caller computes it from a list it read a frame ago
     * and a tab can close in between, so "as near as asked" is the better answer to a drop the user
     * has already committed to.
     *
     * Note what this cannot name: a pane with NO tabs in it. `ActiveTabData` is a tab, so a pane
     * with none contributes no id anywhere, and there is nothing to pass. Use [moveTabToWorkspace]
     * for that case and let the host choose.
     *
     * Gated by [supportsTabTransfer] and by the same `minBossVersion` as [moveTabToWorkspace] -
     * they ship together, so a host that has one has the other.
     *
     * @param tabId The tab to move, from [activeTabs].
     * @param targetWorkspaceId A workspace id from [liveWorkspaceIds].
     * @param targetPanelId A pane of that workspace, from [ActiveTabData.panelId].
     * @param targetIndex Position in that pane's tab list, or null to append.
     * @return true if the tab was moved.
     */
    suspend fun moveTabToPane(
        tabId: String,
        targetWorkspaceId: String,
        targetPanelId: String,
        targetIndex: Int? = null
    ): Boolean = false

    /**
     * The panel the user is working in, in this window, or null if the host cannot say.
     *
     * "Focused" in the tab-bar sense: of several panes on screen, the one a new tab would open in
     * and the one whose selected tab wears the accent marker. A pane in a workspace that is not on
     * screen is never this, however recently it was.
     */
    val activePanelId: String? get() = null

    /**
     * The tab currently selected in a pane - the one that pane is SHOWING - or null.
     *
     * Not derivable from [activeTabs]: that is a flat list of what exists, and every pane has
     * exactly one tab on top of it that the list does not mark. A pane in a workspace running
     * behind this one still answers, because it still has a selected tab; it just is not visible.
     *
     * Together with [activePanelId] this is what lets a caller draw the tab bar's two-strength
     * marker - full accent for the selected tab of the FOCUSED pane, a quieter one for the
     * selected tab of every other pane - rather than guessing or marking nothing.
     *
     * **[workspaceId] is required, and is not redundant.** A panel id is unique only WITHIN one
     * workspace's tree: every workspace's first pane is called `main`, so a lookup by panel id
     * alone would answer from whichever running workspace happened to be searched first and mark
     * the wrong row. Pass [ActiveTabData.workspaceId] and [ActiveTabData.panelId] from the same
     * tab.
     */
    fun selectedTabId(workspaceId: String, panelId: String): String? = null

    /**
     * Every tab in EVERY open window, where [activeTabs] is this window's alone.
     *
     * A separate member rather than widening [activeTabs], because the two answer different
     * questions and a panel needs both. A sidebar listing "what is running here" is scoped to its
     * own window - grouping another window's tabs under this one's workspaces would be wrong - but
     * a quick switcher is not: the tab you are reaching for may be in the window behind this one,
     * and a switcher that cannot see it is a switcher you stop trusting.
     *
     * Use [ActiveTabData.windowId] to tell them apart; it is populated for every entry.
     *
     * **The default is [activeTabs], not an empty list.** A host that cannot see other windows
     * then degrades to this window's tabs - narrower, but every entry still true - where an empty
     * default would make a switcher look broken. Compare against [activeTabs] if you need to know
     * whether a host really answered.
     */
    val allWindowTabs: StateFlow<List<ActiveTabData>> get() = activeTabs

    /**
     * Refresh [allWindowTabs] before reading it.
     *
     * [refreshTabs] covers this window only. Cross-window state is collected on demand rather than
     * pushed, so a caller opening a switcher should ask first instead of showing whatever the last
     * window to publish happened to leave behind.
     */
    suspend fun refreshAllWindowTabs() {
    }

    /**
     * What colour each Space is wearing, by workspace id.
     *
     * A BOSS theme belongs to a Space: entering one re-skins the whole app, and a Space that names
     * no theme of its own wears the one chosen in Settings. This is that decision, published - the
     * `signal` colour of the theme each Space resolves to, which is the same value
     * `BossColors.accent` reports for whichever Space is on screen.
     *
     * **A map, not a lookup function.** Three reasons, and the first is the requirement:
     *
     * - **It has to follow a live theme change.** A plain function is read once and nothing
     *   re-reads it, so a Space re-themed while a panel is up would keep its old tint until
     *   something else recomposed. Making the function `@Composable` would fix that and bind the
     *   answer to a composition, where a caller also needs it in ordinary code - a floor's
     *   receding faces are shaded off its front in plain arithmetic, and a row list is built
     *   before anything is drawn.
     * - **A [StateFlow] is what every other live value here already is** ([activeTabs],
     *   [allWindowTabs]), so a consumer collects it the way it collects those and an IPC proxy
     *   forwards it the way it forwards those.
     * - **A caller wants every Space at once, not one.** A panel drawing a header per running
     *   Space and a stack drawing all of them asks N times a frame for a value that changes about
     *   never; one collection answers the lot.
     *
     * **Keyed over every Space the host knows** - saved, running, and the layouts BOSS ships -
     * not only the current one, because the callers that need this are listing Spaces they are not
     * in. An id that is absent has no colour to offer and should be drawn untinted; never
     * substitute `BossColors.accent`, which is the colour of the Space you are LOOKING at and
     * would mark an unknown Space as the current one.
     *
     * The default is a permanently empty flow rather than a fresh one per read: a caller collects
     * this in a composition, and handing back a new instance each time would re-subscribe on every
     * recomposition. Empty means "this host does not theme Spaces", which is a tint nobody draws,
     * not a colour nobody can see.
     */
    val workspaceAccents: StateFlow<Map<String, Color>> get() = NO_WORKSPACE_ACCENTS
}

/**
 * The default [ActiveTabsProvider.workspaceAccents]: one shared, permanently empty flow.
 *
 * A top-level `val` rather than an expression in the getter, so every host that does not
 * implement the member hands back the same instance and a `collectAsState` over it settles.
 */
private val NO_WORKSPACE_ACCENTS: StateFlow<Map<String, Color>> = MutableStateFlow(emptyMap())

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
