package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable

/**
 * Provider interface for terminal content - platform-specific implementation.
 * This allows the terminal panel to be loaded as a dynamic plugin.
 */
interface TerminalContentProvider {
    /**
     * Display tabbed terminal content.
     */
    @Composable
    fun TabbedTerminalContent(
        workingDirectory: String? = null,
        onExit: () -> Unit = {},
        onShowSettings: () -> Unit = {}
    )

    /**
     * Reset all terminal states.
     */
    fun resetTerminals()
}

/**
 * Provider interface for tab-specific terminal rendering.
 * This allows terminal tabs to be loaded as a dynamic plugin.
 *
 * Unlike TerminalContentProvider which is for the sidebar terminal panel,
 * this interface provides persistent terminal content for individual tab instances.
 */
interface TerminalTabContentProvider {
    /**
     * Display persistent tabbed terminal content for a specific terminal tab.
     *
     * @param terminalId Unique ID for this terminal instance, used as key in state registry
     * @param initialCommand Optional command to run after terminal starts (only for new terminals)
     * @param workingDirectory Optional working directory for the terminal
     * @param onExit Called when the last terminal tab is closed
     * @param onShowSettings Callback when settings should be shown
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
     * Check if a terminal state exists for the given window and terminal ID.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     * @return true if the terminal state exists
     */
    fun hasTerminalState(windowId: String, terminalId: String): Boolean

    /**
     * Remove a terminal state for the given window and terminal ID.
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID
     */
    fun removeTerminalState(windowId: String, terminalId: String)

    // ============ Phase 1: Terminal Control APIs ============

    /**
     * Send a command to a running terminal.
     *
     * @param windowId The window ID containing the terminal
     * @param terminalId The terminal ID to send the command to
     * @param command The command to execute (will be followed by newline)
     * @return true if the command was sent successfully, false otherwise
     */
    fun sendCommand(windowId: String, terminalId: String, command: String): Boolean = false

    /**
     * Send an interrupt signal (Ctrl+C) to a terminal.
     *
     * @param windowId The window ID containing the terminal
     * @param terminalId The terminal ID to interrupt
     * @return true if the interrupt was sent successfully, false otherwise
     */
    fun sendInterrupt(windowId: String, terminalId: String): Boolean = false

    /**
     * Request to close a terminal tab programmatically.
     * This will trigger the onExit callback if the terminal is closed.
     *
     * @param windowId The window ID containing the terminal
     * @param terminalId The terminal ID to close
     */
    fun requestCloseTab(windowId: String, terminalId: String) {}

    /**
     * Remove all terminal states for a window (cleanup on window close).
     *
     * @param windowId The window ID to cleanup
     */
    fun removeAllForWindow(windowId: String) {}

    // ============ Phase 2: Runner Terminal Integration ============

    /**
     * Check if this terminal is a runner terminal (used for run configurations).
     *
     * @param terminalId The terminal ID to check
     * @return true if this is a runner terminal
     */
    fun isRunnerTerminal(terminalId: String): Boolean = terminalId.startsWith("runner-")

    /**
     * Mark a runner terminal as stopped (process exited).
     * This is called when the terminal process exits.
     *
     * @param terminalId The terminal ID that stopped
     */
    fun markRunnerTerminalStopped(terminalId: String) {}

    /**
     * Get the run configuration ID associated with a runner terminal.
     *
     * @param terminalId The terminal ID
     * @return The run configuration ID, or null if not a runner terminal
     */
    fun getRunConfigurationId(terminalId: String): String? = null

    // ============ Phase 2: Terminal Settings ============

    /**
     * Get the current shell path.
     * @return The path to the current shell executable
     */
    fun getShellPath(): String = "/bin/zsh"

    /**
     * Set the shell path.
     *
     * @param path The path to the shell executable
     */
    fun setShellPath(path: String) {}

    /**
     * Get the list of available shells.
     * @return List of shell paths
     */
    fun getAvailableShells(): List<String> = listOf("/bin/zsh", "/bin/bash", "/bin/sh")

    // ============ Phase 3: Split Pane Support ============

    /**
     * Create a split terminal pane (horizontal split).
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID to split from
     * @return The new terminal ID, or null if split failed
     */
    fun createHorizontalSplit(windowId: String, terminalId: String): String? = null

    /**
     * Create a split terminal pane (vertical split).
     *
     * @param windowId The window ID
     * @param terminalId The terminal ID to split from
     * @return The new terminal ID, or null if split failed
     */
    fun createVerticalSplit(windowId: String, terminalId: String): String? = null

    // ============ Phase 4: Reset and Keyboard Context ============

    /**
     * Get the current reset generation counter.
     * This counter increments when terminals are reset, allowing UI to observe
     * and recompose when terminals need to be recreated.
     *
     * @return The current reset generation counter value
     */
    fun getResetGeneration(): Int = 0

    /**
     * Get the keyboard shortcut context identifier for terminal.
     * Used for context-specific keyboard shortcuts.
     *
     * @return The shortcut context string (e.g., "TERMINAL")
     */
    fun getShortcutContext(): String = "TERMINAL"
}

/**
 * Provider interface for panel events.
 * Allows plugins to trigger panel operations.
 */
interface PanelEventProvider {
    /**
     * Close the panel.
     */
    suspend fun closePanel(panelId: PanelId, windowId: String)
}

/**
 * Provider interface for opening settings.
 * Allows plugins to open the settings dialog.
 */
interface SettingsProvider {
    /**
     * Open settings at specific section.
     */
    fun openSettings(windowId: String, section: String)
}

/**
 * Provider interface for the Boss Console dashboard content.
 * Allows browser plugins to display the host's dashboard for about:blank pages.
 */
interface DashboardContentProvider {
    /**
     * Display the Boss Console dashboard.
     *
     * @param onNavigate Callback when user wants to navigate to a URL (from search or quick links)
     */
    @Composable
    fun DashboardContent(
        onNavigate: (String) -> Unit
    )
}
