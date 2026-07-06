package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow

/**
 * The one shared heuristic for attributing host stdout/stderr lines to a plugin.
 *
 * [LogEntryData] carries no plugin field (capture happens below the plugin
 * layer), so attribution is keyword-based: a line "belongs" to a plugin when it
 * mentions the plugin id, its last dotted segment, or its display name. Lives in
 * the API so every consumer (the Console panel's plugin filter, the Tool
 * Sidecar's probe, MCP tools) matches identically instead of re-implementing
 * the heuristic.
 */
object PluginLogMatcher {

    /** Lowercased, de-duplicated match keywords for a plugin. */
    fun keywordsFor(pluginId: String, displayName: String? = null): List<String> = buildList {
        add(pluginId)
        add(pluginId.substringAfterLast('.'))
        displayName?.takeIf { it.isNotBlank() }?.let { add(it) }
    }.map { it.lowercase() }.distinct()

    /** True when [message] mentions any of [keywords] (case-insensitive). */
    fun matches(message: String, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return false
        val line = message.lowercase()
        return keywords.any { line.contains(it) }
    }

    /** Convenience: entries from [entries] attributed to the plugin, preserving order. */
    fun filter(
        entries: List<LogEntryData>,
        pluginId: String,
        displayName: String? = null,
    ): List<LogEntryData> {
        val keywords = keywordsFor(pluginId, displayName)
        return entries.filter { matches(it.message, keywords) }
    }
}

/**
 * Cross-plugin log services implemented by the Console plugin.
 *
 * The Console plugin registers an implementation via
 * [PluginContext.registerPluginAPI]; consumers (e.g. the Tool Sidecar) obtain it
 * with `context.getPluginAPI(ConsoleLogsAPI::class.java)` — null when the
 * Console plugin is not installed/enabled, so consumers must degrade gracefully
 * (typically by filtering [PluginContext.logDataProvider] themselves with
 * [PluginLogMatcher]).
 *
 * Note: all flows derive from the host's [LogDataProvider.logs], which is a
 * single app-wide stream pre-filtered by the console's source filter and search
 * query. Per-plugin attribution uses [PluginLogMatcher] on top of that stream.
 */
interface ConsoleLogsAPI {

    /**
     * Live log entries attributed to [pluginId] (see [PluginLogMatcher]).
     * The returned flow is owned by the Console plugin's scope and stays active
     * while the plugin is loaded.
     */
    fun logsForPlugin(pluginId: String, displayName: String? = null): StateFlow<List<LogEntryData>>

    /**
     * The Console panel's current plugin filter as a pluginId, null = all
     * plugins. Set with [setPluginFilter].
     */
    val pluginFilter: StateFlow<String?>

    /**
     * Select (or clear, with null) the plugin whose logs the Console panel
     * shows. Lets other plugins deep-link a user into "this plugin's logs".
     */
    fun setPluginFilter(pluginId: String?)
}
