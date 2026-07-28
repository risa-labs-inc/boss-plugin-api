package ai.rever.boss.plugin.api

import ai.rever.boss.plugin.bookmark.Bookmark
import ai.rever.boss.plugin.bookmark.BookmarkCollection
import ai.rever.boss.plugin.bookmark.FavoriteWorkspace
import ai.rever.boss.plugin.workspace.LayoutWorkspace
import ai.rever.boss.plugin.workspace.TabConfig
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * Provider interface for bookmark management operations.
 *
 * This interface abstracts BookmarkManager functionality to allow
 * the Bookmarks panel to be extracted to a separate module.
 *
 * Implemented by the bookmarks plugin, but marked [HostImplemented] because the
 * host compiles this type in and serves it parent-first: the host's pinned copy
 * is what every plugin resolves, so a member change here ships with a
 * BossConsole release and must be gated on `minBossVersion`.
 */
@HostImplemented
interface BookmarkDataProvider {
    /**
     * All bookmark collections.
     */
    val collections: StateFlow<List<BookmarkCollection>>

    /**
     * Favorite workspaces.
     */
    val favoriteWorkspaces: StateFlow<List<FavoriteWorkspace>>

    // ==================== Bookmark Operations ====================

    /**
     * Add a bookmark to a collection.
     *
     * The reference implementation no-ops when no collection named
     * [collectionName] exists; behaviour is otherwise undefined, so call
     * [createCollection] first. Prefer [addBookmarks] when inserting more than
     * one at a time.
     */
    fun addBookmark(collectionName: String, bookmark: Bookmark)

    /**
     * Whether [addBookmarks] is implemented natively rather than falling back
     * to the per-item shim below.
     *
     * The shim is a real JVM default method, so it always resolves — a caller
     * cannot tell the two apart by catching [LinkageError], and reflecting on
     * the declaring class is unreliable (an override that delegates to `super`
     * looks native; `by` delegation and IPC proxies look native regardless).
     * Check this instead when the difference matters.
     *
     * **An override of [addBookmarks] that does not also override this is a
     * bug.** The flag cannot be derived from the presence of an override, so
     * forgetting it makes a perfectly good implementation report `false` and be
     * throttled for nothing.
     *
     * Two independent version axes decide what a caller sees:
     * - the member *existing* tracks the host — it lives in the host's
     *   parent-first copy, so on a host below 1.0.69 reading it throws
     *   `NoSuchMethodError` exactly as [addBookmarks] would;
     * - the value being `true` tracks the *bookmarks plugin*, which is what
     *   supplies the override.
     *
     * So this separates native from shim, never present from absent. Gate on
     * `minBossVersion` first, then consult it.
     *
     * @since 1.0.69
     */
    val supportsBulkAdd: Boolean get() = false

    /**
     * Add several bookmarks to a collection in a single operation, creating the
     * collection if it does not already exist.
     *
     * Contract for implementations:
     * - **Persist once for the whole batch.** The shim below does not; it
     *   exists only so implementations compiled against an earlier API stay
     *   binary compatible, and it inherits the per-item write amplification
     *   (and, before the atomic write landed, the risk of a torn save) that
     *   this method exists to avoid. Override it and set
     *   [supportsBulkAdd].
     * - **An empty list is a no-op** — it must not create the collection.
     * - **Entries are appended, not de-duplicated.** Callers importing from an
     *   external source are responsible for filtering entries they already
     *   have. [isTabBookmarked] encodes the usual field comparison, but takes a
     *   `TabConfig` (pass `bookmark.tabConfig`) and searches every collection,
     *   not just this one.
     * - **Callers must supply distinct ids.** `Bookmark.generateId()` is
     *   millisecond-based, so bookmarks built in a loop collide — and
     *   `removeBookmark`/`updateBookmark` match by id, so a collision makes one
     *   delete or rewrite all of its twins.
     * - Get-or-create is **not atomic** here. Call from a single thread, or
     *   make it atomic in the implementation.
     * - **Implementations should persist all-or-nothing.** The shim cannot: it
     *   forwards item by item, so a write that fails midway leaves a partial
     *   batch, and the caller sees `Unit` either way. An importer that needs to
     *   know how much landed must count from [collections] itself.
     * - **[collectionName] is matched exactly**, case included. Passing "work"
     *   where "Work" exists creates a second collection.
     *
     * Returns nothing deliberately: callers that need the resulting collection
     * should resolve it from [collections], which is also where the
     * duplicate-name ambiguity has to be handled anyway.
     *
     * Gate on `minBossVersion` before depending on this. `BookmarkDataProvider`
     * is compiled into the host and served parent-first, so on a host pinned
     * below 1.0.69 the method does not exist at all, whatever api jar the
     * plugin was built against.
     *
     * @since 1.0.69
     */
    fun addBookmarks(collectionName: String, bookmarks: List<Bookmark>) {
        // Matches the documented contract, and the override in the reference
        // implementation: an empty batch creates nothing.
        if (bookmarks.isEmpty()) return

        // Resolve through createCollection's return value rather than reading
        // `collections` back. Going via the flow would assume createCollection
        // publishes synchronously and stores the name verbatim; if either fails
        // to hold, every addBookmark below takes its documented no-op path and
        // the whole batch vanishes with no way for the caller to notice.
        val target =
            collections.value.firstOrNull { it.name == collectionName }
                ?: createCollection(collectionName)
        bookmarks.forEach { addBookmark(target.name, it) }
    }

