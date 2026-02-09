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
 */
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
     */
    fun addBookmark(collectionName: String, bookmark: Bookmark)

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
     */
    fun addTerminalTab(id: String, title: String, workingDirectory: String?)
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
