package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Plugin API exposed by the terminal-tab plugin for other plugins and the host to consume.
 *
 * This follows the same pattern as BookmarkDataProvider: the terminal-tab plugin owns
 * the terminal logic, registers this API via registerPluginAPI(), and consumers access
 * it via getPluginAPI(TerminalTabPluginAPI::class.java).
 *
 * The terminal panel plugin and BossConsole host (via TerminalAPIAccess) both use this API.
 */
interface TerminalTabPluginAPI {

    // ============================================================
    // COMPOSABLE RENDERING
    // ============================================================

    /**
     * Render a persistent tabbed terminal for a specific terminal tab.
     *
     * @param terminalId Unique ID for this terminal instance, used as key in state registry
     * @param initialCommand Optional command to run after terminal starts (only for new terminals)
     * @param workingDirectory Optional working directory for the terminal
     * @param onExit Called when the last terminal tab is closed
     * @param onShowSettings Called when user requests settings
     * @param onTitleChange Called when terminal window title changes via escape sequences
     * @param onLinkClick Optional callback for hyperlink handling (url, linkType) -> handled
     */
    @Composable
    fun PersistentTabbedTerminalContent(
        terminalId: String,
        initialCommand: String? = null,
        workingDirectory: String? = null,
        onExit: () -> Unit = {},
        onShowSettings: () -> Unit = {},
        onTitleChange: ((String) -> Unit)? = null,
        onLinkClick: ((url: String, linkType: String) -> Boolean)? = null
    )

    /**
     * Render tabbed terminal content for the sidebar panel.
     *
     * @param workingDirectory Optional working directory for the terminal
     * @param onExit Called when the last terminal tab is closed
     * @param onShowSettings Called when user requests settings
     */
    @Composable
    fun TabbedTerminalContent(
        workingDirectory: String? = null,
        onExit: () -> Unit = {},
        onShowSettings: () -> Unit = {}
    )

    /**
     * Render a single embedded terminal.
     *
     * @param terminalId Optional unique ID for persistent state
     * @param initialCommand Optional command to run after terminal starts
     * @param workingDirectory Optional working directory for the terminal
     * @param onExit Called when terminal process exits
     */
    @Composable
    fun TerminalContent(
        terminalId: String? = null,
        initialCommand: String? = null,
        workingDirectory: String? = null,
        onExit: () -> Unit = {}
    )

    // ============================================================
    // STATE MANAGEMENT
    // ============================================================

    /**
     * Check if a terminal state exists for the given window and terminal ID.
     */
    fun hasTerminalState(windowId: String, terminalId: String): Boolean

    /**
     * Remove a terminal state for the given window and terminal ID.
     */
    fun removeTerminalState(windowId: String, terminalId: String)

    /**
     * Remove all terminal states for a specific window.
     * Called when a window is closed.
     *
     * @return Number of terminal states that were disposed
     */
    fun removeAllForWindow(windowId: String): Int

    /**
     * Reset all terminal states across all registries.
     * Called by "Reset Terminal" in Help menu.
     *
     * @return Total number of terminal states disposed
     */
    fun resetAllTerminals(): Int

    // ============================================================
    // TERMINAL CONTROL
    // ============================================================

    /**
     * Send a command to a running terminal.
     *
     * @param windowId The window ID containing the terminal
     * @param terminalId The terminal ID to send the command to
     * @param command The command to execute (will be followed by newline)
     * @return true if the command was sent successfully
     */
    fun sendCommand(windowId: String, terminalId: String, command: String): Boolean

    /**
     * Send an interrupt signal (Ctrl+C) to a terminal.
     *
     * @param windowId The window ID containing the terminal
     * @param terminalId The terminal ID to interrupt
     * @return true if the interrupt was sent successfully
     */
    fun sendInterrupt(windowId: String, terminalId: String): Boolean

    /**
     * Send raw input bytes to a terminal.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID to send input to
     * @param bytes The bytes to send
     * @return true if the terminal exists and input was sent
     */
    fun sendInput(windowId: String, terminalId: String, bytes: ByteArray): Boolean

    /**
     * Close the active tab in a terminal.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID to close the active tab in
     * @return true if the terminal exists and tab was closed
     */
    fun closeActiveTab(windowId: String, terminalId: String): Boolean

    // ============================================================
    // SIDEBAR TERMINAL OPERATIONS
    // ============================================================

    /**
     * Open or reuse a sidebar terminal tab.
     *
     * @param windowId The window ID
     * @param command The command to run
     * @param workingDirectory Optional working directory
     * @param configId Optional configuration ID for runner tracking
     * @param isRerun If true, sends Ctrl+C first to stop running process
     * @return true if command was sent successfully
     */
    fun newSidebarTab(
        windowId: String,
        command: String,
        workingDirectory: String? = null,
        configId: String? = null,
        isRerun: Boolean = false
    ): Boolean

