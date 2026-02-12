package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

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
