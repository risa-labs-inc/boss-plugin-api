package ai.rever.boss.plugin.api

/**
 * Why AI cannot be used right now, and therefore what would fix it.
 *
 * The two failure states have **different fixes**, so collapsing them into one
 * "AI unavailable" message sends half of users to the wrong place. Treat as an open
 * set and always write an `else`.
 */
enum class AiReadiness {
    /** A gateway is present and a provider is configured. */
    READY,

    /** No plugin provides [AiGatewayAPI]. The fix is installing the AI Gateway plugin. */
    GATEWAY_MISSING,

    /** A gateway is present but no provider is chosen. The fix is Settings, AI Providers. */
    NO_PROVIDER,
}

/**
 * Tells a user why an AI action did nothing, and takes them to the fix.
 *
 * Without this each plugin ends at a dead-end toast: "AI is unavailable, install the
 * gateway and configure a provider" is accurate and useless, because it names two
 * different problems and offers a route to neither. [promptToFix] shows a dialog for
 * whichever one is actually true, and its confirm button opens the place that fixes it.
 *
 * It lives in the api jar rather than in the AI Gateway plugin for a structural reason:
 * the case it exists to handle is *the gateway being absent*, so a helper shipped inside
 * the gateway could never run then. The api jar is served by `ApiClassLoader` and is
 * always present.
 *
 * **Nothing here throws.** Every call crosses a plugin classloader boundary or reaches a
 * host provider that may be null, and this runs on the path where a user has just pressed
 * a button - so a failure to *explain* a failure must not become a second failure. When
 * the host cannot show a dialog, [promptToFix] still reports what it found so the caller
 * can fall back to its own message.
 */
object AiAvailability {

    /**
     * What is missing, if anything. Cheap; safe to call per keystroke or per composition.
     *
     * Resolves the gateway on every call rather than caching, for the same reason every
     * consumer does: plugin load order is not guaranteed, so a null now may be a gateway
     * that has simply not registered yet.
     */
    fun check(context: PluginContext): AiReadiness =
        runCatching {
            val gateway =
                context.getPluginAPI(AiGatewayAPI::class.java)
                    ?: return@runCatching AiReadiness.GATEWAY_MISSING
            if (gateway.activeModel() == null) AiReadiness.NO_PROVIDER else AiReadiness.READY
        }.getOrElse {
            // A LinkageError here means a gateway built against a different api revision,
            // which for the user is indistinguishable from not having one.
            AiReadiness.GATEWAY_MISSING
        }

    /**
     * If AI is unavailable, show a dialog offering the fix, and open it if accepted.
     *
     * Returns what it found, so a caller can decide whether to proceed. [READY] means go
     * ahead; anything else means the action should not run. Call this at the point the
     * user asks for AI, not at load: a dialog on startup for a feature nobody invoked is
     * an interruption, not help.
     *
     * [featureName] appears in the message ("Fix with AI needs..."), so pass what the
     * user just pressed rather than the plugin name.
     *
     * Accepting opens the Toolbox (for [GATEWAY_MISSING]) or Settings, AI Providers (for
     * [NO_PROVIDER]) and returns immediately. It does **not** wait for the user to finish,
     * and it cannot install anything itself - installing is an operator action, so the
     * button opens the store rather than pretending to do it.
     */
    suspend fun promptToFix(
        context: PluginContext,
        featureName: String,
    ): AiReadiness {
        val readiness = check(context)
        if (readiness == AiReadiness.READY) return readiness

        val dialogs = context.genericDialogProvider ?: return readiness
        val accepted =
            runCatching {
                when (readiness) {
                    AiReadiness.GATEWAY_MISSING ->
                        dialogs.showConfirmationDialog(
                            title = "AI Gateway not installed",
                            message =
                                "$featureName needs the AI Gateway plugin, which provides AI to " +
                                    "every BOSS plugin. Open the Toolbox to install it?",
                            confirmText = "Open Toolbox",
                        )

                    AiReadiness.NO_PROVIDER ->
                        dialogs.showConfirmationDialog(
                            title = "No AI provider configured",
                            message =
                                "$featureName needs an AI provider. Choose one and add its key in " +
                                    "Settings, AI Providers.",
                            confirmText = "Open settings",
                        )

                    AiReadiness.READY -> false
                }
            }.getOrDefault(false)

        if (accepted) {
            when (readiness) {
                AiReadiness.GATEWAY_MISSING -> openToolbox(context)
                AiReadiness.NO_PROVIDER -> openProviderSettings(context)
                AiReadiness.READY -> Unit
            }
        }
        return readiness
    }

    /**
     * Open the Toolbox panel, where plugins are installed.
     *
     * `defaultOrder` is UI metadata the host ignores when matching an open event (it
     * compares `panelId` and `pluginId` only), so 0 is fine here and does not have to
     * agree with the Toolbox's own declaration. The host also waits briefly for a panel
     * that has not registered yet rather than dropping the event, which matters because
     * this can fire early in a session.
     */
    private suspend fun openToolbox(context: PluginContext) {
        val events = context.panelEventProvider ?: return
        val windowId = context.windowId ?: return
        runCatching { events.openPanel(PanelId(TOOLBOX_PANEL_ID, 0), windowId) }
    }

    private fun openProviderSettings(context: PluginContext) {
        val settings = context.settingsProvider ?: return
        val windowId = context.windowId ?: return
        runCatching { settings.openSettings(windowId, AI_PROVIDERS_SECTION) }
    }

    /** Panel id of the Toolbox (the plugin manager), which hosts the plugin store. */
    private const val TOOLBOX_PANEL_ID = "plugin-manager"

    /**
     * Host `SettingsSection` entry for the AI providers page.
     *
     * Still `LLM_PROVIDERS` even though the section displays as "AI Providers": the enum
     * name is what deep links and existing callers use, and the host matches it
     * case-insensitively.
     */
    private const val AI_PROVIDERS_SECTION = "LLM_PROVIDERS"
}
