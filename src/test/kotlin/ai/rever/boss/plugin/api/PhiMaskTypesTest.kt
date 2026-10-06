package ai.rever.boss.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhiMaskTypesTest {
    @Test
    fun `result toString omits text and extras values`() {
        val s = PhiMaskResult("[NAME] SECRET-TEXT", listOf(PhiDetection(0, 4, "NAME", 0.9)), mapOf("k" to "SECRET-EXTRA")).toString()

        assertFalse("SECRET-TEXT" in s)
        assertFalse("SECRET-EXTRA" in s)
        assertTrue("detections=1" in s)
        assertTrue("k" in s)
    }

    @Test
    fun `profile wire names round trip`() {
        assertEquals(listOf("fast", "realtime", "balanced", "strict"), PhiMaskProfile.entries.map { it.wireName })
        PhiMaskProfile.entries.forEach { assertEquals(it, PhiMaskProfile.fromWireName(it.wireName)) }
        assertNull(PhiMaskProfile.fromWireName("FAST"))
    }

    @Test
    fun `exception carries code and message only`() {
        val e = PhiMaskException(PhiMaskException.UNAVAILABLE, "PHI masking is unavailable.")

        assertEquals("UNAVAILABLE", e.code)
        assertNull(e.cause)
    }

    @Test
    fun `extras default to empty`() {
        assertTrue(PhiMaskResult("x", emptyList()).extras.isEmpty())
        assertTrue(PhiDetection(0, 1, "NAME", 1.0).extras.isEmpty())
        assertTrue(PhiMaskStatus(PhiMaskState.NOT_STARTED, emptyList()).extras.isEmpty())
    }
}
