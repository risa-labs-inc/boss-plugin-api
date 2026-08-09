package ai.rever.boss.plugin.api

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The api is mostly declarations, and `apiCheck` guards their shape. These cover the
 * hand-written behaviour it cannot see.
 */
class AiGatewayTypesTest {

    // ==================== AiImage ====================

    @Test
    fun `two images with the same bytes are equal`() {
        // The generated equals compares the array by identity, so without the override two
        // equal images are unequal - and an AiMessage holding one inherits that.
        val a = AiImage("abc".toByteArray(), "image/png")
        val b = AiImage("abc".toByteArray(), "image/png")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `images differing in bytes or media type are not equal`() {
        val png = AiImage("abc".toByteArray(), "image/png")

        assertNotEquals(png, AiImage("abd".toByteArray(), "image/png"))
        assertNotEquals(png, AiImage("abc".toByteArray(), "image/jpeg"))
    }

    @Test
    fun `an image is usable as a map key`() {
        val map = mapOf(AiImage("k".toByteArray(), "image/png") to "value")

        assertEquals("value", map[AiImage("k".toByteArray(), "image/png")])
    }

    // ==================== AiUsage ====================

    @Test
    fun `usage sums and totals`() {
        val a = AiUsage(inputTokens = 10, outputTokens = 4)
        val b = AiUsage(inputTokens = 1, outputTokens = 2)

        assertEquals(AiUsage(11, 6), a + b)
        assertEquals(14, a.totalTokens)
        // An agent loop sums per turn, so the identity has to hold or a budget drifts.
        assertEquals(a, a + AiUsage())
    }

    // ==================== AiMessage ====================

    @Test
    fun `the message factories use the declared roles`() {
        assertEquals(AiMessage.ROLE_USER, AiMessage.user("hi").role)
        assertEquals(AiMessage.ROLE_ASSISTANT, AiMessage.assistant("yo").role)
        assertEquals("hi", AiMessage.user("hi").text)
    }

    // ==================== BrokeredCredential ====================

    @Test
    fun `a brokered credential never renders its token`() {
        // The accidental path is what this guards: an interpolated log line, an exception
        // message, a crash report. A data class would put the live token in all three.
        val credential =
            BrokeredCredential(
                token = "sk-live-do-not-print-me",
                refreshAfterSeconds = 3600,
                expiresAt = "2026-08-09T00:00:00Z",
            )

        val rendered = "$credential"

        assertFalse(rendered.contains("sk-live-do-not-print-me"), rendered)
        assertContains(rendered, "***")
        // The non-secret fields still have to be there, or this is useless for debugging.
        assertContains(rendered, "3600")
        // A caller that explicitly asks still gets it.
        assertEquals("sk-live-do-not-print-me", credential.token)
    }

    @Test
    fun `a failed Result does not render the token either`() {
        // Result.toString() delegates to the value's, which is the shape that actually
        // reaches a log line while someone is debugging an exchange.
        val rendered = Result.success(BrokeredCredential("sk-secret", 60)).toString()

        assertFalse(rendered.contains("sk-secret"), rendered)
    }

    // ==================== open-set contracts ====================

    @Test
    fun `AiStopReason carries a fallback so a newer reason has somewhere to land`() {
        // The enum is matched on by callers; UNKNOWN is what makes an else branch
        // meaningful rather than a guess.
        assertTrue(AiStopReason.entries.contains(AiStopReason.UNKNOWN))
        assertTrue(AiStopReason.entries.contains(AiStopReason.PROVIDER_STOPPED))
    }

    // There is deliberately no test for the absence of ROLE_TOOL. Reflection over a
    // companion does not reliably see `const val`s (they inline), so such a test passes
    // whether or not the constant exists - which is worse than none. The committed
    // apiDump lists every public field, so re-adding it shows up there as a review signal.

    @Test
    fun `the step default refuses tools rather than reporting a final answer`() {
        // A gateway that predates step() must not map complete() and hand back an empty
        // toolCalls, which this api documents as a final answer.
        val older =
            object : AiGatewayAPI {
                override suspend fun complete(request: AiRequest) = Result.success(AiReply("considered"))

                override fun stream(request: AiRequest) = kotlinx.coroutines.flow.emptyFlow<AiChunk>()

                override suspend fun runAgent(
                    request: AiRequest,
                    tools: List<AiToolSpec>,
                    budget: AiBudget,
                    invoke: suspend (AiToolCall) -> AiToolOutcome,
                ) = Result.failure<AiAgentResult>(UnsupportedOperationException())
            }

        val withTools =
            kotlinx.coroutines.runBlocking {
                older.step(AiRequest(), tools = listOf(AiToolSpec("ls", "list")))
            }
        val withoutTools = kotlinx.coroutines.runBlocking { older.step(AiRequest()) }

        assertTrue(withTools.isFailure)
        assertEquals("considered", withoutTools.getOrNull()?.text)
    }
}
