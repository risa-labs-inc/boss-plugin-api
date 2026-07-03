package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Live state of the `boss` MCP server (hosted by the terminal-tab plugin).
 */
data class McpServerState(
    /** MCP server identifier as clients see it (e.g. "boss"). */
    val serverName: String,
    /** User's enabled setting (server auto-starts when true). */
    val enabled: Boolean,
    /** Whether the server is currently bound and serving. */
    val running: Boolean,
    /** Bound port when running (may be a fallback port), else the configured port. */
    val port: Int?,
)

/**
 * One third-party AI CLI the MCP server can be registered ("attached") with.
 */
data class McpAttachTargetInfo(
    /** Stable key to pass to [McpServerController.attach] (e.g. "CLAUDE_CODE"). */
    val key: String,
    /** Human-readable name (e.g. "Claude Code"). */
    val displayName: String,
    /** True when this CLI currently has an attach registration. */
    val attached: Boolean,
)

/** Result of an attach attempt: [ok] plus a short human-readable message. */
data class McpAttachOutcome(
    val ok: Boolean,
    val message: String,
)

/**
 * Control surface for the `boss` MCP server, exposed by the terminal-tab plugin
 * via [PluginContext.registerPluginAPI] so management UIs (e.g. the Plugin
 * Manager's MCP tab) can toggle the server and attach it to AI CLIs.
 *
 * Resolve lazily via `context.getPluginAPI(McpServerController::class.java)` —
 * plugin load order is not guaranteed, so it may be null at `register()` time
 * and non-null once terminal-tab has loaded.
 */
interface McpServerController {
    /** Live server state (name, enabled, running, port). */
    val state: StateFlow<McpServerState>

    /** Available attach targets with their current attached flag. */
    val attachTargets: StateFlow<List<McpAttachTargetInfo>>

    /** Turn the MCP server on/off (persisted; the server reconciles live). */
    fun setEnabled(enabled: Boolean)

    /**
     * Change the server's configured port (persisted; the running server
     * reconciles live with a stop/start and re-registers attached CLIs on
     * the new endpoint). Valid range 1024-65535 — implementations reject
     * values outside it. Observe [state] for the port actually bound (which
     * may be a fallback when the configured one is busy).
     *
     * Callers should guard invocation with a try/catch on
     * [NoSuchMethodError]/[AbstractMethodError] and degrade (hide the
     * control) — a host or terminal-tab predating this method may still be
     * running.
     */
    fun setPort(port: Int)

    /**
     * Register the MCP endpoint with the CLI identified by [targetKey]
     * (a [McpAttachTargetInfo.key]). Idempotent; on failure the ready-to-paste
     * config is copied to the clipboard and [McpAttachOutcome.message] says so.
     */
    suspend fun attach(targetKey: String): McpAttachOutcome
}
