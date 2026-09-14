package ai.rever.boss.plugin.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `shouldRouteHeavyweight`, `shouldArmModalInput` and `anchorRectInDp` are each documented in this
 * file's `BossDialog.kt` as pure "so it can be pinned by a test", and until now the only tests were
 * in the host repository, against the host's copy.
 *
 * That is the wrong copy to test. Both repositories ship the same `ai.rever.boss.plugin.ui` package,
 * and a plugin classloader resolves it parent-first, so on a host that predates these types it is
 * **this** copy that executes. The tested copy was the one that never ran on the fallback path, and
 * the untested one was the one that did.
 *
 * The expected values below are deliberately the same numbers the host's `BossDialogRoutingTest`
 * pins. Neither repository can import the other, so agreement cannot be asserted directly; sharing
 * the constants means a divergence shows up as a failing test in whichever repository moved, rather
 * than as a popup landing in the wrong place on a machine nobody is testing on.
 *
 * Composing a `Window` needs a display, so the routing decision is as far as a unit test reaches.
 * That is also where every regression lands, since each of the three inputs suppresses the
 * heavyweight path for a different reason.
 *
 * From BossConsole#143.
 */
class OverlayRoutingTest {

    @Test
    fun `a browser-hosting window with a registered renderer routes heavyweight`() {
        assertTrue(
            shouldRouteHeavyweight(
                useHeavyweightOverlays = true,
                hasRenderer = true,
                hostNeedsHeavyweight = true,
            ),
        )
    }

    @Test
    fun `an OFF_SCREEN install keeps the lightweight path`() {
        assertFalse(
            shouldRouteHeavyweight(
                useHeavyweightOverlays = false,
                hasRenderer = true,
                hostNeedsHeavyweight = true,
            ),
        )
    }

    @Test
    fun `with nothing injected there is nowhere to route to`() {
        assertFalse(
            shouldRouteHeavyweight(
                useHeavyweightOverlays = true,
                hasRenderer = false,
                hostNeedsHeavyweight = true,
            ),
        )
    }

    @Test
    fun `a secondary window stays lightweight even under HARDWARE`() {
        // Settings and the first-run setup window host no browser surface. An always-on-top window
        // sized to the MAIN window would cover the wrong window and keep floating over it, because
        // a heavyweight modal deliberately survives focus moving to another window of the same
        // application.
        assertFalse(
            shouldRouteHeavyweight(
                useHeavyweightOverlays = true,
                hasRenderer = true,
                hostNeedsHeavyweight = false,
            ),
        )
    }

    @Test
    fun `all three inputs are required, so no pair can carry the decision alone`() {
        // Exhaustive over the eight combinations: exactly one routes. A regression that dropped any
        // one of the conjuncts would pass every single-case test above while flipping a case here.
        var routed = 0
        for (overlays in listOf(true, false)) {
            for (renderer in listOf(true, false)) {
                for (needs in listOf(true, false)) {
                    if (shouldRouteHeavyweight(overlays, renderer, needs)) routed++
                }
            }
        }
        assertEquals(1, routed, "exactly one of the eight input combinations may route heavyweight")
    }
}

/**
 * The arming rule for a freshly-opened heavyweight modal.
 *
 * A heavyweight modal is a new window placed under the cursor, so the click that opened it is still
 * in flight when it appears. An earlier version let the arming timer fire unconditionally: a click
 * held longer than the delay, plus the window's own first-frame latency, expired the timer while the
 * button was still down, and the release then chose whichever option sat under the pointer. That
 * reintroduced the bug the guard exists for, intermittently, which is worse than not guarding.
 */
class ModalInputArmingTest {

    @Test
    fun `a held button vetoes arming`() {
        assertFalse(shouldArmModalInput(pointerDown = true))
    }

    @Test
    fun `an idle pointer arms it`() {
        assertTrue(shouldArmModalInput(pointerDown = false))
    }

    @Test
    fun `the answer depends on the button alone, not on which signal is asking`() {
        // Both the pointer handler and the delay timer ask this same question. The function takes
        // one input precisely so there is no path on which "the timer fired" can outrank "a button
        // is down", and this pins that shape rather than only its current answers.
        assertEquals(!true, shouldArmModalInput(pointerDown = true))
        assertEquals(!false, shouldArmModalInput(pointerDown = false))
    }
}

/**
 * The anchor conversion from Compose pixels to AWT logical units.
 *
 * The unit change is the whole point and is easy to miss. Passing pixels straight through put the
 * URL-bar suggestion list at roughly double its intended position on a 2x display, and looked
 * perfectly correct on a 1x one, which is why every case below states its scale factor.
 */
class AnchorRectConversionTest {

    @Test
    fun `at 1x the numbers are unchanged, which is why the bug hid`() {
        assertEquals(
            IntRect(120, 40, 620, 68),
            anchorRectInDp(Offset(120f, 40f), IntSize(500, 28), density = 1f),
        )
    }

    @Test
    fun `at 2x position and size both halve to the same rect`() {
        assertEquals(
            IntRect(120, 40, 620, 68),
            anchorRectInDp(Offset(240f, 80f), IntSize(1000, 56), density = 2f),
        )
    }

    @Test
    fun `a 150 percent display is handled, not just integral scales`() {
        assertEquals(
            IntRect(120, 40, 620, 68),
            anchorRectInDp(Offset(180f, 60f), IntSize(750, 42), density = 1.5f),
        )
    }

    @Test
    fun `the anchor keeps its width, which is what the content is sized to`() {
        // The suggestion list stretched edge to edge because a zero-width anchor left the content
        // inheriting the overlay window's width instead.
        assertEquals(500, anchorRectInDp(Offset(240f, 80f), IntSize(1000, 0), density = 2f).width)
    }

    @Test
    fun `a zero-height anchor still reports its position`() {
        // The anchor Box wraps a zero-size probe, so zero height is the normal case, not an error.
        val rect = anchorRectInDp(Offset(240f, 80f), IntSize(1000, 0), density = 2f)
        assertEquals(40, rect.top)
        assertEquals(40, rect.bottom)
    }

    @Test
    fun `a nonsensical density yields an empty rect rather than a divide by zero`() {
        assertEquals(IntRect.Zero, anchorRectInDp(Offset(10f, 10f), IntSize(10, 10), density = 0f))
        assertEquals(IntRect.Zero, anchorRectInDp(Offset(10f, 10f), IntSize(10, 10), density = -2f))
    }

    @Test
    fun `an unplaced layout reporting NaN does not become a NaN window position`() {
        // positionInWindow() reports Unspecified for a detached or not-yet-placed node, and an
        // Offset carrying NaN would otherwise convert into a window position no platform can honour.
        assertEquals(
            IntRect.Zero,
            anchorRectInDp(Offset(Float.NaN, Float.NaN), IntSize(10, 10), density = 2f),
        )
        assertEquals(
            IntRect.Zero,
            anchorRectInDp(Offset(Float.NaN, 40f), IntSize(10, 10), density = 2f),
        )
        assertEquals(
            IntRect.Zero,
            anchorRectInDp(Offset(120f, Float.NaN), IntSize(10, 10), density = 2f),
        )
    }
}
