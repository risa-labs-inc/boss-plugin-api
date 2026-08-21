package ai.rever.boss.plugin.browser

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two halves of the page-event channel that fail *silently* if someone tidies them.
 *
 * Same reasoning as [FillCredentialsNoOpTest], which is the precedent: a defaulted member on a
 * `@HostImplemented` interface and a bare `const` both look like clutter to a later reader, and
 * neither produces a compile error anywhere when it goes wrong.
 *
 * Reflection rather than a call, for the reason that test gives: exercising the body would need a
 * fake implementing all of [BrowserHandle], and that fake becomes a standing tax on every future
 * member.
 */
class PageEventScriptContractTest {
    private val declared =
        BrowserHandle::class.java.methods.filter { it.name == "setPageEventScript" }

    @Test
    fun `the method is declared`() {
        assertTrue(declared.isNotEmpty(), "setPageEventScript is gone from BrowserHandle")
    }

    @Test
    fun `it is not abstract, so an older host degrades to silence instead of AbstractMethodError`() {
        // The whole gating story depends on this. A plugin declaring minBossVersion resolves the
        // HOST's copy of BrowserHandle parent-first, and on a host that predates the implementation
        // the default body is what it lands on. Make this abstract and that same plugin throws
        // AbstractMethodError at the call site instead of quietly doing nothing - which turns a
        // feature that is merely unavailable into a crash inside a coroutine.
        val concrete = declared.filterNot { Modifier.isAbstract(it.modifiers) }
        assertTrue(
            concrete.isNotEmpty(),
            "setPageEventScript has no default body; a caller on an older host would get " +
                "AbstractMethodError rather than silence",
        )
    }

    @Test
    fun `it takes the script and a two-argument callback`() {
        // The callback's FIRST argument is the posting document's URL, supplied by the host. It is
        // the only trustworthy attribution a consumer has: the bridge is a public property on
        // window, so any page script can post, and a URL inside the JSON is whatever the poster
        // chose to write. Collapsing this back to a one-argument callback would leave consumers
        // guessing which origin an event belongs to - and the first consumer uses it to decide
        // which site a password gets stored against.
        val method = declared.first()
        assertEquals(2, method.parameterCount, "signature changed: ${method.parameterTypes.toList()}")
        assertEquals(String::class.java, method.parameterTypes[0])
        assertEquals(
            "kotlin.jvm.functions.Function2",
            method.parameterTypes[1].name,
            "the callback is no longer a two-argument function, so the document URL is gone",
        )
    }

    @Test
    fun `the bridge name is exactly what already-built consumers compiled in`() {
        // PAGE_EVENT_BRIDGE is `const`, so its value is inlined at every call site. A rename would
        // not be a compile error for anyone: already-built plugins keep posting to the old name and
        // the host installs the new one, which is a dead channel with nothing in any log. This
        // assertion is the only thing standing between a rename and that outcome.
        assertEquals("__bossPageEvent", PAGE_EVENT_BRIDGE)
    }
}
