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
        // The callback's FIRST argument is the posting document's URL, supplied by the host.
        //
        // The reason is NOT that the channel is forgeable - under the parameter design the bridge
        // never reaches window, so it largely is not. It is that a URL inside the JSON is only as
        // trustworthy as whatever wrote it, and the alternative of reading the handle's URL AFTER
        // the fact can be overtaken by the navigation the event itself started: a credential typed
        // on one site then attributed to the site the login landed on, which for a cross-domain
        // sign-in means storing it against the wrong site entirely.
        //
        // `single` rather than `first`, though not for the reason first written down: an
        // origin-scoping overload would take three parameters and would not match this filter at
        // all. What `single` actually guards is a future TWO-parameter overload - a spec object,
        // say - which `first` would pick nondeterministically and then assert against.
        val method = declared.single { it.parameterCount == 2 }
        assertEquals(String::class.java, method.parameterTypes[0])
        assertEquals(
            "kotlin.jvm.functions.Function2",
            method.parameterTypes[1].name,
            "the callback is no longer a two-argument function, so the document URL is gone",
        )
        // NOT covered, and worth saying so rather than letting a reader assume it is: both Function2
        // parameters erase to Object, so reflection cannot see that the URL comes FIRST. That order
        // is enforced by the KDoc alone.
    }

    @Test
    fun `uninstalling is its own method, so a null pair cannot mean two things`() {
        // The nullable-pair shape admitted setPageEventScript(script, null) and
        // setPageEventScript(null, callback), both of which could only be caller bugs and both of
        // which silently uninstalled. Reshaping after a release costs a BossConsole release, since
        // BrowserHandle is @HostImplemented.
        val clear = BrowserHandle::class.java.methods.filter { it.name == "clearPageEventScript" }
        assertTrue(clear.isNotEmpty(), "clearPageEventScript is gone; uninstalling has no verb")
        assertTrue(
            clear.none { Modifier.isAbstract(it.modifiers) },
            "clearPageEventScript has no default body, so an older host would throw rather than no-op",
        )
    }

    @Test
    fun `absence is distinguishable from silence`() {
        // Silence has three causes here: an older host, a host-side drop, and the user doing
        // nothing. Without this flag a consumer cannot tell the first from the others and has to
        // treat its whole feature as best-effort. Same shape as supportsHiddenEntries /
        // supportsBulkAdd.
        val supports = BrowserHandle::class.java.methods.filter { it.name == "getSupportsPageEventScript" }
        assertTrue(supports.isNotEmpty(), "supportsPageEventScript is gone")
        assertTrue(
            supports.none { Modifier.isAbstract(it.modifiers) },
            "supportsPageEventScript must default to false for hosts that do not implement the channel",
        )
    }

    @Test
    fun `the bridge name and its method are exactly what already-built consumers compiled in`() {
        // PAGE_EVENT_BRIDGE is `const`, so its value is inlined at every call site. A rename would
        // not be a compile error for anyone: already-built plugins keep posting to the old name and
        // the host installs the new one, which is a dead channel with nothing in any log. This
        // assertion is the only thing standing between a rename and that outcome.
        assertEquals("__bossPageEvent", PAGE_EVENT_BRIDGE)
        // The method name is the same class of hazard as the property name, which the first draft
        // left in prose: renaming it host-side is a compile error at no consumer, and every built
        // plugin posts into a method that no longer exists.
        assertEquals("emit", PAGE_EVENT_EMIT)
    }
}
