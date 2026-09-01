package ai.rever.boss.plugin.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `searchInProject`'s default arguments are part of the contract: the host
 * engine sees them verbatim, and a tidy-up that flips one (maxResults 200 to
 * 100, a false to true, a null to "") changes what every caller gets with no
 * signature change for apiCheck to see. Every default is pinned below, as is
 * the replaceInProject dryRun default (the one flip that changes what hits
 * disk rather than what is returned).
 */
class ProjectSearchProviderDefaultsTest {

    private val match = FileMatch(path = "a.kt", line = 1, column = 1, matchLength = 2, contextLine = "ab")

    @Test
    fun `searchInProject defaults are the values the engine contract assumes`() {
        var seen: List<Any?>? = null
        val engine =
            object : ProjectSearchProvider {
                override suspend fun searchInProject(
                    query: String,
                    pathPattern: String?,
                    excludePattern: String?,
                    isRegex: Boolean,
                    caseSensitive: Boolean,
                    wholeWord: Boolean,
                    maxResults: Int,
                ): List<FileMatch> {
                    seen =
                        listOf(
                            query,
                            pathPattern,
                            excludePattern,
                            isRegex,
                            caseSensitive,
                            wholeWord,
                            maxResults,
                        )
                    return listOf(match)
                }
            }

        val results = runBlocking { engine.searchInProject("ab") }

        assertEquals(listOf(match), results)
        // No pattern, no excludes, literal case-insensitive matching, cap 200.
        assertEquals(listOf("ab", null, null, false, false, false, 200), seen)
    }

    @Test
    fun `replaceInProject defaults to dryRun = true - a later tidy-up must not write to disk`() {
        // dryRun is the one default where a flip changes what hits disk, not
        // what is returned: the caller omitted the argument, so the value the
        // engine sees is exactly the default body's contract.
        var seenDryRun: Boolean? = null
        var seenFiles: List<String>? = null
        val counting =
            object : ProjectSearchProvider {
                override suspend fun searchInProject(
                    query: String,
                    pathPattern: String?,
                    excludePattern: String?,
                    isRegex: Boolean,
                    caseSensitive: Boolean,
                    wholeWord: Boolean,
                    maxResults: Int,
                ): List<FileMatch> = emptyList()

                override suspend fun replaceInProject(
                    query: String,
                    replacement: String,
                    files: List<String>,
                    isRegex: Boolean,
                    caseSensitive: Boolean,
                    wholeWord: Boolean,
                    dryRun: Boolean,
                ): ReplaceSummary {
                    seenDryRun = dryRun
                    seenFiles = files
                    return ReplaceSummary(filesReplaced = 0, totalReplacements = 0, dryRun = dryRun)
                }
            }

        val summary = runBlocking { counting.replaceInProject("a", "b", listOf("a.kt")) }

        assertEquals(true, seenDryRun)
        assertEquals(listOf("a.kt"), seenFiles)
        // The summary echoes the flag it was produced under.
        assertEquals(true, summary.dryRun)
    }
}