    /**
     * Register a tab ID for a config after the first tab is created.
     *
     * @param windowId The window ID
     * @param configId The configuration ID
     * @param tabId The terminal tab ID
     */
    fun registerSidebarTabId(windowId: String, configId: String, tabId: String)

    /**
     * Remove tab tracking for a config.
     *
     * @param windowId The window ID
     * @param configId The configuration ID
     */
    fun removeSidebarConfigTracking(windowId: String, configId: String)

    /**
     * Clear all sidebar config tracking for a specific window.
     *
     * @param windowId The window ID
     */
    fun clearSidebarConfigTrackingForWindow(windowId: String)

    /**
     * Get the config ID for a sidebar tab ID (reverse lookup).
     *
     * @param windowId The window ID
     * @param tabId The terminal tab ID
     * @return The config ID, or null if not found
     */
    fun getConfigIdForSidebarTab(windowId: String, tabId: String): String?

    // ============================================================
    // PENDING SIDEBAR COMMANDS
    // ============================================================

    /**
     * Set a pending command to run when the sidebar terminal panel opens.
     *
     * @param windowId The window ID
     * @param command The command to run
     * @param workingDirectory Optional working directory
     * @param configId Optional configuration ID for runner tracking
     */
    fun setPendingSidebarCommand(windowId: String, command: String, workingDirectory: String?, configId: String? = null)

    /**
     * Get and clear the pending command for a specific window.
     *
     * @param windowId The window ID
     * @return The pending command info (command, workingDirectory, configId), or null if none
     */
    fun consumePendingSidebarCommand(windowId: String): PendingSidebarCommand?

    // ============================================================
    // OBSERVABLE STATE
    // ============================================================

    /**
     * Generation counter that increments on each reset operation.
     * Composables can observe this to trigger recomposition when terminals are reset.
     */
    val resetGeneration: StateFlow<Int>

    // ============================================================
    // SETTINGS & ONBOARDING
    // ============================================================

    /**
     * Render the terminal settings panel.
     * Default no-op so existing plugin JARs don't break.
     */
    @Composable
    fun TerminalSettingsPanel(modifier: Modifier) {}

    /**
     * Render the terminal onboarding wizard dialog.
     * Default no-op so existing plugin JARs don't break.
     */
    @Composable
    fun TerminalOnboardingWizard(onDismiss: () -> Unit, onComplete: () -> Unit) {}

    // ============================================================
    // TERMINAL SETTINGS MANAGEMENT
    // ============================================================

    /**
     * Get the current terminal font size.
     * @return Font size in points, or -1 if not available
     */
    fun getTerminalFontSize(): Float = -1f

    /**
     * Set the terminal font size.
     *
     * @param size Font size in points
     * @return true if the font size was set successfully
     */
    fun setTerminalFontSize(size: Float): Boolean = false

    /**
     * Get the current terminal font family name.
     * @return Font family name, or empty string if not available
     */
    fun getTerminalFontFamily(): String = ""

    /**
     * Set the terminal font family.
     *
     * @param family Font family name
     * @return true if the font family was set successfully
     */
    fun setTerminalFontFamily(family: String): Boolean = false

    /**
     * Check if terminal onboarding has been completed.
     * @return true if onboarding is completed, true by default for backward compatibility
     */
    fun isOnboardingCompleted(): Boolean = true

    // ============================================================
    // TERMINAL TAB MANAGEMENT
    // ============================================================

    /**
     * List all tabs in a terminal instance.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @return List of tab information, or empty list if terminal not found
     */
    fun listTabs(windowId: String, terminalId: String): List<TerminalTabInfo> = emptyList()

    /**
     * Switch to a specific tab by its ID.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @param tabId The tab ID to switch to
     * @return true if the tab was found and switched to
     */
    fun switchToTab(windowId: String, terminalId: String, tabId: String): Boolean = false

    /**
     * Get the index of the currently active tab.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @return The active tab index (0-based), or -1 if terminal not found
     */
    fun getActiveTabIndex(windowId: String, terminalId: String): Int = -1

    /**
     * Get the number of tabs in a terminal instance.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @return The tab count, or 0 if terminal not found
     */
    fun getTabCount(windowId: String, terminalId: String): Int = 0

    /**
     * Create a new terminal tab programmatically.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID of the tabbed terminal instance
     * @param workingDirectory Optional working directory for the new tab
     * @param initialCommand Optional command to run after the tab starts
     * @return The tab ID of the newly created tab, or null if creation failed
     */
    fun createTab(
        windowId: String,
        terminalId: String,
        workingDirectory: String? = null,
        initialCommand: String? = null
    ): String? = null

