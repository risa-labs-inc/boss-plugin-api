package ai.rever.boss.plugin.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DaemonServiceDefaultsTest {
    @Test
    fun `existing contexts expose no daemon provider by default`() {
        val context = object : PluginContext {
            override val panelRegistry: PanelRegistry get() = error("Unused")
            override val tabRegistry: TabRegistry get() = error("Unused")
            override val pluginScope: CoroutineScope get() = error("Unused")
        }
        assertNull(context.daemonServiceProvider)
    }

    @Test
    fun `provider configuration and connection payload defaults remain empty`() = runBlocking<Unit> {
        val connection = object : DaemonServiceConnection {
            override val endpoints = emptyMap<String, String>()
            override suspend fun request(method: String, payload: String): String = payload
            override suspend fun stop() {}
        }
        val provider = object : DaemonServiceProvider {
            override suspend fun connect(serviceId: String, entryPoint: String,
                configuration: Map<String, String>): DaemonServiceConnection {
                assertEquals(emptyMap(), configuration)
                return connection
            }
        }
        assertEquals("", provider.connect("worker", "example.Worker").request("status"))
    }
}
