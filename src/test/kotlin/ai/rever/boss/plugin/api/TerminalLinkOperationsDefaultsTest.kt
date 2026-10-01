package ai.rever.boss.plugin.api

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse

class TerminalLinkOperationsDefaultsTest {
    @Test
    fun `an unimplemented chooser reports no support`() {
        val operations = Proxy.newProxyInstance(
            SplitViewOperations::class.java.classLoader,
            arrayOf(SplitViewOperations::class.java),
        ) { proxy, method, args ->
            check(method.isDefault) { "Unexpected operation: ${method.name}" }
            InvocationHandler.invokeDefault(proxy, method, *args.orEmpty())
        } as SplitViewOperations

        assertFalse(operations.supportsOpenTerminalLink)
        operations.openTerminalLink("https://example.com", "terminal")
    }
}