    /**
     * Get the working directory of the active terminal tab.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @return The working directory path, or null if not available
     */
    fun getActiveWorkingDirectory(windowId: String, terminalId: String): String? = null

    // ============================================================
    // SHELL UTILITIES
    // ============================================================

    /**
     * Get the default shell for the current platform.
     * @return The shell path (e.g., "/bin/zsh", "/bin/bash", "cmd.exe")
     */
    fun getDefaultShell(): String = ""

    /**
     * Check if the current platform is Windows.
     * @return true if running on Windows
     */
    fun isWindows(): Boolean = false

    // ============================================================
    // SPLIT PANE MANAGEMENT (T6)
    // ============================================================

    /**
     * Split the focused pane vertically (left/right) in the specified tab.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @param tabId Optional tab ID (null = active tab)
     * @return Session ID of the new pane, or null if split failed
     */
    fun splitVertical(windowId: String, terminalId: String, tabId: String? = null): String? = null

    /**
     * Split the focused pane horizontally (top/bottom) in the specified tab.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @param tabId Optional tab ID (null = active tab)
     * @return Session ID of the new pane, or null if split failed
     */
    fun splitHorizontal(windowId: String, terminalId: String, tabId: String? = null): String? = null

    /**
     * Close the focused pane in the specified tab.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @param tabId Optional tab ID (null = active tab)
     * @return true if pane was closed
     */
    fun closeFocusedPane(windowId: String, terminalId: String, tabId: String? = null): Boolean = false

    /**
     * Get the number of panes in the specified tab.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @param tabId Optional tab ID (null = active tab)
     * @return Pane count (1 if no splits, 0 if tab not found)
     */
    fun getPaneCount(windowId: String, terminalId: String, tabId: String? = null): Int = 0

    /**
     * Check if the specified tab has split panes.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @param tabId Optional tab ID (null = active tab)
     * @return true if tab has split panes
     */
    fun hasSplitPanes(windowId: String, terminalId: String, tabId: String? = null): Boolean = false

    /**
     * Write text to the focused pane in the specified tab.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @param text The text to write
     * @param tabId Optional tab ID (null = active tab)
     * @return true if text was written
     */
    fun writeToFocusedPane(windowId: String, terminalId: String, text: String, tabId: String? = null): Boolean = false

    // ============================================================
    // REACTIVE STATE (T7)
    // ============================================================

    /**
     * Get a reactive flow of terminal tab information.
     * Updates whenever tabs are added/removed/renamed or connection state changes.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @return StateFlow of tab info list, or null if terminal not found
     */
    fun getTabsFlow(windowId: String, terminalId: String): Flow<List<TerminalTabInfo>>? = null

    /**
     * Get a reactive flow of the active tab index.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @return StateFlow of active tab index, or null if terminal not found
     */
    fun getActiveTabIndexFlow(windowId: String, terminalId: String): Flow<Int>? = null
}

/**
 * Information about a terminal tab.
 */
data class TerminalTabInfo(
    /** Unique identifier of the tab. */
    val id: String,
    /** Display title of the tab. */
    val title: String,
    /** Whether this tab is currently active/selected. */
    val isActive: Boolean,
    /** Zero-based index of the tab. */
    val index: Int
)

/**
 * Structured information about a terminal hyperlink.
 * Mirrors BossTerm's HyperlinkInfo with plugin-API-safe types.
 */
data class TerminalHyperlinkInfo(
    /** The URL or path of the hyperlink. */
    val url: String,
    /** The type of hyperlink. */
    val type: TerminalHyperlinkType,
    /** The matched text in the terminal output. */
    val matchedText: String = "",
    /** Whether this link points to a file. */
    val isFile: Boolean = false,
    /** Whether this link points to a folder. */
    val isFolder: Boolean = false,
    /** The URL scheme (e.g., "http", "file"). */
    val scheme: String = ""
)

/**
 * Type of terminal hyperlink.
 */
enum class TerminalHyperlinkType {
    HTTP,
    FILE,
    FOLDER,
    EMAIL,
    FTP,
    CUSTOM
}

/**
 * Data class holding pending sidebar command info.
 * Set before opening the panel so TabbedTerminal can use it on first render.
 */
data class PendingSidebarCommand(
    val command: String,
    val workingDirectory: String?,
    val configId: String? = null
)

/**
 * ID for the sidebar terminal panel's persistent state.
 */
const val SIDEBAR_TERMINAL_ID = "sidebar-terminal"
