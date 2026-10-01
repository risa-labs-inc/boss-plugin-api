package ai.rever.boss.plugin.api

import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins the unsupported-capability behavior for hosts that expose the new API members. */
class McpExecutionDefaultsTest {
    private val context = object : PluginContext {
        override val panelRegistry: PanelRegistry get() = error("Must not access panel registry")
        override val tabRegistry: TabRegistry get() = error("Must not access tab registry")
        override val pluginScope: CoroutineScope get() = error("Must not access plugin scope")
    }

    private val observer = object : McpToolExecutionObserver {
        override val observerId: String get() = error("Unsupported registration must not inspect observer")
        override fun onExecutionStarted(request: McpExecutionRequest) = error("Must not invoke observer")
        override fun onExecutionFinished(request: McpExecutionRequest, outcome: McpExecutionOutcome) =
            error("Must not invoke observer")
    }

    @Test
    fun `unsupported registration returns false without accessing plugin code`() {
        repeat(2) { assertFalse(context.registerMcpToolExecutionObserver(observer)) }
    }

    @Test
    fun `unsupported cleanup is harmless before and after failed registration`() {
        context.unregisterMcpToolExecutionObserver("unknown")
        assertFalse(context.registerMcpToolExecutionObserver(observer))
        repeat(2) { context.unregisterMcpToolExecutionObserver("unknown") }
    }

    @Test
    fun `registration and cleanup remain JVM defaults for existing host implementations`() {
        assertTrue(PluginContext::class.java.getMethod(
            "registerMcpToolExecutionObserver", McpToolExecutionObserver::class.java
        ).isDefault)
        assertTrue(PluginContext::class.java.getMethod(
            "unregisterMcpToolExecutionObserver", String::class.java
        ).isDefault)
    }
}
