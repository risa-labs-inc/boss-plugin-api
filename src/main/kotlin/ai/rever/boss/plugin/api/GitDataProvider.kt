package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Interface for Git data providers.
 *
 * This interface allows the Git Status and Git Log panels to be extracted
 * to separate modules while keeping the Git infrastructure in composeApp.
 *
 * Usage:
 * - composeApp implements this interface with GitService and WindowGitState
 * - plugin-panel-git-status and plugin-panel-git-log depend only on this interface
 * - At registration time, composeApp provides the implementation
 */
interface GitDataProvider {
    // ═══════════════════════════════════════════════════════════════════════════
    // STATE (per-window)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * List of file status entries for the current repository.
     */
    val fileStatus: StateFlow<List<GitFileStatusData>>

    /**
     * List of commits in the log.
     */
    val commitLog: StateFlow<List<GitCommitInfoData>>

    /**
     * Whether the current project is a Git repository.
     */
    val isGitRepository: StateFlow<Boolean>

    /**
     * Whether a Git operation is currently in progress.
     */
    val isLoading: StateFlow<Boolean>

    // ═══════════════════════════════════════════════════════════════════════════
    // OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Refresh the git status.
     */
    suspend fun refreshStatus()

    /**
     * Refresh the commit log.
     *
     * @param limit Maximum number of commits to fetch (default 100)
     */
    suspend fun refreshLog(limit: Int = 100)

    /**
     * Stage a file for commit.
     *
     * @param filePath Path to the file to stage
     * @return Operation result
     */
    suspend fun stage(filePath: String): GitOperationResultData

    /**
     * Unstage a file.
     *
     * @param filePath Path to the file to unstage
     * @return Operation result
     */
    suspend fun unstage(filePath: String): GitOperationResultData

    /**
     * Stage all changed files.
     *
     * @return Operation result
     */
    suspend fun stageAll(): GitOperationResultData

    /**
     * Unstage all staged files.
     *
     * @return Operation result
     */
    suspend fun unstageAll(): GitOperationResultData

    /**
     * Discard changes to a file.
     *
     * @param filePath Path to the file
     * @return Operation result
     */
    suspend fun discardChanges(filePath: String): GitOperationResultData

    /**
     * Cherry-pick a commit.
     *
     * @param commitHash Hash of the commit to cherry-pick
     * @return Operation result
     */
    suspend fun cherryPick(commitHash: String): GitOperationResultData

    /**
     * Revert a commit.
     *
     * @param commitHash Hash of the commit to revert
     * @return Operation result
     */
    suspend fun revert(commitHash: String): GitOperationResultData

    /**
     * Checkout a commit, branch, or tag.
     *
     * @param ref Reference to checkout (commit hash, branch name, or tag)
     * @return Operation result
     */
    suspend fun checkout(ref: String): GitOperationResultData

    /**
     * Get the current project path.
     *
     * @return Project path, or null if no project is selected
     */
    fun getCurrentProjectPath(): String?

    /**
     * Open a file in the editor.
     *
     * @param filePath Path to the file
     * @param windowId The window that initiated the open request
     */
    fun openFile(filePath: String, windowId: String)
}

/**
 * Git file status data.
 */
@Serializable
data class GitFileStatusData(
    val path: String,
    val indexStatus: GitFileStatusTypeData?,
    val workTreeStatus: GitFileStatusTypeData?,
    val isStaged: Boolean,
    val isUnstaged: Boolean
)

/**
 * Git file status types.
 */
@Serializable
enum class GitFileStatusTypeData {
    MODIFIED,
    ADDED,
    DELETED,
    RENAMED,
    COPIED,
    UNTRACKED,
    IGNORED,
    UNMERGED
}

/**
 * Git commit information.
 */
@Serializable
data class GitCommitInfoData(
    val hash: String,
    val shortHash: String,
    val subject: String,
    val author: String,
    val authorEmail: String,
    val date: Long,
    val refs: List<String>
)

/**
 * Result of a Git operation.
 */
@Serializable
sealed class GitOperationResultData {
    @Serializable
    data class Success(val message: String? = null) : GitOperationResultData()

    @Serializable
    data class Error(val message: String) : GitOperationResultData()
}
