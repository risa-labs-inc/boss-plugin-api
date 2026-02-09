package ai.rever.boss.plugin.api

import ai.rever.boss.plugin.ui.ContextMenuItemData
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Provider interface for context menu functionality in dynamic plugins.
 *
 * This interface allows dynamic plugins to display context menus using the
 * host application's native context menu implementation, ensuring consistent
 * styling and behavior across all plugins.
 *
 * Example usage in a plugin:
 * ```kotlin
 * @Composable
 * fun BookmarkItem(
 *     bookmark: Bookmark,
 *     contextMenuProvider: ContextMenuProvider?,
 *     onRemove: () -> Unit,
 *     onCopy: () -> Unit
 * ) {
 *     val items = listOf(
 *         ContextMenuItemData(label = "Remove", onClick = onRemove),
 *         ContextMenuItemData(label = "Copy to...", onClick = onCopy)
 *     )
 *
 *     Row(
 *         modifier = contextMenuProvider?.applyContextMenu(Modifier, items) ?: Modifier
 *     ) {
 *         // Item content
 *     }
 * }
 * ```
 */
interface ContextMenuProvider {
    /**
     * Apply context menu functionality to a modifier.
     *
     * When the user right-clicks (desktop) or long-presses (mobile) on the
     * element with this modifier, a context menu with the specified items
     * will be displayed.
     *
     * @param modifier The base modifier to extend with context menu functionality
     * @param items The list of menu items to display in the context menu
     * @return A new modifier with context menu functionality applied
     */
    @Composable
    fun applyContextMenu(
        modifier: Modifier,
        items: List<ContextMenuItemData>
    ): Modifier
}
