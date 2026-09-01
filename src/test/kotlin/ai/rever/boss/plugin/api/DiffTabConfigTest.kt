package ai.rever.boss.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cast contract the KDoc mandates: the renderer reads its diff tab's
 * [TabInfo] as `(tabInfo as? DiffTabConfig)` - a SAFE cast, null below the
 * host floor or for a foreign tab type, never a ClassCastException at
 * tab-open time.
 */
class DiffTabConfigTest {

    private val diffTabInfo: Any =
        object : DiffTabConfig {
            override val filePath = "a.kt"
            override val staged = false
            override val fromRef: String? = null
            override val toRef: String? = null
        }

    @Test
    fun `an implementing tabInfo reads back through the safe cast`() {
        val read: DiffTabConfig? = diffTabInfo as? DiffTabConfig

        // The cast succeeds for the implementing type and the fields are
        // readable - a wrong cast target would be a ClassCastException here.
        assertTrue(read is DiffTabConfig)
        assertEquals("a.kt", read!!.filePath)
    }

    @Test
    fun `a non-implementing tabInfo yields null, not a ClassCastException`() {
        val foreignTab: Any = "some-other-tab"

        assertNull(foreignTab as? DiffTabConfig)
    }
}
