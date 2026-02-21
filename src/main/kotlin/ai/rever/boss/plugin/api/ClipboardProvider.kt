package ai.rever.boss.plugin.api

/**
 * Provider interface for clipboard operations.
 *
 * Plugins cannot directly access AWT clipboard due to classloader isolation.
 * This provider bridges the gap by executing clipboard operations in the host context.
 */
interface ClipboardProvider {

    /**
     * Read text content from the system clipboard.
     *
     * @return The clipboard text content, or null if clipboard is empty or doesn't contain text
     */
    fun readText(): String?

    /**
     * Set text content on the system clipboard.
     *
     * @param text The text to place on the clipboard
     * @return true if the text was successfully placed on the clipboard
     */
    fun setText(text: String): Boolean

    /**
     * Check if the system clipboard contains text content.
     *
     * @return true if the clipboard has text content available
     */
    fun hasText(): Boolean
}
