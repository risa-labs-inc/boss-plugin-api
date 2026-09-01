package ai.rever.boss.plugin.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The LIVE BUFFER MODEL defaults are the degradation contract for consumers of a
 * host whose editor-tab plugin has not caught up: readBuffer/observeChanges/
 * focusedDocument degrade to null, applyEdit to an explicit failure, and
 * openEditor/openSplit to "cannot do it here". A later tidy-up that flips one
 * of them - null to empty, false to true, the failure to a silent success -
 * compiles fine and changes what every caller sees, so each default is pinned.
 */
class EditorTabPluginDefaultsTest {

    private val api = object : EditorTabPluginAPI {}

    @Test
    fun `unimplemented reads degrade to null, never to an empty document`() {
        // A null readBuffer tells the caller to fall back to disk; an empty
        // BufferSnapshot would tell it the file is open and blank.
        assertNull(runBlocking { api.readBuffer("a.kt") })
        assertNull(api.observeChanges("a.kt"))
        assertNull(runBlocking { api.focusedDocument() })
    }

    @Test
    fun `unimplemented applyEdit is an explicit failure naming the unsupported reason`() {
        val result =
            runBlocking {
                api.applyEdit(
                    path = "a.kt",
                    startLine = 1,
                    startCol = 1,
                    endLine = 1,
                    endCol = 1,
                    newText = "",
                    expectedVersion = 1L,
                )
            }

        assertFalse(result.applied)
        assertNull(result.newVersion)
        assertEquals(EditResult.REASON_UNSUPPORTED, result.reason)
    }

    @Test
    fun `unimplemented openEditor and openSplit refuse rather than claiming success`() {
        assertFalse(runBlocking { api.openEditor("a.kt") })
        assertFalse(runBlocking { api.openSplit("a.kt") })
    }

    @Test
    fun `the reason literals are pinned - callers pattern-match on them`() {
        // REASON_STALE is the retry-after-re-read signal: a later rewording would
        // silently kill every `reason == REASON_STALE` retry loop, the same hazard
        // the EXTRAS_KEY_MODEL_OVERRIDE pin guards against in AiGatewayTypesTest.
        assertEquals("stale", EditResult.REASON_STALE)
        assertEquals("unsupported", EditResult.REASON_UNSUPPORTED)
    }
}