    /**
     * Remove a bookmark from a collection.
     */
    fun removeBookmark(collectionId: String, bookmarkId: String)

    /**
     * Update a bookmark in a collection.
     */
    fun updateBookmark(collectionId: String, bookmark: Bookmark)

    /**
     * Move a bookmark from one collection to another.
     */
    fun moveBookmark(bookmarkId: String, fromCollectionId: String, toCollectionId: String)

    /**
     * Mark a bookmark as accessed (updates lastAccessedAt timestamp).
     */
    fun markBookmarkAsAccessed(collectionId: String, bookmarkId: String)

    /**
     * Check if a tab is already bookmarked in any collection.
     */
    fun isTabBookmarked(tabConfig: TabConfig): Boolean

    /**
     * Find which collection and bookmark ID contain this tab.
     * Returns Pair(collectionId, bookmarkId) or null if not found.
     */
    fun findBookmarkForTab(tabConfig: TabConfig): Pair<String, String>?

    // ==================== Collection Operations ====================

    /**
     * Create a new bookmark collection.
     */
    fun createCollection(name: String): BookmarkCollection

    /**
     * Delete a bookmark collection.
     */
    fun deleteCollection(collectionId: String)

    /**
     * Rename a bookmark collection.
     */
    fun renameCollection(collectionId: String, newName: String)

    // ==================== Favorite Workspace Operations ====================

    /**
     * Add a workspace to favorites.
     */
    fun addFavoriteWorkspace(workspaceId: String, workspaceName: String)

    /**
     * Remove a workspace from favorites.
     */
    fun removeFavoriteWorkspace(workspaceId: String)

    /**
     * Check if a workspace is favorited.
     */
    fun isFavorite(workspaceId: String): Boolean
}

/**
 * Provider interface for workspace management operations.
 *
 * This interface abstracts WorkspaceManager functionality to allow
 * the Bookmarks panel to be extracted to a separate module.
 */
interface WorkspaceDataProvider {
    /**
     * All available workspaces.
     */
    val workspaces: StateFlow<List<LayoutWorkspace>>

    /**
     * Current loaded workspace.
     */
    val currentWorkspace: StateFlow<LayoutWorkspace?>

    /**
     * Load a workspace (sets it as current).
     */
    fun loadWorkspace(workspace: LayoutWorkspace)

    /**
     * Update current workspace with new layout.
     */
    fun updateCurrentWorkspace(newWorkspace: LayoutWorkspace)

    /**
     * Save current workspace to disk.
     * @param name Optional name for the workspace (uses current name if null)
     * @return The saved workspace, or null if no current workspace
     */
    fun saveCurrentWorkspace(name: String?): LayoutWorkspace?

    /**
     * Export workspace to JSON.
     */
    fun exportWorkspace(workspace: LayoutWorkspace): String

    /**
     * Delete a workspace.
     */
    fun deleteWorkspace(name: String)

    /**
     * Rename a workspace.
     */
    fun renameWorkspace(oldName: String, newName: String)
}

/**
 * Provider interface for split view operations (tab management).
 *
 * This interface abstracts SplitViewState functionality to allow
 * the Bookmarks panel to open tabs without direct coupling to SplitViewState.
 */
/**
 * Where [SplitViewOperations.openTabInSplit] places a tab, mirroring the host's
 * terminal-link chooser:
 * - [EXISTING_SPLIT]: reuse another already-open split pane (falls back to a new
 *   vertical split if there is none).
 * - [VERTICAL_SPLIT] / [HORIZONTAL_SPLIT]: create a new split of the active panel
 *   in that orientation and place the tab there.
 */
enum class TabSplitMode { EXISTING_SPLIT, VERTICAL_SPLIT, HORIZONTAL_SPLIT }

interface SplitViewOperations {
    /**
     * Open a URL in the active panel.
     */
    fun openUrlInActivePanel(url: String, title: String, forceNewTab: Boolean = false)

    /**
     * Open a file in the active panel.
     */
    fun openFileInActivePanel(filePath: String, fileName: String)

