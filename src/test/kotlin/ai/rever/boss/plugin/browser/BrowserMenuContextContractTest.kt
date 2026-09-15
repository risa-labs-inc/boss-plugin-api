package ai.rever.boss.plugin.browser

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BrowserMenuContextContractTest {
    @Test
    fun `legacy commands remain and default overloads never misroute an explicit token`() {
        val calls = mutableListOf<String>()
        val handle = Proxy.newProxyInstance(
            BrowserHandle::class.java.classLoader,
            arrayOf(BrowserHandle::class.java)
        ) { proxy, method, args ->
            if (method.isDefault) {
                InvocationHandler.invokeDefault(proxy, method, *(args ?: emptyArray()))
            } else {
                calls += method.name
                null
            }
        } as BrowserHandle
        val token = object : BrowserMenuContext {}
        for (name in listOf("copySelection", "paste", "cut", "selectAll")) {
            val legacy = BrowserHandle::class.java.getMethod(name)
            val contextual = BrowserHandle::class.java.getMethod(name, BrowserMenuContext::class.java)
            assertTrue(contextual.isDefault, "$name must support old implementations")
            calls.clear()
            legacy.invoke(handle)
            contextual.invoke(handle, null)
            assertEquals(listOf(name, name), calls)
            contextual.invoke(handle, token)
            assertEquals(listOf(name, name), calls, "An unknown token must never target the focused frame")
        }
    }

    @Test
    fun `menu token preserves legacy construction and copy signatures`() {
        val oldTypes = arrayOf(
            String::class.java, String::class.java, Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
            String::class.java, String::class.java, String::class.java, FormFieldInfo::class.java
        )
        BrowserContextMenuInfo::class.java.getConstructor(*oldTypes)
        BrowserContextMenuInfo::class.java.getMethod("copy", *oldTypes)
        val token = object : BrowserMenuContext {}
        val menu = BrowserContextMenuInfo(selectedText = "selected", menuContext = token)
        assertSame(token, menu.menuContext)
        assertEquals("selected", menu.selectedText)
        assertNull(BrowserContextMenuInfo().menuContext)
        assertNull(menu.copy().menuContext, "Copied contents must not retain a transient frame token")
        assertEquals(menu, menu.copy())
    }
}
