package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
}
