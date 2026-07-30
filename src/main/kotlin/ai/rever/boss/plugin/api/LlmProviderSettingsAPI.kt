package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plugin API for the plugin that owns AI provider configuration — the provider
 * list, credentials, environment-variable resolution, and the live model catalog.
 *
 * Same pattern as [EditorTabPluginAPI]: the owning plugin registers this via
 * registerPluginAPI(), and consumers access it via
 * getPluginAPI(LlmProviderSettingsAPI::class.java). The BossConsole host consumes
 * it through LlmProviderAPIAccess to render Settings → AI Providers and to back
 * [PluginContext.llmProvider].
 *
 * It extends [LlmProvider] on purpose: the plugin is already the authority on
 * which provider is active and what credential it carries, so the host can hand
 * the registered instance straight to other plugins as [PluginContext.llmProvider]
 * rather than maintaining a parallel copy of that state.
 *
 * Consumers gate on `minApiVersion: 1.0.70` for the interface itself. Note that
 * [LlmApiFormat.GOOGLE_GENERATIVE], added in the same release, additionally needs
 * `minBossVersion` — see [LlmApiFormat].
 *
 * [LlmProviderSettingsPanel] has a default no-op so a plugin built against a later
 * api that adds members here keeps loading on an older host, and so a host that
 * gains a new bridge degrades rather than failing. Since the interface is new, that
 * buys forward compatibility rather than protecting any existing implementation.
 */
@HostImplemented
interface LlmProviderSettingsAPI : LlmProvider {

    /**
     * Whether [LlmProviderSettingsPanel] actually renders something.
     *
     * The default no-op panel means the host cannot otherwise tell "this plugin has
     * no settings UI" from "this plugin drew a blank page" — it would compose nothing
     * and show an empty Settings section with no explanation. An implementation that
     * overrides the panel should override this to true so the host can fall back to an
     * explanatory empty state instead.
     *
     * Same shape as [FileSystemDataProvider.supportsHiddenEntries] and
     * `BookmarkDataProvider.supportsBulkAdd`.
     */
    val supportsSettingsPanel: Boolean get() = false

    /**
     * Render the AI provider settings panel — provider selection, credentials,
     * model picker, and connection status.
     *
     * Rendered by the host inside its Settings window. Default no-op; gate on
     * [supportsSettingsPanel] before relying on it.
     */
    @Composable
    fun LlmProviderSettingsPanel(modifier: Modifier) {}
}
