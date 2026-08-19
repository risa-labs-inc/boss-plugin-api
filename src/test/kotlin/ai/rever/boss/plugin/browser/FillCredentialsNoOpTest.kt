package ai.rever.boss.plugin.browser

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `BrowserHandle.fillCredentials` is a deprecated no-op awaiting removal. Both halves of that
 * matter enough to pin, and this test exists because a no-op is exactly the kind of code someone
 * later deletes as pointless.
 *
 * **It must still be declared.** Deleting it would make every caller compiled against an older api
 * throw `NoSuchMethodError` at the call site. fluck-browser 1.2.19 guards its call and would
 * degrade to nothing, but 1.2.18 and earlier call it bare inside a `launch`, where an `Error`
 * reaches the coroutine uncaught and the host tears the whole plugin down - closing every open
 * browser tab. `minBossVersion` gates plugin updates and the api plugin has no such gate, so a user
 * on an older host can receive this api while pinned to a build that would crash on it.
 *
 * **It must carry a body.** An abstract member would still link for a caller, but every
 * implementation compiled against an older api would keep its own override - which is the host
 * heuristic being retired, the one that wrote a password into a `display: none` input on Google's
 * sign-in page. The default body is what makes dropping those overrides safe.
 *
 * Asserted by reflection rather than by calling it: exercising the body would need a fake
 * implementing all of `BrowserHandle`, and that fake would have to be updated every time the
 * interface grows a member - a standing tax for a method scheduled for deletion.
 */
class FillCredentialsNoOpTest {
    private val declared =
        BrowserHandle::class.java.methods.filter { it.name == "fillCredentials" }

    @Test
    fun `the method is still declared, so an old caller cannot hit NoSuchMethodError`() {
        assertTrue(declared.isNotEmpty(), "fillCredentials is gone from BrowserHandle")
    }

    @Test
    fun `it is not abstract, so an implementor can drop its override`() {
        val concrete = declared.filterNot { Modifier.isAbstract(it.modifiers) }
        assertTrue(
            concrete.isNotEmpty(),
            "fillCredentials has no default body; implementors would have to keep the old heuristic",
        )
    }

    @Test
    fun `the default-argument bridge survives too`() {
        // A caller that omitted `fillBoth` compiled against `fillCredentials$default`. Losing that
        // synthetic breaks those call sites even while the method itself resolves.
        assertTrue(
            BrowserHandle::class.java.methods.any { it.name == "fillCredentials\$default" },
            "the \$default bridge is gone; callers omitting fillBoth would not link",
        )
    }

    // The @Deprecated annotation itself is deliberately NOT asserted here. kotlin.Deprecated has
    // BINARY retention, so it is written to the class file but is not visible to runtime
    // reflection - an attempt to check it here failed for that reason rather than because the
    // annotation was missing. Verifying it would mean parsing bytecode or Kotlin metadata, which
    // is a lot of machinery to guard one annotation on a method scheduled for deletion. The
    // compiler already enforces the part that matters: callers get a deprecation warning.
}
