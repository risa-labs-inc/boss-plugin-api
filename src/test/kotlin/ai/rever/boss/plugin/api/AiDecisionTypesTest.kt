package ai.rever.boss.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiDecisionTypesTest {

    @Test
    fun `request defaults are 30s and 1 MiB`() {
        val r = AiDecisionRequest(providerId = "p", body = "{}")

        assertEquals(30_000L, r.timeoutMs)
        assertEquals(1_048_576, r.maxResponseBytes)
    }

    @Test
    fun `timeout bounds are 1s to 120s`() {
        assertEquals(1_000L, AiDecisionRequest.MIN_TIMEOUT_MS)
        assertEquals(120_000L, AiDecisionRequest.MAX_TIMEOUT_MS)
        assertTrue(AiDecisionRequest("p", "{}").timeoutMs in AiDecisionRequest.MIN_TIMEOUT_MS..AiDecisionRequest.MAX_TIMEOUT_MS)
    }

    @Test
    fun `extras default to empty on every type and take part in equality`() {
        val request = AiDecisionRequest("p", "{}")
        val reply = AiDecisionReply("{}", "p", 5)
        val provider = AiDecisionProvider("p", "P", local = false, reachable = true, models = emptyList())

        assertEquals(emptyMap(), request.extras)
        assertEquals(emptyMap(), reply.extras)
        assertEquals(emptyMap(), provider.extras)
        assertNotEquals(request, request.copy(extras = mapOf("k" to "v")))
        assertNotEquals(reply, reply.copy(extras = mapOf("k" to "v")))
        assertNotEquals(provider, provider.copy(extras = mapOf("k" to "v")))
    }

    @Test
    fun `model display name defaults to id`() {
        assertEquals("jev-1", AiDecisionModel("jev-1").displayName)
        assertEquals("Jev", AiDecisionModel("jev-1", "Jev").displayName)
    }

    @Test
    fun `provider detail defaults to null and equality is structural`() {
        val a = AiDecisionProvider("ollaya", "Ollaya", local = true, reachable = false, models = emptyList())
        val b = a.copy()

        assertNull(a.detail)
        assertFalse(a.reachable)
        assertEquals(a, b)
        assertNotEquals(a, a.copy(reachable = true))
    }

    @Test
    fun `needsCredential defaults to false and takes part in equality`() {
        val a = AiDecisionProvider("openrouter", "OpenRouter", local = false, reachable = false, models = emptyList())

        assertFalse(a.needsCredential)
        assertTrue(a.copy(needsCredential = true).needsCredential)
        assertNotEquals(a, a.copy(needsCredential = true))
    }

    @Test
    fun `reply equality is structural`() {
        assertEquals(AiDecisionReply("{}", "p", 5), AiDecisionReply("{}", "p", 5))
        assertNotEquals(AiDecisionReply("{}", "p", 5), AiDecisionReply("{}", "q", 5))
    }

    @Test
    fun `exception carries its code and message`() {
        val e = AiDecisionException(AiDecisionException.LOCAL_UNAVAILABLE, "down")

        assertEquals("LOCAL_UNAVAILABLE", e.code)
        assertEquals("down", e.message)
    }

    @Test
    fun `failure codes are distinct`() {
        val codes = listOf(
            AiDecisionException.UNKNOWN_PROVIDER, AiDecisionException.MISSING_CREDENTIAL,
            AiDecisionException.AUTH_ERROR, AiDecisionException.INVALID_INPUT,
            AiDecisionException.MODEL_NOT_FOUND, AiDecisionException.RATE_LIMITED,
            AiDecisionException.UPSTREAM_ERROR, AiDecisionException.TIMEOUT,
            AiDecisionException.NETWORK_ERROR, AiDecisionException.RESPONSE_TOO_LARGE,
            AiDecisionException.LOCAL_UNAVAILABLE,
        )
        assertEquals(codes.size, codes.toSet().size)
    }
}
