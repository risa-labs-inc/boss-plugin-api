package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Result of an MCP tool invocation.
 *
 * [text] is the payload returned to the calling MCP client (the model). Plugins
 * format it however they like — typically plain text or a JSON string. Set
 * [isError] to signal the call failed so the client surfaces it as an error.
 */
data class McpToolResult(
    val text: String,
    val isError: Boolean = false,
)

/**
 * Typed, parsed view of an MCP tool call's arguments.
 *
 * The host parses the client's JSON arguments (matching the tool's
 * [McpToolDefinition.inputSchema]) into this holder before invoking the handler,
 * so plugins need no JSON/serialization dependency of their own. Only top-level
 * scalar arguments are exposed via the typed getters; use [raw] for the original
 * JSON string if a tool needs nested structures.
 */
class McpToolArgs(
    private val values: Map<String, Any?>,
    /** The original JSON-object arguments string (`"{}"` if none), for advanced parsing. */
    val raw: String = "{}",
) {
    /** String value for [key], or null if absent/null. Non-string scalars are stringified. */
    fun string(key: String): String? = when (val v = values[key]) {
        null -> null
        is String -> v
        else -> v.toString()
    }

    /** Boolean value for [key], or null if absent/not a boolean. Accepts "true"/"false" strings. */
    fun boolean(key: String): Boolean? = when (val v = values[key]) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull()
        else -> null
    }

    /** Int value for [key], or null if absent/not an integer. */
    fun int(key: String): Int? = when (val v = values[key]) {
        is Int -> v
        is Long -> v.toInt()
        is Double -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }

    /** Double value for [key], or null if absent/not a number. */
    fun double(key: String): Double? = when (val v = values[key]) {
        is Double -> v
        is Int -> v.toDouble()
        is Long -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }

    /** True if [key] was present in the arguments (even if null). */
    fun has(key: String): Boolean = values.containsKey(key)
}

/**
 * Handler invoked when an MCP client calls a plugin-contributed tool.
 *
 * [args] is the parsed argument set (see [McpToolArgs]). Runs on a background
 * coroutine — suspending calls (e.g. provider refresh operations) are expected.
 * Throwing is tolerated (the host catches it and returns an error result), but
 * returning an [McpToolResult] with `isError = true` is preferred for clean
 * messages.
 */
fun interface McpToolHandler {
    suspend fun call(args: McpToolArgs): McpToolResult
}

/**
 * One MCP tool a plugin exposes to in-terminal agents via the `boss` MCP server.
 *
 * The tool is live only while the contributing plugin is active — it appears when
 * the plugin loads/enables and is removed when it disables/unloads.
 */
data class McpToolDefinition(
    /** Unique tool name, snake_case, e.g. `git_status`. Surfaces to clients as `mcp__boss__<name>`. */
    val name: String,
    /** Human/model-facing description of what the tool does and when to use it. */
    val description: String,
    /**
     * JSON-Schema object describing the tool's arguments, as a JSON string.
     * Defaults to a no-argument object. Example:
     * `{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}`.
     */
    val inputSchema: String = """{"type":"object","properties":{}}""",
    /** Hint that the tool only reads state (no side effects). */
    val readOnly: Boolean = true,
    /** Invoked when the tool is called. */
    val handler: McpToolHandler,
) {
    /**
     * RBAC permissions the user must ALL hold for this tool to be exposed to
     * agents. Empty (default) means no per-tool permission gate — the tool is
     * still bounded by whether its plugin is active and any user toggle.
     * Admins bypass this check. Set via, e.g.,
     * `McpToolDefinition(...).apply { requiredPermissions = listOf("secrets.create") }`.
     */
    var requiredPermissions: List<String> = emptyList()

    /** When true, only administrators may use this tool (mirrors manifest `requiresAdmin`). */
    var requiresAdmin: Boolean = false
}

/**
 * A plugin's group of MCP tools. Register via [PluginContext.registerMcpToolProvider].
 *
 * Mirrors [SearchProvider]: implement it, register in `register()`, and the host
 * automatically unregisters it when the plugin is disabled or unloaded.
 *
 * Example:
 * ```kotlin
 * context.registerMcpToolProvider(object : McpToolProvider {
 *     override val providerId = pluginId
 *     override fun tools() = listOf(
 *         McpToolDefinition(
 *             name = "git_status",
 *             description = "Working-tree status of the current project.",
 *             handler = McpToolHandler { args -> McpToolResult(runStatus()) },
 *         )
 *     )
 * })
 * ```
 */
interface McpToolProvider {
    /** Unique id for this provider; convention is the owning plugin's pluginId. */
    val providerId: String

    /** The tools this provider currently exposes. Queried at registration time. */
    fun tools(): List<McpToolDefinition>
}

/**
 * A tool as held by the host registry, tagged with the provider that owns it.
 * Consumed by the MCP server bridge to mirror tools onto the live MCP server.
 */
data class RegisteredMcpTool(
    val providerId: String,
    val definition: McpToolDefinition,
)

/**
 * Host-side registry aggregating all plugin-contributed MCP tools.
 *
 * The MCP server bridge (the terminal-tab plugin) observes [tools] and mirrors
 * the set onto the live MCP server, and routes tool calls through [invoke].
 * Obtained via [PluginContext.mcpToolRegistry].
 */
interface McpToolRegistry {
    /**
     * Tools currently exposed to MCP clients: contributed by an active provider
     * AND not user-disabled. The bridge mirrors exactly this set onto the live
     * MCP server, so it emits on every register/unregister and on enable/disable.
     */
    val tools: StateFlow<List<RegisteredMcpTool>>

    /**
     * Every tool contributed by an active provider, including user-disabled ones.
     * Management UIs (e.g. the Plugin Manager MCP tab) render this and cross-
     * reference [disabledToolNames] to show each tool's on/off state.
     */
    val allTools: StateFlow<List<RegisteredMcpTool>>

    /**
     * Names of tools the user has turned off. A disabled tool stays in [allTools]
     * (while its plugin is active) but is excluded from [tools]. Persisted across
     * restarts.
     */
    val disabledToolNames: StateFlow<Set<String>>

    /**
     * Enable or disable a contributed tool by name (persisted). Disabling removes
     * it from the live MCP server; enabling restores it. No-op for unknown names
     * except that the preference is still recorded.
     */
    fun setToolEnabled(toolName: String, enabled: Boolean)

    /**
     * Invoke a tool by name with a JSON-object argument string.
     * Guarded by the host: returns an error [McpToolResult] rather than throwing
     * if the tool is missing or its handler fails.
     */
    suspend fun invoke(toolName: String, arguments: String): McpToolResult
}
