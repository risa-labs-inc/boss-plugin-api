package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.SharedFlow

/**
 * Navigation target event for cursor positioning after file opens.
 *
 * @property filePath Absolute path to the file
 * @property line Line number (1-based)
 * @property column Column number (1-based)
 * @property sourceWindowId The window that initiated the navigation
 */
data class NavigationTargetEvent(
    val filePath: String,
    val line: Int,
    val column: Int,
    val sourceWindowId: String
)

/**
 * Provider for navigation target events.
 *
 * This allows plugins to listen for navigation events and position their
 * editor cursors appropriately. When a file is opened via navigation
 * (go-to-definition, find usages), this provider emits events that
 * editors can use to position the cursor at the target location.
 *
 * Usage in a plugin editor:
 * ```kotlin
 * LaunchedEffect(filePath, windowId) {
 *     context.navigationTargetProvider?.targets?.collect { target ->
 *         if (target.sourceWindowId == windowId && target.filePath == filePath) {
 *             val line = (target.line - 1).coerceAtLeast(0)
 *             val column = (target.column - 1).coerceAtLeast(0)
 *             editorState.moveCaret(EditorPosition(line, column))
 *             editorState.clearSelection()
 *             editorState.scrollToLine(line, lineHeight, viewportHeight)
 *             context.navigationTargetProvider?.clearCache()
 *         }
 *     }
 * }
 * ```
 */
interface NavigationTargetProvider {
    /**
     * Flow of navigation target events.
     * Subscribe to this to receive cursor positioning events.
     */
    val targets: SharedFlow<NavigationTargetEvent>

    /**
     * Clear the replay cache after handling a navigation event.
     * Call this to avoid stale targets being replayed to new editors.
     */
    fun clearCache()
}
