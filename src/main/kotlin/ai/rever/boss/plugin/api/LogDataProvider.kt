package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Provider interface for log capture and display operations.
 *
 * This interface abstracts log capture functionality to allow
 * the Console panel to be extracted to a dynamic plugin module
 * that can access logs from the main application's classloader.
 */
interface LogDataProvider {
    /**
     * List of all captured log entries.
     */
    val logs: StateFlow<List<LogEntryData>>

    /**
     * Current filter setting (ALL/STDOUT/STDERR).
     */
    val filter: StateFlow<LogFilterData>

    /**
     * Current search query for filtering logs.
     */
    val searchQuery: StateFlow<String>

    /**
     * Whether auto-scroll is enabled.
     */
    val autoScroll: StateFlow<Boolean>

    /**
     * Set the log filter.
     */
    fun setFilter(filter: LogFilterData)

    /**
     * Set the search query for filtering logs.
     */
    fun setSearchQuery(query: String)

    /**
     * Toggle auto-scroll on/off.
     */
    fun toggleAutoScroll()

    /**
     * Clear all captured logs.
     */
    fun clearLogs()

    /**
     * Export all visible logs as formatted text.
     *
     * @return Formatted text of all logs matching current filter/search
     */
    fun exportLogs(): String
}

/**
 * Data class representing a single log entry.
 *
 * @property timestamp When the log was captured (milliseconds since epoch)
 * @property message The log message text
 * @property source Whether this came from stdout or stderr
 */
data class LogEntryData(
    val timestamp: Long,
    val message: String,
    val source: LogSourceData
) {
    /**
     * Format timestamp as HH:mm:ss.SSS
     */
    fun formatTimestamp(): String {
        val instant = kotlin.time.Instant.fromEpochMilliseconds(timestamp)
        val dateTime = instant.toString() // ISO 8601 format

        // Extract time portion (HH:mm:ss.SSS)
        return try {
            val timeStart = dateTime.indexOf('T') + 1
            val timeEnd = dateTime.indexOf('Z')
            if (timeStart > 0 && timeEnd > timeStart) {
                dateTime.substring(timeStart, timeEnd).take(12) // HH:mm:ss.SSS
            } else {
                "00:00:00.000"
            }
        } catch (e: Exception) {
            "00:00:00.000"
        }
    }
}

/**
 * Source of a log entry.
 */
enum class LogSourceData {
    /**
     * Standard output (System.out)
     */
    STDOUT,

    /**
     * Standard error (System.err)
     */
    STDERR
}

/**
 * Log filter options.
 */
enum class LogFilterData {
    /**
     * Show all logs (stdout + stderr)
     */
    ALL,

    /**
     * Show only stdout logs
     */
    STDOUT,

    /**
     * Show only stderr logs
     */
    STDERR
}
