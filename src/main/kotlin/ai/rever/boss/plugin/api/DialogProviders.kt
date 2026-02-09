package ai.rever.boss.plugin.api

import ai.rever.boss.plugin.bookmark.BookmarkCollection
import ai.rever.boss.plugin.workspace.LayoutWorkspace
import androidx.compose.runtime.Composable

/**
 * Provider interface for Bookmarks dialog composables.
 *
 * This interface abstracts dialog UI to allow the Bookmarks panel
 * to be extracted to a separate module while keeping the dialogs
 * in the host application.
 */
interface BookmarksDialogProvider {
    @Composable
    fun NewCollectionDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit)

    @Composable
    fun NewWorkspaceDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit)

    @Composable
    fun ConfirmationDialog(
        title: String,
        message: String,
        confirmText: String,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    )

    @Composable
    fun RemoveBookmarkConfirmationDialog(
        bookmarkTitle: String,
        onDismiss: () -> Unit,
        onConfirm: () -> Unit
    )

    @Composable
    fun CollectionSelectionDialog(
        title: String,
        collections: List<BookmarkCollection>,
        excludeCollectionId: String,
        isMoveMode: Boolean,
        onDismiss: () -> Unit,
        onConfirm: (List<String>) -> Unit
    )

    @Composable
    fun WorkspaceSelectionDialog(
        title: String,
        workspaces: List<LayoutWorkspace>,
        preselectedWorkspaces: Map<String, String?>,
        onDismiss: () -> Unit,
        onConfirm: (Map<String, String?>) -> Unit
    )

    @Composable
    fun RenameDialog(
        title: String,
        currentName: String,
        label: String,
        onDismiss: () -> Unit,
        onRename: (String) -> Unit
    )

    @Composable
    fun BookmarkDialog(
        tabTitle: String,
        collections: List<BookmarkCollection>,
        workspaces: List<LayoutWorkspace>,
        onDismiss: () -> Unit,
        onConfirm: (List<String>, Map<String, String?>) -> Unit
    )
}

/**
 * Provider interface for Fluck panel content.
 *
 * This interface abstracts the browser-based content rendering
 * to allow the Fluck panel to be extracted to a separate module.
 */
interface FluckPanelContentProvider {
    /**
     * Render the Fluck panel content (browser or error view).
     */
    @Composable
    fun FluckPanelContent()
}
