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
 * [LlmProviderSettingsPanel] has a default no-op so plugin JARs built against
 * older API versions keep loading and hosts degrade gracefully when the installed
 * plugin predates the method.
 */
interface LlmProviderSettingsAPI : LlmProvider {

    /**
     * Render the AI provider settings panel — provider selection, credentials,
     * model picker, and connection status.
     *
     * Rendered by the host inside its Settings window. Default no-op.
     */
    @Composable
    fun LlmProviderSettingsPanel(modifier: Modifier) {}
}
