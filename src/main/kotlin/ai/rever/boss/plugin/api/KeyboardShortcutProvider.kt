package ai.rever.boss.plugin.api

/**
 * Provider interface for querying keyboard shortcuts.
 *
 * Provides read-only access to the application's keyboard shortcuts.
 * Plugins can use this to display shortcut information or check key bindings.
 */
interface KeyboardShortcutProvider {

    /**
     * Get all registered keyboard shortcuts.
     *
     * @return List of keyboard shortcut descriptors
     */
    fun getShortcuts(): List<KeyboardShortcutInfo>

    /**
     * Get keyboard shortcuts filtered by category.
     *
     * @param category The category to filter by
     * @return List of matching shortcuts
     */
    fun getShortcutsByCategory(category: String): List<KeyboardShortcutInfo>

    /**
     * Check if the current platform is macOS (affects modifier key display).
     * @return true if running on macOS
     */
    fun isMacOS(): Boolean
}

/**
 * Information about a keyboard shortcut.
 */
data class KeyboardShortcutInfo(
    /** Action name (e.g., "New Window", "Close Tab"). */
    val action: String,
    /** Key name (e.g., "N", "W", "Space"). */
    val key: String,
    /** Modifier keys (e.g., ["Cmd"], ["Cmd", "Shift"]). */
    val modifiers: List<String>,
    /** Category grouping (e.g., "Window Management", "Tab Management"). */
    val category: String,
    /** Human-readable description of the shortcut. */
    val description: String
)
