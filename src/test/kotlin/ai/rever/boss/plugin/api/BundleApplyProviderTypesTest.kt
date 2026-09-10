package ai.rever.boss.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behaviour apiCheck cannot see: defaults on the stable apply subset and the
 * literacy-reject contract a host impl is expected to mirror.
 */
class BundleApplyProviderTypesTest {
    private class RecordingProvider : BundleApplyProvider {
        override val providerId: String = "test-recording"
        override val isHostBacked: Boolean = true

        override fun applyBundle(request: BundleApplyRequest): BundleApplyOutcome {
            if (!request.literacyAcknowledged) {
                return BundleApplyOutcome(
                    ok = false,
                    status = BundleApplyStatus.Rejected,
                    message = "literacy required",
                    bundleId = request.bundleId,
                    providerId = providerId,
                    pluginsRequested = request.plugins.map { it.pluginId },
                    mcpsRequested = request.mcps.map { it.name },
                    errors = listOf("literacyAcknowledged=false"),
                )
            }
            return BundleApplyOutcome(
                ok = true,
                status = BundleApplyStatus.Applied,
                message = "ok",
                bundleId = request.bundleId,
                providerId = providerId,
                pluginsRequested = request.plugins.map { it.pluginId },
                pluginsEnabled = request.plugins.map { it.pluginId },
                mcpsRequested = request.mcps.map { it.name },
                mcpsAllowlisted = request.mcps.map { it.name },
            )
        }
    }

    @Test
    fun `plugin and mcp specs keep schema-friendly defaults`() {
        val plugin = BundleApplyPluginSpec(pluginId = "ai.rever.boss.plugin.demo")
        assertEquals("*", plugin.version)
        assertFalse(plugin.optional)

        val mcp = BundleApplyMcpSpec(name = "boss")
        assertEquals("community", mcp.kind)
        assertEquals(null, mcp.toolPrefix)
        assertTrue(mcp.required)
    }

    @Test
    fun `provider rejects when literacy not acknowledged`() {
        val outcome =
            RecordingProvider().applyBundle(
                BundleApplyRequest(
                    bundleId = "demo.builder",
                    mode = BundleApplyMode.Builder,
                    plugins = listOf(BundleApplyPluginSpec("p1")),
                    mcps = listOf(BundleApplyMcpSpec("boss", kind = "boss-native")),
                    literacyAcknowledged = false,
                    source = BundleApplySource.Ui,
                ),
            )
        assertFalse(outcome.ok)
        assertEquals(BundleApplyStatus.Rejected, outcome.status)
        assertEquals(listOf("literacyAcknowledged=false"), outcome.errors)
        assertEquals(listOf("p1"), outcome.pluginsRequested)
        assertEquals(emptyList(), outcome.pluginsEnabled)
    }

    @Test
    fun `provider applies when literacy acknowledged`() {
        val outcome =
            RecordingProvider().applyBundle(
                BundleApplyRequest(
                    bundleId = "demo.org",
                    mode = BundleApplyMode.Organisation,
                    plugins = listOf(BundleApplyPluginSpec("p1", optional = true)),
                    mcps = listOf(BundleApplyMcpSpec("shared-mcp", kind = "community")),
                    literacyAcknowledged = true,
                    source = BundleApplySource.Mcp,
                ),
            )
        assertTrue(outcome.ok)
        assertEquals(BundleApplyStatus.Applied, outcome.status)
        assertEquals(listOf("p1"), outcome.pluginsEnabled)
        assertEquals(listOf("shared-mcp"), outcome.mcpsAllowlisted)
    }
}
