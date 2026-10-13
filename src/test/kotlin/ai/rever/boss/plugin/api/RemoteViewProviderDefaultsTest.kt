package ai.rever.boss.plugin.api

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertNull

class RemoteViewProviderDefaultsTest {
    @Test
    fun `a host that predates remote view reports no provider`() {
        val context = Proxy.newProxyInstance(
            PluginContext::class.java.classLoader,
            arrayOf(PluginContext::class.java),
        ) { proxy, method, args ->
            check(method.isDefault) { "Unexpected operation: ${method.name}" }
            InvocationHandler.invokeDefault(proxy, method, *args.orEmpty())
        } as PluginContext

        assertNull(context.remoteViewProvider)
    }
}