    /**
     * Open a file in the browser tab (for images, PDFs, etc.).
     * Note: file:// URL construction here must stay in sync with
     * SplitViewState.toFileUrl() for duplicate-tab detection to work.
     */
    fun openFileInBrowser(filePath: String, fileName: String) {
        openUrlInActivePanel(java.io.File(filePath).toURI().toString(), fileName)
    }

    /**
     * Force-open a file in the code editor, bypassing smart file routing.
     * Unlike [openFileInActivePanel], this always creates an editor tab
     * even for browser-renderable file types (images, PDFs, etc.).
     *
     * Implementors must route to a non-smart-routing path (e.g. openFileInEditorTab).
     */
    fun openFileInEditor(filePath: String, fileName: String)

    /**
     * Open a file in the active panel and navigate to a specific position.
     * This is used for code navigation (go-to-definition, find usages).
     *
     * @param filePath Absolute path to the file
     * @param fileName Display name for the tab
     * @param line Target line number (1-based)
     * @param column Target column number (1-based)
     */
    fun openFileAtPosition(filePath: String, fileName: String, line: Int, column: Int)

    /**
     * Set the active panel.
     */
    fun setActivePanel(panelId: String)

    /**
     * Preserve current state before switching workspaces.
     */
    fun preserveCurrentState(workspaceId: String, workspaceName: String)

    /**
     * Get the active tabs component for adding tabs programmatically.
     * Returns an object that can add tabs, or null if unavailable.
     */
    fun getActiveTabsComponent(): TabsComponent?

    /**
     * Apply a workspace layout.
     */
    fun applyWorkspace(workspace: LayoutWorkspace)

    /**
     * Select a specific tab in a specific panel.
     * Used by TopOfMind panel to focus tabs.
     */
    fun selectTabInPanel(tabId: String, panelId: String)

    /**
     * Open a new tab of any registered type in the active panel.
     *
     * The [tabInfo]'s [TabInfo.typeId] is used to look up the registered tab
     * factory (see [TabRegistry]), so a plugin can open a tab of a type it
     * registered without the host needing to know about it. The default
     * implementation is a no-op so existing implementors are unaffected.
     *
     * In-process plugins only: the IPC/out-of-process proxy doesn't forward this,
     * so for sandboxed/out-of-process plugins it is a no-op.
     *
     * @param tabInfo The configuration describing the tab to open.
     */
    fun openTab(tabInfo: TabInfo) {}

    /**
     * Open a registered tab type into a SPLIT of the active panel instead of a
     * new tab in it — the split half of the host's "new tab vs split" chooser.
     * Like [openTab], the [tabInfo]'s [TabInfo.typeId] selects the registered
     * factory, so a plugin can place e.g. a terminal beside the current content.
     *
     * Default no-op so older hosts are unaffected — gate on minBossVersion when
     * you depend on it (pair with a fallback to [openTab] if it silently no-ops).
     *
     * @param tabInfo The configuration describing the tab to open.
     * @param mode Where to place it (see [TabSplitMode]).
     */
    fun openTabInSplit(tabInfo: TabInfo, mode: TabSplitMode) {}

    /**
     * Open a URL into a SPLIT of the active panel (browser tab) — the URL
     * analogue of [openTabInSplit]; [openUrlInActivePanel] covers the new-tab
     * case. Default no-op so older hosts are unaffected.
     */
    fun openUrlInSplit(url: String, title: String, mode: TabSplitMode) {}
}

/**
 * Interface for adding tabs to a panel programmatically.
 */
interface TabsComponent {
    /**
     * Add a terminal tab.
     * @param id Unique ID for the tab
     * @param title Display title
     * @param workingDirectory Optional working directory
     * @param initialCommand Optional command to run when terminal starts
     */
    fun addTerminalTab(id: String, title: String, workingDirectory: String?, initialCommand: String? = null)
}

// ==================== Composition Locals ====================

/**
 * CompositionLocal for providing SplitViewOperations to panels.
 * Must be provided by the window-level composition.
 */
val LocalSplitViewOperations = staticCompositionLocalOf<SplitViewOperations?> { null }

/**
 * CompositionLocal for providing BookmarkDataProvider to panels.
 * Must be provided at the application level.
 */
val LocalBookmarkDataProvider = staticCompositionLocalOf<BookmarkDataProvider?> { null }

/**
 * CompositionLocal for providing WorkspaceDataProvider to panels.
 * Must be provided at the application level.
 */
val LocalWorkspaceDataProvider = staticCompositionLocalOf<WorkspaceDataProvider?> { null }

/**
 * CompositionLocal for providing the current project path.
 * Must be provided at the window level.
 */
val LocalProjectPath = staticCompositionLocalOf { "" }
