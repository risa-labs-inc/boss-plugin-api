package ai.rever.boss.plugin.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The six-argument `searchInProject` is a default body over the seven-argument
 * primary. The delegation direction is the contract: a host that implements only
 * the exclude-aware engine must still serve the ergonomic entry point. This pins
 * it, because flipped delegation compiles fine and returns empty forever.
 */
class ProjectSearchProviderDefaultsTest {

    private val match = FileMatch(path = "a.kt", line = 1, column = 1, matchLength = 2, contextLine = "ab")

    @Test
    fun `overriding only the primary overload serves the six-argument entry too`() {
        var seenExclude: String? = "sentinel"
        val engineOnly =
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
                    seenExclude = excludePattern
                    return listOf(match)
                }
            }

        val results = runBlocking { engineOnly.searchInProject("ab") }

        assertEquals(listOf(match), results)
        // The delegation passes "no excludes", not "" or a stale pattern.
        assertNull(seenExclude)
    }
}
