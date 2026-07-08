package ai.rever.boss.plugin.api

/**
 * Provider interface for plugin diagnostic reporting.
 *
 * Allows plugins to report diagnostic information beyond the basic
 * health monitoring provided by PluginSandboxRef.
 */
interface DiagnosticProvider {

    /**
     * Report a diagnostic entry for this plugin.
     *
     * @param category Category of the diagnostic (e.g., "performance", "error", "info")
     * @param message Human-readable diagnostic message
     * @param metadata Optional key-value metadata
     */
    fun reportDiagnostic(
        category: String,
        message: String,
        metadata: Map<String, String> = emptyMap()
    )

    /**
     * Get recent diagnostic entries for this plugin.
     *
     * @param limit Maximum number of entries to return
     * @return List of recent diagnostics, newest first
     */
    fun getRecentDiagnostics(limit: Int = 50): List<DiagnosticEntry>

    /**
     * Clear all diagnostic entries for this plugin.
     */
    fun clearDiagnostics()
}

/**
 * A diagnostic entry recorded by a plugin.
 */
data class DiagnosticEntry(
    /** Timestamp when the diagnostic was recorded (epoch milliseconds). */
    val timestamp: Long,
    /** Category of the diagnostic. */
    val category: String,
    /** Human-readable message. */
    val message: String,
    /** Optional metadata. */
    val metadata: Map<String, String> = emptyMap()
)
