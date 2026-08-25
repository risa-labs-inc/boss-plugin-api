package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * As with [AiGatewayTypesTest]: `apiCheck` guards the shape of these declarations, so
 * these cover the hand-written behaviour it cannot see.
 *
 * The rule worth pinning here is [allowsCancel]. It used to be prose in this file and
 * a separate expression in both the host and the Toolbox - two copies that can drift,
 * with "Cancel offered mid-jar-swap" as the failure mode.
 */
class DownloadCenterTypesTest {
    /** The smallest thing that satisfies the interface, for the default-parameter cases. */
    private class MinimalCenter : DownloadCenterProvider {
        override val transfers: StateFlow<List<TransferInfo>> = MutableStateFlow(emptyList())

        var lastId: String? = null
        var lastTitle: String? = null
        var lastKind: TransferKind? = null
        var lastDetail: String? = null
        var lastOnCancel: (() -> Unit)? = null

        override fun begin(
            id: String,
            title: String,
            kind: TransferKind,
            detail: String?,
            onCancel: (() -> Unit)?
        ): TransferHandle {
            lastId = id
            lastTitle = title
            lastKind = kind
            lastDetail = detail
            lastOnCancel = onCancel
            return NoopHandle
        }
    }

    private object NoopHandle : TransferHandle {
        override fun progress(fraction: Float) = Unit

        override fun phase(phase: TransferPhase) = Unit

        override fun done() = Unit
    }

    @Test
    fun `installing is the only phase that forbids cancel`() {
        // Abandoning a jar swap leaves the plugin unloaded. Everything else can be
        // abandoned: a download is bytes, and a downloaded update is a file to delete.
        assertFalse(TransferPhase.INSTALLING.allowsCancel)
        TransferPhase.entries
            .filter { it != TransferPhase.INSTALLING }
            .forEach { assertTrue(it.allowsCancel, "$it should be abandonable") }
    }

    @Test
    fun `allowsCancel is the phase half, not the answer`() {
        val noAction =
            TransferInfo(
                id = "p",
                title = "Docker",
                kind = TransferKind.PLUGIN_INSTALL,
                phase = TransferPhase.DOWNLOADING,
            )

        // The phase permits it; the transfer still is not cancellable, because no
        // cancel action was supplied. A renderer reaching for the phase alone would
        // offer a Cancel that does nothing.
        assertTrue(noAction.phase.allowsCancel)
        assertFalse(noAction.cancellable)
    }

    @Test
    fun `a transfer is described by id title kind and phase alone`() {
        val info = TransferInfo(id = "p", title = "Docker", kind = TransferKind.PLUGIN_INSTALL, phase = TransferPhase.PREPARING)

        // The three optional fields default to "nothing known yet", which is what a
        // transfer looks like before its first byte arrives.
        assertNull(info.detail)
        assertNull(info.progress, "indeterminate until a size is known")
        assertFalse(info.cancellable, "the host derives this; a reporter never sets it")
    }

    @Test
    fun `begin can be called with only the required arguments`() {
        val center = MinimalCenter()

        center.begin(id = "p", title = "Docker", kind = TransferKind.PLUGIN_UPDATE)

        // Both optionals sit after the required parameters, so their defaults are
        // reachable positionally - the reason `detail` is not second.
        assertEquals("p", center.lastId)
        assertEquals(TransferKind.PLUGIN_UPDATE, center.lastKind)
        assertNull(center.lastDetail)
        assertNull(center.lastOnCancel, "no cancel action means no Cancel is offered")
    }

    @Test
    fun `a cancel action is carried through to the host`() {
        val center = MinimalCenter()
        var cancelled = false

        center.begin("p", "Docker", TransferKind.PLUGIN_INSTALL, detail = "1 of 3", onCancel = { cancelled = true })

        assertEquals("1 of 3", center.lastDetail)
        center.lastOnCancel?.invoke()
        assertTrue(cancelled)
    }

    @Test
    fun `every kind and phase is named, so a new one cannot be added silently`() {
        // Both enums are @HostImplemented: the host switches on them, so a new constant
        // is a host-contract change. This fails when one is added without a decision.
        assertEquals(
            listOf("PLUGIN_INSTALL", "PLUGIN_UPDATE", "APP_UPDATE", "OTHER"),
            TransferKind.entries.map { it.name },
        )
        assertEquals(
            listOf("PREPARING", "DOWNLOADING", "INSTALLING", "READY_TO_INSTALL"),
            TransferPhase.entries.map { it.name },
        )
    }
}
