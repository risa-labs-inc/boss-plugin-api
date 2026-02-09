package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Interface for run configuration data providers.
 *
 * This interface allows the RunConfigurations panel to be extracted to a separate module
 * while keeping the configuration management infrastructure in composeApp.
 *
 * Usage:
 * - composeApp implements this interface with RunConfigurationManager + RunEventBus
 * - plugin-panel-run-configurations depends only on this interface
 * - At registration time, composeApp provides the implementation
 */
interface RunConfigurationDataProvider {
    /**
     * List of auto-detected run configurations from project scan.
     */
    val detectedConfigurations: StateFlow<List<RunConfigurationData>>

    /**
     * Whether a project scan is currently in progress.
     */
    val isScanning: StateFlow<Boolean>

    /**
     * Last error that occurred during scanning or configuration operations.
     * Null if no error.
     */
    val lastError: StateFlow<String?>

    /**
     * Scan a project directory for runnable entry points.
     *
     * @param projectPath Path to the project to scan
     * @param windowId The window that initiated the scan (required for multi-window support)
     */
    suspend fun scanProject(projectPath: String, windowId: String)

    /**
     * Execute a run configuration.
     *
     * @param config The configuration to execute
     * @param windowId The window that initiated the run (required for multi-window support)
     */
    suspend fun execute(config: RunConfigurationData, windowId: String)

    /**
     * Clear the last error.
     */
    suspend fun clearError()
}

/**
 * Simplified run configuration data for the plugin interface.
 * Maps from the internal RunConfiguration type.
 */
@Serializable
data class RunConfigurationData(
    val id: String,
    val name: String,
    val type: RunConfigurationTypeData,
    val filePath: String,
    val lineNumber: Int,
    val language: LanguageData,
    val command: String,
    val workingDirectory: String,
    val environmentVariables: Map<String, String> = emptyMap(),
    val arguments: String = "",
    val isAutoDetected: Boolean = true,
    val timestamp: Long = 0L
)

/**
 * Types of run configurations.
 */
@Serializable
enum class RunConfigurationTypeData {
    MAIN_FUNCTION,
    SCRIPT,
    TEST,
    CUSTOM
}

/**
 * Supported programming languages for run detection.
 */
@Serializable
enum class LanguageData(val displayName: String, val extensions: List<String>) {
    KOTLIN("Kotlin", listOf("kt", "kts")),
    JAVA("Java", listOf("java")),
    PYTHON("Python", listOf("py")),
    JAVASCRIPT("JavaScript", listOf("js", "jsx", "mjs")),
    TYPESCRIPT("TypeScript", listOf("ts", "tsx")),
    GO("Go", listOf("go")),
    RUST("Rust", listOf("rs")),
    UNKNOWN("Unknown", emptyList());

    companion object {
        fun fromExtension(extension: String): LanguageData {
            return entries.find { it.extensions.contains(extension.lowercase()) } ?: UNKNOWN
        }

        fun fromFileName(fileName: String): LanguageData {
            val ext = fileName.substringAfterLast('.', "")
            return fromExtension(ext)
        }
    }
}
