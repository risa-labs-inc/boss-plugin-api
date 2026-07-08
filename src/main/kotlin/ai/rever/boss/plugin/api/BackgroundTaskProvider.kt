package ai.rever.boss.plugin.api

import kotlinx.coroutines.Job

/**
 * Provider interface for background task management.
 *
 * Allows plugins to launch, track, and cancel background tasks
 * with structured tracking beyond raw coroutine scope.
 */
interface BackgroundTaskProvider {

    /**
     * Launch a named background task.
     *
     * @param name Human-readable task name for tracking
     * @param task The suspend function to execute
     * @return A task handle for tracking and cancellation, or null if launch failed
     */
    fun launchTask(name: String, task: suspend () -> Unit): BackgroundTaskHandle?

    /**
     * Get all currently running tasks.
     *
     * @return List of active task handles
     */
    fun getRunningTasks(): List<BackgroundTaskHandle>

    /**
     * Cancel all running tasks.
     *
     * @return Number of tasks cancelled
     */
    fun cancelAll(): Int
}

/**
 * Handle for a running background task.
 */
interface BackgroundTaskHandle {
    /** Human-readable name of the task. */
    val name: String

    /** Whether the task is still running. */
    val isActive: Boolean

    /** Cancel this task. */
    fun cancel()

    /** The underlying coroutine Job. */
    val job: Job
}
