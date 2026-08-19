package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/**
 * Plugin API exposed by the editor-tab plugin for the host (and other plugins) to consume.
 *
 * Same pattern as [TerminalTabPluginAPI]: the editor-tab plugin owns the editor stack
 * (BossEditor is bundled privately inside the plugin JAR), registers this API via
 * registerPluginAPI(), and consumers access it via
 * getPluginAPI(EditorTabPluginAPI::class.java).
 *
 * The BossConsole host consumes it through EditorAPIAccess.
 *
 * Every method has a default implementation so plugin JARs built against older
 * API versions keep loading, and hosts degrade gracefully when the installed
 * plugin predates a method.
 */
interface EditorTabPluginAPI {

    // ============================================================
    // SETTINGS PANELS
    // ============================================================

    /**
     * Render the editor settings panel (font, theme, editing behavior, …).
     * Default no-op.
     */
    @Composable
    fun EditorSettingsPanel(modifier: Modifier) {}

    /**
     * Render the LSP / language-server settings panel.
     * Default no-op.
     */
    @Composable
    fun LspSettingsPanel(modifier: Modifier) {}

    // ============================================================
    // AUTO SAVE
    // ============================================================

    /**
     * Whether the editor saves modified files automatically, as an observable the host can
     * render a checked state from.
     *
     * The setting belongs to the editor rather than the host: the host has no editor state to
     * apply it to, and `editor-settings.json` is rewritten wholesale by the editor's own
     * settings manager, so a host-owned copy there would be erased on the next settings change.
     *
     * Null when the installed editor-tab plugin predates this method, which is how a host built
     * against a newer API tells that it should hide the control rather than show a dead one.
     */
    fun autoSaveEnabled(): StateFlow<Boolean>? = null

    /**
     * Turn auto save on or off. No-op on plugins that predate this method - pair it with
     * [autoSaveEnabled] returning non-null before offering the control.
     */
    fun setAutoSaveEnabled(enabled: Boolean) {}
}
