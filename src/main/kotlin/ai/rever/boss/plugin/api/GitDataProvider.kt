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
@HostImplemented
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
     * Commit the staged changes with [message] (`git commit -m`).
     *
     * @return Operation result. The error default body protects implementors
     * that predate this member; callers gate on minBossVersion (see the
     * gating note on the DIFF block below).
     */
    suspend fun commit(message: String): GitOperationResultData =
        GitOperationResultData.Error("Commit is not supported on this host")

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

    // ═══════════════════════════════════════════════════════════════════════════
    // DIFF (1.0.87)
    //
    // Every member here carries a default body: this interface has implementors
    // compiled against earlier versions (the host, the OOP IPC proxy), and a
    // defaultless addition rejects them at load.
    //
    // GATING, for every 1.0.87 member below: [GitDataProvider] is @HostImplemented,
    // so these members resolve from the host's pinned copy, parent-first. A plugin
    // that references any of them gates on minBossVersion, not minApiVersion -
    // below the floor the validator rejects the WHOLE plugin at load, it does not
    // degrade at the call site. The default bodies protect IMPLEMENTORS compiled
    // against earlier versions; they are not a graceful-degradation path for
    // callers.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Working-tree (or index) diff at [path], ONE ENTRY PER CHANGED FILE. [path] is
     * normally a single file, but git accepts a directory pathspec, so this returns a
     * list for the same reason [diffRef] does: returning the first silently dropped the
     * rest. Empty when nothing changed under [path].
     */
    suspend fun diffFile(path: String, staged: Boolean = false): List<GitDiffData> = emptyList()

    /**
     * The changes introduced by the single commit [ref] (`git show`), ONE ENTRY PER
     * CHANGED FILE, optionally restricted to one [path]. Empty when [ref] names no
     * commit or changes nothing.
     *
     * A list, not a single value: a commit touches as many files as it touches, and
     * returning the first silently presented one file's changes as the whole commit.
     * Restrict with [path] when you want exactly one.
     */
    suspend fun diffRef(ref: String, path: String? = null): List<GitDiffData> = emptyList()

    /**
     * Parsed diff between two refs (`git diff from to`), ONE ENTRY PER CHANGED FILE,
     * optionally restricted to one [path]. Empty when either ref is unknown or they
     * differ in nothing. A list for the same reason as [diffRef].
     */
    suspend fun diffBetween(from: String, to: String, path: String? = null): List<GitDiffData> = emptyList()

    /**
     * Changed-file listing (`git diff --name-status`), working tree or index
     * depending on [staged]. Reuses [GitFileStatusData] deliberately - a diff
     * listing is a status listing, and a second status type would drift.
     */
    suspend fun diffNames(staged: Boolean = false): List<GitFileStatusData> = emptyList()

    /**
     * Open a diff tab for [filePath] in the window [windowId]: working tree vs
     * HEAD, or the index when [staged], or [fromRef] vs [toRef] when both are
     * given. Fire-and-forget like [openFile], and [windowId] is required and
     * second for the same reason it is on [openFile] - a defaulted trailing
     * window parameter compiles cleanly and opens the tab nowhere. The empty
     * default body protects implementors that predate it, per the note above.
     */
    fun openDiff(
        filePath: String,
        windowId: String,
        staged: Boolean = false,
        fromRef: String? = null,
        toRef: String? = null
    ) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // GRAPH (1.0.87)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Commit log with topology, for git-graph rendering (1.0.87).
     *
     * Like [commitLog]'s source but typed as [GitCommitNodeData]: each node
     * carries its parent hashes so a consumer can draw branch lanes, plus the
     * same ref decorations (branch/tag names) [GitCommitInfoData] exposes.
     * Commits come back in `git log` order - newest first, topological.
     *
     * @param limit Maximum number of commits to fetch (default 100).
     * @return the nodes, or an empty list when no repo is open.
     */
    suspend fun logGraph(limit: Int = 100): List<GitCommitNodeData> = emptyList()

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOTE + BRANCH-SCOPED GRAPH (1.0.87)
    //
    // Default bodies, like every block above it: implementors compiled against
    // earlier versions must still load.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * `git fetch` - update remote-tracking refs without touching the work tree.
     *
     * @param prune also delete remote-tracking refs whose remote branch is gone.
     * @return the operation result. The error default body protects
     * implementors, not callers - see the gating note on the DIFF block.
     */
    suspend fun fetch(prune: Boolean = false): GitOperationResultData =
        GitOperationResultData.Error("Fetch is not supported on this host")

    /**
     * `git pull` into the checked-out branch.
     *
     * @return the operation result. The error default body protects
     * implementors, not callers - see the gating note on the DIFF block.
     */
    suspend fun pull(): GitOperationResultData =
        GitOperationResultData.Error("Pull is not supported on this host")

    /**
     * `git push` the checked-out branch to its remote, setting upstream when
     * it has none. Never force-pushes - there is no flag for it here on
     * purpose; a caller that needs one uses a terminal, where the consequences
     * are visible.
     *
     * @return the operation result. The error default body protects
     * implementors, not callers - see the gating note on the DIFF block.
     */
    suspend fun push(): GitOperationResultData =
        GitOperationResultData.Error("Push is not supported on this host")

    /**
     * Local and remote-tracking branches of the open repository.
     *
     * The ref decorations on [logGraph] only name branches that happen to
     * appear in the fetched window of history, which is not the same set - a
     * branch picker needs every branch, including ones whose tip is older than
     * the last N commits of HEAD.
     *
     * @return the branches, or an empty list when no repo is open. The empty
     * default body protects implementors, not callers - see the gating note
     * on the DIFF block.
     */
    suspend fun branches(): List<GitBranchRefData> = emptyList()

    /**
     * Like [logGraph], but for the history reachable from [ref] rather than
     * from HEAD (`git log <ref>`).
     *
     * A null or blank [ref] means HEAD, and the default body delegates to
     * [logGraph] for that case. Defensive only: both members ship in 1.0.87,
     * so the delegation matters just for an implementor built against an
     * intermediate snapshot that had [logGraph] without this member - it is
     * not a degradation path callers can rely on (see the gating note on the
     * DIFF block).
     *
     * @return the nodes, or an empty list when [ref] names nothing.
     */
    suspend fun logGraphFor(ref: String?, limit: Int = 100): List<GitCommitNodeData> =
        if (ref.isNullOrBlank()) logGraph(limit) else emptyList()
}

