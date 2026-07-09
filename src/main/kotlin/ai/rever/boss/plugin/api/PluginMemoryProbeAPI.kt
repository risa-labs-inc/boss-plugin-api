package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

/**
 * One class's live-heap footprint attributed to a plugin.
 */
@Serializable
data class PluginClassStatData(
    val className: String,
    val instances: Long,
    val bytes: Long,
)

/**
 * A point-in-time sample of the live heap objects attributed to a plugin.
 *
 * In-process plugins share the host JVM heap, so attribution is by code
 * package: an object counts toward a plugin when its class lives under the
 * plugin's package prefix (derived from the jar manifest's mainClass, falling
 * back to the pluginId).
 */
@Serializable
data class PluginMemorySampleData(
    val atMs: Long,
    val pluginId: String,
    val packagePrefix: String,
    val classCount: Int,
    val instanceCount: Long,
    val totalBytes: Long,
    val topClasses: List<PluginClassStatData> = emptyList(),
)

/**
 * Cross-plugin per-plugin memory probing, implemented by the Performance
 * plugin.
 *
 * The Performance plugin registers an implementation via
 * [PluginContext.registerPluginAPI]; consumers (e.g. the Tool Evolver's probe)
 * obtain it with `context.getPluginAPI(PluginMemoryProbeAPI::class.java)` —
 * null when the Performance plugin is not installed/enabled, so consumers must
 * degrade gracefully (typically by sampling the class histogram themselves).
 *
 * Sample history is kept by the implementation and shared across consumers
 * (Performance panel, MCP tools, evolver probes), so leak heuristics see every
 * sample taken this session regardless of which surface requested it.
 */
interface PluginMemoryProbeAPI {

    /**
     * Sample live heap objects attributed to [pluginId], recording the sample
     * into the shared history. Forces a full GC — call on demand, never
     * periodically. Returns null when the JVM refuses the histogram (e.g. the
     * DiagnosticCommand MBean is unavailable).
     */
    fun sampleMemory(pluginId: String, top: Int = 12): PluginMemorySampleData?

    /** Samples recorded for [pluginId] this session, oldest first. */
    fun sampleHistory(pluginId: String): List<PluginMemorySampleData>

    /**
     * Human-readable leak heuristics for [pluginId] over the recorded history
     * plus host state (loaded/instance counts). Empty means nothing suspicious.
     */
    fun leakSignals(pluginId: String): List<String>
}
