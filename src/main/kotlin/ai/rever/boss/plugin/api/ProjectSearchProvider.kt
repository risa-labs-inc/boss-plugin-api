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
 *
 * GATING: both floors apply, for the reasons spelled out on
 * [PluginContext.projectSearchProvider]. This TYPE exists in the installed
 * api jar, so referencing it gates on minApiVersion 1.0.87 - and the
 * ApiClassLoader takes the newest INSTALLED jar, not the host's pinned one,
 * so a host at the minBossVersion floor with an older installed api jar still
 * lacks the type. The members resolve from the host's pinned copy
 * (parent-first), so they gate on minBossVersion as well.
 */
@HostImplemented
interface ProjectSearchProvider {
    /**
     * Search file contents under the current project.
     *
     * [excludePattern] is applied by the ENGINE, not filtered from the
     * returned list: [maxResults] caps the scan, so a caller that filters the
     * RETURNED list gets "the first N matches, minus the excluded ones" -
     * fewer results than exist, with nothing to say so. Excluding during the
     * walk means the cap applies to matches the caller actually wants.
     *
     * @param query Literal text, or a regular expression when [isRegex]
     * @param pathPattern Optional glob filter on the path relative to the
     * project root (e.g. `"**&#47;*.kt"`); null matches all files
     * @param excludePattern Comma-separated globs; a file matching any of them
     * is never scanned. Same syntax as [pathPattern]. Null or blank excludes
     * nothing
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
        excludePattern: String? = null,
        isRegex: Boolean = false,
        caseSensitive: Boolean = false,
        wholeWord: Boolean = false,
        maxResults: Int = 200
    ): List<FileMatch> = emptyList()

    /**
     * Replace occurrences across an EXPLICIT file list - never project-wide by
     * default. Open buffers are routed through the editor's version-guarded
     * apply path (undoable); closed files are written to disk.
     *
     * The write side carries the same contract as the read side: closed files
     * are written ATOMICALLY (temp file in the same directory, then rename),
     * so an interrupted or failing batch never leaves a truncated source
     * file; and the write preserves what the file had before - line endings
     * (a CRLF file comes back CRLF), encoding and BOM, and file mode. A batch
     * replace that rewrites CRLF as LF or drops the executable bit is a
     * corruption, not a fix, and this interface is the only place that
     * contract can live.
     *
     * @param query Same semantics as [searchInProject.query]
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
     * @param isRegex, caseSensitive, wholeWord Same semantics as on
     * [searchInProject], including the literal-escape and word-boundary edge
     * cases documented there
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
    ): ReplaceSummary = ReplaceSummary(filesReplaced = 0, totalReplacements = 0, dryRun = dryRun)
}

/**
 * One content match (1.0.87). 1-based [line]/[column]; [matchLength] is the
 * matched span in characters - together they are the match range a replace
 * engine and a highlighter both need. [contextLine] is the source line for
 * rendering.
 *
 * The span always fits on ONE line: matching is strictly per-line (see the
 * scanning rules on [ProjectSearchProvider]), so a highlighter slicing
 * [contextLine] from [column] - 1 for [matchLength] characters has no
 * out-of-bounds path. A [endLine]/[endColumn] pair is deliberately absent
 * because the contract does not need it, not because it would be dropped
 * later.
 *
 * Truncation is TAIL-ONLY: [contextLine] always starts at the start of the
 * source line, and only the part AFTER the match end may be cut - the prefix
 * is exactly what a highlighter indexes [column] against, and cutting it
 * would silently re-base the column. A match at column 900,000 of a minified
 * bundle therefore keeps its 900 KB prefix and loses what follows the match.
 * When even the prefix up to the match end exceeds the implementation's
 * budget, it returns the full line or skips the file (the match is not
 * reported); it never truncates the head.
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
 *
 * [dryRun] echoes the request's flag: with it defaulted `true`, a caller that
 * forgets the flag would otherwise render "42 replacements" for a run that
 * touched nothing, and a renderer that sees only the summary (an MCP tool
 * result, a host panel) cannot ask the caller.
 */
@Serializable
data class ReplaceSummary(
    val filesReplaced: Int,
    val totalReplacements: Int,
    val files: List<FileReplaceResult> = emptyList(),
    val dryRun: Boolean = false
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