/**
 * One branch of the open repository (1.0.87).
 *
 * [name] is git's short form: `main` for a local branch, `origin/main` for a
 * remote-tracking one. [isCurrent] is true for at most one entry, and for none
 * at all when HEAD is detached.
 */
@Serializable
data class GitBranchRefData(
    val name: String,
    val isCurrent: Boolean = false,
    val isRemote: Boolean = false,
)

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
 * Commit node with topology, for git-graph rendering (1.0.87).
 *
 * A separate type from [GitCommitInfoData] on purpose: data classes are never
 * evolved across the boundary, so the parent list ships in its own class.
 * [parents] is the space-separated `%P` list - empty for a root commit,
 * more than one entry for a merge commit.
 */
@Serializable
data class GitCommitNodeData(
    val hash: String,
    val shortHash: String,
    val subject: String,
    val author: String,
    val authorEmail: String,
    val date: Long,
    val refs: List<String>,
    val parents: List<String>
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

/**
 * Parsed unified diff of one file (1.0.87).
 *
 * [rawUnified] is the exact `git diff`/`git show` output; a unified view renders
 * it as-is. [hunks] carry the same content parsed, for structured consumers
 * (side-by-side rendering, hunk accept/reject, agents). Ship both: [rawUnified]
 * is the forward-compat hatch - anything the parsed model does not yet express
 * is still in the text.
 *
 * Do not add constructor parameters later (additive-only rule); grow through
 * [rawUnified] or a new type.
 */
@Serializable
data class GitDiffData(
    val path: String,
    val oldPath: String? = null,
    val additions: Int = 0,
    val deletions: Int = 0,
    val isBinary: Boolean = false,
    val hunks: List<DiffHunk> = emptyList(),
    val rawUnified: String = ""
)

/**
 * One hunk of a unified diff. Line numbers are 1-based, git's convention:
 * [oldStart]/[newStart] are the first line of each side's range, 0 when that
 * side of the hunk is empty (pure addition at file start / pure deletion).
 */
@Serializable
data class DiffHunk(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val header: String = "",
    val lines: List<DiffLine> = emptyList()
)

/**
 * One line of a hunk. [oldLine]/[newLine] are 1-based line numbers on each
 * side; null on the side the line does not exist on (a CONTEXT line has both,
 * an ADDED line only [newLine], a REMOVED line only [oldLine]).
 */
@Serializable
data class DiffLine(
    val kind: DiffLineKind = DiffLineKind.UNKNOWN,
    val text: String,
    val oldLine: Int? = null,
    val newLine: Int? = null
)

/**
 * Kind of a [DiffLine]. Consumers must `when` over this with an `else` branch:
 * it is an open set across the compiled-plugin boundary.
 *
 * [UNKNOWN] exists for the WIRE side of that openness: this enum crosses the
 * IPC boundary inside [DiffLine], and without it the first added kind would
 * make an older consumer's decoder throw and take the whole diff down.
 * Decoders must coerce unrecognised names to [UNKNOWN] - with kotlinx
 * serialization, `Json { coerceInputValues = true }` does it via [DiffLine]'s
 * default. Producers never emit it.
 */
@Serializable
enum class DiffLineKind {
    CONTEXT,
    ADDED,
    REMOVED,
    UNKNOWN
}
