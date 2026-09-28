package ai.rever.boss.plugin.ui

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalTitleBarBridgeTest {
    @Test
    fun `provider arbitration is ordered and callbar owners have separate remembered state`() = runBlocking {
        val first = EqualOwner()
        val second = EqualOwner()
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(EmptyApplier(), recomposer)
        var provider = ""
        var value = ""
        fun callBar(label: String): @androidx.compose.runtime.Composable () -> Unit = {
            val remembered = remember { label }
            SideEffect { value = remembered }
        }
        try {
            TerminalTitleBarBridge.hostWindow("composed", true)
            TerminalTitleBarBridge.registerProvider(first) { SideEffect { provider = "first" } }
            TerminalTitleBarBridge.registerProvider(second) { SideEffect { provider = "second" } }
            composition.setContent { TerminalTitleBarBridge.Content("composed") }
            assertEquals("second", provider)
            TerminalTitleBarBridge.unregisterProvider(second)
            composition.setContent { TerminalTitleBarBridge.Content("composed") }
            assertEquals("first", provider)
            TerminalTitleBarBridge.publishCallBar("composed", first, callBar("first"))
            composition.setContent { TerminalTitleBarBridge.CallBarContent("composed") }
            assertEquals("first", value)
            TerminalTitleBarBridge.publishCallBar("composed", second, callBar("second"))
            composition.setContent { TerminalTitleBarBridge.CallBarContent("composed") }
            assertEquals("second", value)
            TerminalTitleBarBridge.removeCallBar(second)
            composition.setContent { TerminalTitleBarBridge.CallBarContent("composed") }
            assertEquals("first", value)
        } finally {
            composition.dispose()
            recomposer.cancel()
            TerminalTitleBarBridge.unregisterProvider(first)
            TerminalTitleBarBridge.unregisterProvider(second)
            TerminalTitleBarBridge.removeCallBar(first)
            TerminalTitleBarBridge.removeCallBar(second)
            TerminalTitleBarBridge.hostWindow("composed", false)
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    private class EqualOwner {
        override fun equals(other: Any?) = other is EqualOwner
        override fun hashCode() = 0
    }

    private fun action(id: String) = TerminalTitleBarAction(
        id, id, "", ImageVector.Builder(id, 1.dp, 1.dp, 1f, 1f).build(), false, {},
    )

    @Test
    fun `changed publications win and equal owners remain independent`() {
        val first = EqualOwner()
        val second = EqualOwner()
        val a = listOf(action("first"))
        val b = listOf(action("second"))
        val changed = listOf(action("changed"))
        try {
            TerminalTitleBarBridge.publish("one", first, true, a)
            TerminalTitleBarBridge.publish("one", second, true, b)
            assertEquals(b, TerminalTitleBarBridge.actions("one"))
            TerminalTitleBarBridge.publish("one", first, true, a)
            assertEquals(b, TerminalTitleBarBridge.actions("one"), "unchanged effects do not steal ownership")
            TerminalTitleBarBridge.publish("one", first, true, changed)
            assertEquals(changed, TerminalTitleBarBridge.actions("one"))
            TerminalTitleBarBridge.remove(first)
            assertEquals(b, TerminalTitleBarBridge.actions("one"))
            TerminalTitleBarBridge.publish("two", second, true, b)
            assertTrue(TerminalTitleBarBridge.actions("one").isEmpty())
            assertEquals(b, TerminalTitleBarBridge.actions("two"))
            TerminalTitleBarBridge.publish("two", second, false, b)
            assertTrue(TerminalTitleBarBridge.actions("two").isEmpty())
        } finally {
            TerminalTitleBarBridge.remove(first)
            TerminalTitleBarBridge.remove(second)
        }
    }

    @Test
    fun `callbar cleanup is identity scoped and cannot revoke host claim`() {
        val first = EqualOwner()
        val second = EqualOwner()
        try {
            TerminalTitleBarBridge.hostWindow("call-window", true)
            TerminalTitleBarBridge.publishCallBar("call-window", first) {}
            TerminalTitleBarBridge.publishCallBar("call-window", second) {}
            TerminalTitleBarBridge.removeCallBar(first)
            assertTrue(TerminalTitleBarBridge.hasCallBar("call-window"))
            assertFalse(TerminalTitleBarBridge.hasCallBar("other"))
            TerminalTitleBarBridge.removeCallBar(second)
            assertFalse(TerminalTitleBarBridge.hasCallBar("call-window"))
            assertTrue(TerminalTitleBarBridge.isHosted("call-window"))
        } finally {
            TerminalTitleBarBridge.removeCallBar(first)
            TerminalTitleBarBridge.removeCallBar(second)
            TerminalTitleBarBridge.hostWindow("call-window", false)
        }
        assertFalse(TerminalTitleBarBridge.isHosted("call-window"))
    }
}
