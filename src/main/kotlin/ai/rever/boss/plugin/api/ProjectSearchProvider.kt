package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

/**
 * Provider interface for project-wide CONTENT search and replace (1.0.87).
 *
 * Distinct from [SearchProvider], which feeds the Spotlight dialog (fuzzy,
 * filename-oriented, small result sets): this is a find-in-files engine for
 * the search tab, the MCP tools, and programmatic consumers. The host
 * implements it; a plugin consumes it through
 * [PluginContext.projectSearchProvider] and must handle null (host predates
 * the implementation).
 *
 * Scanning rules implementations should state in their KDoc: binary files
 * (NUL heuristic) and oversized files are skipped, results are capped by
 * [maxResults], and a scan is cancellable by cancelling its coroutine.
 */
interface ProjectSearchProvider {
    /**
     * Search file contents under the current project.
     *
     * @param query Literal text, or a regular expression when [isRegex]
     * @param pathPattern Optional glob filter on the path relative to the
     * project root (e.g. `"**&#47;*.kt"`); null matches all files
     * @param isRegex Treat [query] as a regular expression
     * @param caseSensitive Case-sensitive matching (regex: `(?i)` still
     * applies on top of this)
     * @param wholeWord Match whole words only (boundaries `\b` for regex)
     * @param maxResults Hard cap on returned matches
     * @return Matches in scan order, capped; empty when nothing matches
     */
    suspend fun searchInProject(
        query: String,
        pathPattern: String? = null,
        isRegex: Boolean = false,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        maxResults: Int = 200
    ): List<FileMatch> = emptyList()

    /**
     * Search file contents, with an exclude filter applied by the ENGINE.
     *
     * An overload rather than an `excludePattern` parameter on the method above:
     * adding a parameter changes that method's JVM descriptor, and every
     * implementor compiled against the old one - the host and the 19 IPC proxies
     * among them - would silently stop overriding it. A new method is additive;
     * a changed signature is not. `apiCheck` enforces exactly this.
     *
     * Excluding here rather than in the caller is the whole point of the overload.
     * [maxResults] caps the scan, so a caller that filters the RETURNED list gets
     * "the first N matches, minus the excluded ones" - fewer results than exist,
     * with nothing to say so. Excluding during the walk means the cap applies to
     * matches the caller actually wants.
     *
     * @param excludePattern Comma-separated globs; a file matching any of them is
     * never scanned. Same syntax as [pathPattern]. Null or blank excludes nothing.
     *
     * The default body delegates to the six-argument overload, so a host that
     * predates this returns unfiltered results rather than none - the exclude box
     * stops working, the search does not.
     */
    suspend fun searchInProject(
        query: String,
        pathPattern: String?,
        excludePattern: String?,
        isRegex: Boolean,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        maxResults: Int
    ): List<FileMatch> =
        searchInProject(query, pathPattern, isRegex, caseSensitive, wholeWord, maxResults)

    /**
     * Replace occurrences across an EXPLICIT file list - never project-wide by
     * default. Open buffers are routed through the editor's version-guarded
     * apply path (undoable); closed files are written to disk.
     *
     * @param replacement For [isRegex] queries this supports `$1`..`$9`
     * capture-group references, Kotlin `Regex.replace` semantics
     * @param files Explicit relative-or-absolute paths to touch
     * @param dryRun Count what WOULD be replaced without writing anything
     * @return Per-file outcome; [ReplaceSummary.totalReplacements] is the sum
     */
    suspend fun replaceInProject(
        query: String,
        replacement: String,
        files: List<String>,
        isRegex: Boolean = false,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        dryRun: Boolean = true
    ): ReplaceSummary = ReplaceSummary(filesReplaced = 0, totalReplacements = 0)
}

/**
 * One content match (1.0.87). 1-based [line]/[column]; [matchLength] is the
 * matched span in characters - together they are the match range a replace
 * engine and a highlighter both need. [contextLine] is the full source line
 * for rendering.
 */
@Serializable
data class FileMatch(
    val path: String,
    val line: Int,
    val column: Int,
    val matchLength: Int,
    val contextLine: String
)

/**
 * Outcome of a [ProjectSearchProvider.replaceInProject] call (1.0.87).
 */
@Serializable
data class ReplaceSummary(
    val filesReplaced: Int,
    val totalReplacements: Int,
    val files: List<FileReplaceResult> = emptyList()
)

/**
 * Per-file replace outcome; [error] is non-null when that file was skipped or
 * failed (the rest of the batch still runs).
 */
@Serializable
data class FileReplaceResult(
    val path: String,
    val replacements: Int,
    val error: String? = null
)
