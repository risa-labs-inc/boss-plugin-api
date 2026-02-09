package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Provider interface for download management operations.
 *
 * This interface abstracts download functionality to allow
 * the Downloads panel to be extracted to a separate module.
 */
interface DownloadDataProvider {
    /**
     * List of all downloads across all windows.
     */
    val downloads: StateFlow<List<DownloadItemData>>

    /**
     * Pause a download.
     */
    suspend fun pauseDownload(id: String): Result<Unit>

    /**
     * Resume a paused download.
     */
    suspend fun resumeDownload(id: String): Result<Unit>

    /**
     * Cancel a download.
     */
    suspend fun cancelDownload(id: String): Result<Unit>

    /**
     * Remove a completed or cancelled download from the list.
     */
    suspend fun removeDownload(id: String): Result<Unit>

    /**
     * Clear all completed downloads from the list.
     */
    suspend fun clearCompleted(): Result<Unit>

    /**
     * Reveal a downloaded file in the system file manager.
     */
    fun revealInFolder(path: String)

    /**
     * Open a downloaded file with the default application.
     */
    fun openFile(path: String)
}

/**
 * Data class representing a download item.
 */
data class DownloadItemData(
    val id: String,
    val fileName: String,
    val destinationPath: String,
    val url: String,
    val status: DownloadStatusData,
    val receivedBytes: Long,
    val totalBytes: Long?,
    val speed: Double,
    val canPause: Boolean,
    val canResume: Boolean,
    val errorReason: String?,
    val startTime: Long,
    val endTime: Long?
)

/**
 * Enum representing download status.
 */
enum class DownloadStatusData {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
