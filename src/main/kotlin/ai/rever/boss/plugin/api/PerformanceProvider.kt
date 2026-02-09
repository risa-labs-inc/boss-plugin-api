package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Interface for performance data providers.
 *
 * This interface allows the Performance panel to be extracted to a separate module
 * while keeping the monitoring infrastructure in composeApp.
 *
 * Usage:
 * - composeApp implements this interface with PerformanceMonitor
 * - plugin-panel-performance depends only on this interface
 * - At registration time, composeApp provides the implementation
 */
interface PerformanceDataProvider {
    /**
     * Current performance snapshot (updated periodically).
     */
    val currentSnapshot: StateFlow<PerformanceSnapshotData?>

    /**
     * Historical snapshots for charts.
     */
    val history: StateFlow<List<PerformanceSnapshotData>>

    /**
     * Current performance settings.
     */
    val settings: StateFlow<PerformanceSettingsData>

    /**
     * Request garbage collection.
     */
    fun requestGC()

    /**
     * Export metrics to file.
     * @return Result with file path on success
     */
    suspend fun exportMetrics(): Result<String>

    /**
     * Update performance settings.
     */
    suspend fun updateSettings(settings: PerformanceSettingsData)
}

/**
 * Performance snapshot data (simplified for interface).
 */
@Serializable
data class PerformanceSnapshotData(
    val timestamp: Long,
    val heapUsedBytes: Long,
    val heapMaxBytes: Long,
    val heapCommittedBytes: Long = 0L,
    val heapUsagePercent: Float,
    val nonHeapUsedBytes: Long,
    val nonHeapCommittedBytes: Long = 0L,
    val processLoadPercent: Float,
    val systemLoadPercent: Float,
    val activeThreadCount: Int,
    val availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    val gcCollectionCount: Long,
    val gcCollectionTimeMs: Long,
    val browserTabCount: Int,
    val terminalCount: Int,
    val editorTabCount: Int,
    val panelCount: Int,
    val windowCount: Int,
    val memoryPools: List<MemoryPoolData> = emptyList(),
    val threads: List<ThreadData> = emptyList(),
    val gcCollectors: List<GcCollectorData> = emptyList(),
    val browserTabs: List<BrowserTabData> = emptyList(),
    val terminals: List<TerminalData> = emptyList(),
    val editorTabs: List<EditorTabData> = emptyList()
) {
    val heapUsedMB: Float get() = heapUsedBytes / (1024f * 1024f)
    val heapMaxMB: Float get() = heapMaxBytes / (1024f * 1024f)
    val heapCommittedMB: Float get() = heapCommittedBytes / (1024f * 1024f)
    val nonHeapUsedMB: Float get() = nonHeapUsedBytes / (1024f * 1024f)
    val nonHeapCommittedMB: Float get() = nonHeapCommittedBytes / (1024f * 1024f)
}

/**
 * Memory pool data for detailed view.
 */
@Serializable
data class MemoryPoolData(
    val name: String,
    val type: String,
    val usedBytes: Long,
    val maxBytes: Long,
    val committedBytes: Long
) {
    val usedMB: Float get() = usedBytes / (1024f * 1024f)
    val maxMB: Float get() = if (maxBytes > 0) maxBytes / (1024f * 1024f) else committedBytes / (1024f * 1024f)
    val usagePercent: Float
        get() = if (maxBytes > 0) (usedBytes.toFloat() / maxBytes) * 100f
                else if (committedBytes > 0) (usedBytes.toFloat() / committedBytes) * 100f
                else 0f
}

/**
 * Thread data for detailed view.
 */
@Serializable
data class ThreadData(
    val id: Long,
    val name: String,
    val state: String,
    val cpuTimeMs: Long,
    val userTimeMs: Long,
    val blockedCount: Long,
    val waitedCount: Long
)

/**
 * GC collector data for detailed view.
 */
@Serializable
data class GcCollectorData(
    val name: String,
    val collectionCount: Long,
    val collectionTimeMs: Long,
    val lastGcInfo: LastGcInfoData? = null
)

/**
 * Information about the last GC event for a collector.
 */
@Serializable
data class LastGcInfoData(
    val startTime: Long,
    val durationMs: Long,
    val memoryBeforeBytes: Long,
    val memoryAfterBytes: Long
) {
    val memoryReclaimedBytes: Long get() = memoryBeforeBytes - memoryAfterBytes
    val memoryReclaimedMB: Float get() = memoryReclaimedBytes / (1024f * 1024f)
    val memoryBeforeMB: Float get() = memoryBeforeBytes / (1024f * 1024f)
    val memoryAfterMB: Float get() = memoryAfterBytes / (1024f * 1024f)
}

/**
 * Information about an open browser tab.
 */
@Serializable
data class BrowserTabData(
    val id: String,
    val title: String,
    val url: String,
    val isActive: Boolean = false
)

/**
 * Information about an open terminal session.
 */
@Serializable
data class TerminalData(
    val id: String,
    val title: String,
    val workingDirectory: String = "",
    val isActive: Boolean = false
)

/**
 * Information about an open editor tab.
 */
@Serializable
data class EditorTabData(
    val id: String,
    val fileName: String,
    val filePath: String,
    val isModified: Boolean = false,
    val isActive: Boolean = false
)

/**
 * Performance settings data.
 */
@Serializable
data class PerformanceSettingsData(
    val enabled: Boolean = true,
    val showIndicator: Boolean = true,
    val memoryWarningThresholdPercent: Int = 75,
    val memoryCriticalThresholdPercent: Int = 90,
    val cpuWarningThresholdPercent: Int = 70,
    val cpuCriticalThresholdPercent: Int = 90,
    val memorySampleIntervalMs: Long = 1000,
    val cpuSampleIntervalMs: Long = 2000,
    val historyRetentionMinutes: Int = 30
)

/**
 * Health status for performance indicators.
 */
enum class HealthStatusLevel {
    GOOD,
    WARNING,
    CRITICAL
}

/**
 * Performance health data for status indicators.
 */
@Serializable
data class PerformanceHealthData(
    val memoryStatus: HealthStatusLevel,
    val cpuStatus: HealthStatusLevel,
    val overallStatus: HealthStatusLevel
)

/**
 * Callback for opening files from performance panel (e.g., exported metrics).
 */
fun interface FileOpenCallback {
    fun openFile(filePath: String)
}
