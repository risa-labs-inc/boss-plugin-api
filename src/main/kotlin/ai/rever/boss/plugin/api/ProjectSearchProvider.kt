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
 * [maxResults], and a scan is cancellable by cancelling its coroutine. For
 * regex queries, cancellation alone is not enough - `Regex.find` has no
 * suspension point, so a catastrophic-backtracking pattern would wedge the
 * dispatcher thread - so implementations must also match under a per-file
 * time budget and treat exceeding it as a skipped file.
 *
 * Matching is strictly PER-LINE: the engine matches each line on its own, and
 * a regex never spans a newline. A pattern containing one matches nothing
 * rather than producing a multi-line range, which [FileMatch] cannot name.
 * Zero-length matches are legal output (a regex like `a*`, `^`, or a bare
 * `\b`): they carry [FileMatch.matchLength] = 0, an insertion point rather
 * than a span.
 *
 * Path confinement: every path in this interface is confined to the current
 * project root. Relative paths resolve against it; a path that escapes it
 * (absolute or via `..`) is never scanned, and on the write path it fails
 * that file with a [FileReplaceResult.error] rather than touching disk
 * outside the project. A symlink does not create an escape: confinement is
 * decided on REAL paths - the implementation resolves each candidate with
 * `toRealPath()` and compares it against the project root's own real path, so
 * a link INSIDE the project pointing outside resolves outside and is skipped.
 * The walk must not follow symlinks (or must track visited real paths), since
 * a link cycle wedges it.
 */
@HostImplemented
interface ProjectSearchProvider {
    /**
     * Search file contents under the current project.
     *
     * This is the ergonomic entry point, and it is a thin default body over
     * the seven-argument overload (`excludePattern = null`). Implementors
     * MUST override the seven-argument overload - the exclude-aware engine -
     * and normally leave this one alone; overriding only this one leaves the
     * seven-argument overload returning empty.
     *
     * @param query Literal text, or a regular expression when [isRegex]
     * @param pathPattern Optional glob filter on the path relative to the
     * project root (e.g. `"**&#47;*.kt"`); null matches all files
     * @param isRegex Treat [query] as a regular expression
     * @param caseSensitive Case-sensitive matching (regex: `(?i)` still
     * applies on top of this)
     * @param wholeWord Match whole words only. A regex engine applies it as a
     * NON-capturing wrap of the query, `\b(?:query)\b`, preserving the
     * caller's group numbering exactly: a bare `\b` + query + `\b` mis-parses
     * on alternation (`\bfoo|bar\b` matches bounded `foo` OR unbounded `bar`,
     * alternation binding looser than concatenation), and a CAPTURING wrap
     * shifts every `$1..$9` reference in [replaceInProject] by one, silently
     * substituting the wrong capture. For a LITERAL query the engine must
     * `Regex.escape` it BEFORE the wrap, or a literal `foo(bar)`, `a.b` or
     * `C++` would be read as a pattern and mis-match or throw. Expect the
     * honest edge case too: a boundary against a query edge that is not a
     * word character (`$foo`, `foo.`) has nothing to bound, so such a query
     * matches nothing
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
    ): List<FileMatch> =
        searchInProject(query, pathPattern, null, isRegex, caseSensitive, wholeWord, maxResults)

    /**
     * Search file contents, with an exclude filter applied by the ENGINE.
     *
     * An overload rather than an `excludePattern` parameter on the method
     * above. This interface has never shipped, so no published implementor
     * constrains it - the reason is pragmatic: the host's engine
     * (BossConsole#289) was already written against this exact pair, its
     * six-argument override delegating to one exclude-aware seven-argument
     * path. Collapsing to a single method with `excludePattern: String? =
     * null` would force that override to be rewritten and the host
     * recompiled for a purely additive parameter - the rebuild the
     * one-release collapse was supposed to avoid. Consequence to know: with
     * two overloads, `searchInProject("q", "**&#47;*.kt", "build&#47;**")`
     * does not compile - three positional args match neither - so
     * exclude-aware callers pass all seven or use named arguments.
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
     * This overload is the PRIMARY one - the one implementors override. The
     * six-argument overload's default body delegates HERE with
     * `excludePattern = null`, so a host that builds the one exclude-aware
     * engine serves both entry points; the delegation deliberately does not
     * run the other way, because an engine implemented only here would have
     * left the ergonomic six-argument entry silently returning empty. The
     * `emptyList()` default protects implementors compiled against an
     * intermediate snapshot that predates the overload, nothing more.
     */
    suspend fun searchInProject(
        query: String,
        pathPattern: String?,
        excludePattern: String?,
        isRegex: Boolean,
        caseSensitive: Boolean,
        wholeWord: Boolean,
        maxResults: Int
    ): List<FileMatch> = emptyList()

    /**
     * Replace occurrences across an EXPLICIT file list - never project-wide by
     * default. Open buffers are routed through the editor's version-guarded
     * apply path (undoable); closed files are written to disk.
     *
     * @param replacement For [isRegex] queries this supports `$1`..`$9`
     * capture-group references, Kotlin `Regex.replace` semantics (an
     * implementation built on it therefore also inherits `$0` and
     * `${name}`; callers should not rely on those). For LITERAL queries
     * the string is inserted VERBATIM - `$` and `\` are not special - so an
     * implementation that funnels both paths through `Regex.replace` must
     * escape the replacement first (`\` → `\\`, `$` → `\$`), or a literal
     * like `USD$5` would mangle the output or throw
     * @param files Explicit paths to touch, confined to the project root
     * (see the interface note); a path outside it fails that file, never
     * writes
     * @param dryRun Count what WOULD be replaced without writing anything. For
     * zero-length regex matches the count is the number of insertion points,
     * and the engine must advance at least one position after an empty match
     * so the scan terminates rather than counting forever
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
 *
 * The span always fits on ONE line: matching is strictly per-line (see the
 * scanning rules on [ProjectSearchProvider]), so a highlighter slicing
 * [contextLine] from [column] for [matchLength] has no out-of-bounds path.
 * A [endLine]/[endColumn] pair is deliberately absent because the contract
 * does not need it, not because it would be dropped later.
 *
 * [contextLine] is otherwise the full source line, but implementations MAY
 * truncate a very long one (a minified bundle is megabytes, repeated up to
 * [ProjectSearchProvider.searchInProject]'s maxResults across an IPC
 * boundary) - with one condition: the slice from [column] - 1 for
 * [matchLength] characters must always be present, or the slicing invariant
 * above breaks.
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
