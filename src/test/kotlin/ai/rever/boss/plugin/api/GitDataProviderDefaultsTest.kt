package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `logGraphFor`'s default body routes a null/blank ref to `logGraph` and everything
 * else to empty. Both branches are hand-written behaviour `apiCheck` cannot see, and
 * the delegation direction is exactly the kind of thing a later refactor flips.
 */
class GitDataProviderDefaultsTest {

    private val node =
        GitCommitNodeData(
            hash = "abc123",
            shortHash = "abc123",
            subject = "subject",
            author = "author",
            authorEmail = "author@example.com",
            date = 0L,
            refs = emptyList(),
            parents = emptyList(),
        )

    /** Overrides ONLY logGraph, like a host built before logGraphFor existed. */
    private val provider =
        object : GitDataProvider {
            override val fileStatus: StateFlow<List<GitFileStatusData>> = MutableStateFlow(emptyList())
            override val commitLog: StateFlow<List<GitCommitInfoData>> = MutableStateFlow(emptyList())
            override val isGitRepository: StateFlow<Boolean> = MutableStateFlow(false)
            override val isLoading: StateFlow<Boolean> = MutableStateFlow(false)

            override suspend fun refreshStatus() {}

            override suspend fun refreshLog(limit: Int) {}

            override suspend fun stage(filePath: String) = GitOperationResultData.Success()

            override suspend fun unstage(filePath: String) = GitOperationResultData.Success()

            override suspend fun stageAll() = GitOperationResultData.Success()

            override suspend fun unstageAll() = GitOperationResultData.Success()

            override suspend fun discardChanges(filePath: String) = GitOperationResultData.Success()

            override suspend fun cherryPick(commitHash: String) = GitOperationResultData.Success()

            override suspend fun revert(commitHash: String) = GitOperationResultData.Success()

            override suspend fun checkout(ref: String) = GitOperationResultData.Success()

            override fun getCurrentProjectPath(): String? = null

            override fun openFile(filePath: String, windowId: String) {}

            override suspend fun logGraph(limit: Int) = listOf(node)
        }

    @Test
    fun `a null or blank ref means HEAD, so it reaches logGraph`() {
        runBlocking {
            assertEquals(listOf(node), provider.logGraphFor(null))
            assertEquals(listOf(node), provider.logGraphFor(""))
            assertEquals(listOf(node), provider.logGraphFor("  "))
        }
    }

    @Test
    fun `a named ref does NOT fall back to HEAD's graph`() {
        // Falling back would silently draw the wrong branch; empty is the honest answer
        // from an implementor that cannot scope by ref.
        runBlocking {
            assertTrue(provider.logGraphFor("main").isEmpty())
        }
    }

    @Test
    fun `the write-side defaults refuse with Error, never a silent Success`() {
        // A UI `when`ing over GitOperationResultData renders Success as "done";
        // below the floor the honest answer is Error, and a flip to Success()
        // would tell the user a commit/fetch/pull/push happened that did not.
        runBlocking {
            assertTrue(provider.commit("message") is GitOperationResultData.Error)
            assertTrue(provider.fetch() is GitOperationResultData.Error)
            assertTrue(provider.pull() is GitOperationResultData.Error)
            assertTrue(provider.push() is GitOperationResultData.Error)
        }
    }

    @Test
    fun `the read-side defaults are empty, not throwing`() {
        // Empty (not throwing) is the contract: a panel below the floor
        // renders "nothing to show"; a throw would take the whole panel down.
        runBlocking {
            assertTrue(provider.diffFile("a.kt").isEmpty())
            assertTrue(provider.diffRef("abc123").isEmpty())
            assertTrue(provider.diffBetween("a", "b").isEmpty())
            assertTrue(provider.diffNames().isEmpty())
            assertTrue(provider.branches().isEmpty())
        }
    }

    @Test
    fun `the openDiff default is a no-op, even for a blank window`() {
        // A no-op that is not throwing: a caller below the floor clicks and
        // nothing happens, which the UI can surface - a throw cannot be.
        provider.openDiff("a.kt", windowId = "")
    }
}
