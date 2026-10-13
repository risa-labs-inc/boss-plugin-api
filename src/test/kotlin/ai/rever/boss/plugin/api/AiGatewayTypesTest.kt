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

    // ==================== AiRequest.modelOverride ====================

    @Test
    fun `modelOverride is null until the extras key is set`() {
        val bare = AiRequest()

        assertEquals(null, bare.modelOverride)

        val overridden =
            bare.copy(extras = bare.extras + (AiRequest.EXTRAS_KEY_MODEL_OVERRIDE to "claude-opus-5"))

        assertEquals("claude-opus-5", overridden.modelOverride)
        // The documented set-path must not disturb the original (copy semantics).
        assertEquals(null, bare.modelOverride)
    }

    @Test
    fun `provider and model overrides coexist in the same request`() {
        val request = AiRequest(
            extras = mapOf(
                AiRequest.EXTRAS_KEY_PROVIDER_ID to "X",
                AiRequest.EXTRAS_KEY_MODEL_OVERRIDE to "m",
            ),
        )

        assertEquals("X", request.extras[AiRequest.EXTRAS_KEY_PROVIDER_ID])
        assertEquals("m", request.modelOverride)
    }

    @Test
    fun `the extras key literal is pinned - old jars inlined it`() {
        // EXTRAS_KEY_MODEL_OVERRIDE is a const val: already-compiled plugins carry the
        // LITERAL, so changing the string silently orphans every request they build.
        assertEquals("modelOverride", AiRequest.EXTRAS_KEY_MODEL_OVERRIDE)
        assertEquals("providerId", AiRequest.EXTRAS_KEY_PROVIDER_ID)
        assertEquals("providerOverride", AiGatewayAPI.CAPABILITY_PROVIDER_OVERRIDE)
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

    // ==================== catalog and capability defaults ====================

    @Test
    fun `an older AiGatewayAPI implementor reports no models or capabilities`() {
        val older =
            object : AiGatewayAPI {
                override suspend fun complete(request: AiRequest) = Result.success(AiReply(""))

                override fun stream(request: AiRequest) = kotlinx.coroutines.flow.emptyFlow<AiChunk>()

                override suspend fun runAgent(
                    request: AiRequest,
                    tools: List<AiToolSpec>,
                    budget: AiBudget,
                    invoke: suspend (AiToolCall) -> AiToolOutcome,
                ) = Result.failure<AiAgentResult>(UnsupportedOperationException())
            }

        assertEquals(emptyList(), older.availableModels())
        assertEquals(emptySet(), older.capabilities())
    }

    @Test
    fun `an older LlmProvider implementor reports no models rather than throwing`() {
        val older = object : LlmProvider { override fun activeConfig(): LlmConfig? = null }

        assertEquals(emptyList(), older.availableModels())
    }

    // ==================== 1.0.99 additions ====================

    @Test
    fun `usage plus sums breakdowns only when both sides reported them`() {
        assertEquals(AiUsage(11, 7, cacheReadTokens = 9), AiUsage(10, 5, cacheReadTokens = 7) + AiUsage(1, 2, cacheReadTokens = 2))
        assertEquals(null, (AiUsage(10, 5, cacheReadTokens = 7) + AiUsage(1, 2)).cacheReadTokens, "a partial sum looks complete")
        assertEquals(null, (AiUsage(1, 1) + AiUsage(1, 1)).reasoningTokens)
        assertEquals(AiUsage(2, 2), AiUsage(1, 1) + AiUsage(1, 1))
    }

    @Test
    fun `new fields default to unreported and cache hints are extras keys`() {
        val turn = AiTurn(text = "hi")
        assertEquals(AiFinishReason.UNKNOWN, turn.finishReason)
        assertEquals(null, turn.reasoningCarry)
        assertEquals(AiUsage(1, 2, null, null, null), AiUsage(1, 2))
        // Wire names: gateways in other repos match on these strings.
        assertEquals("cacheSystem", AiGatewayAPI.EXTRA_CACHE_SYSTEM)
        assertEquals("cacheThroughMessage", AiGatewayAPI.EXTRA_CACHE_THROUGH_MESSAGE)
        assertEquals("cacheThroughRound", AiGatewayAPI.EXTRA_CACHE_THROUGH_ROUND)
        assertEquals("completeOnLength", AiGatewayAPI.EXTRA_COMPLETE_ON_LENGTH)
    }

    @Test
    fun `turn toString prints the reasoning carry's length, never its content`() {
        val text = AiTurn(text = "ok", reasoningCarry = "the patient said X").toString()
        assertTrue("<18 chars>" in text, text)
        assertFalse("patient" in text, text)
        assertTrue("reasoningCarry=null" in AiTurn(text = "ok").toString())
    }

    @Test
    fun `api 1_0_98 constructor and copy descriptors link and carry the new fields`() {
        // What a 1.0.98-compiled plugin calls. Reflection stands in for that bytecode.
        val int = Int::class.javaPrimitiveType!!
        val marker = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")

        assertEquals(AiUsage(3, 4), AiUsage::class.java.getConstructor(int, int).newInstance(3, 4))
        // AiUsage(inputTokens = 3): mask bit 1 takes outputTokens' default.
        assertEquals(AiUsage(3, 0), AiUsage::class.java.getConstructor(int, int, int, marker).newInstance(3, 9, 0b10, null))

        val usage = AiUsage(1, 1, cacheReadTokens = 9, cacheWriteTokens = 2, reasoningTokens = 1)
        assertEquals(AiUsage(2, 2, 9, 2, 1), AiUsage::class.java.getMethod("copy", int, int).invoke(usage, 2, 2))
        val usageCopyDefault = AiUsage::class.java.getMethod("copy\$default", AiUsage::class.java, int, int, int, Any::class.java)
        assertEquals(AiUsage(5, 1, 9, 2, 1), usageCopyDefault.invoke(null, usage, 5, 0, 0b10, null), "copy(inputTokens = 5)")

        val oldTurn = AiTurn::class.java.getConstructor(String::class.java, List::class.java, AiUsage::class.java, String::class.java)
            .newInstance("t", emptyList<AiToolCall>(), null, "m")
        assertEquals(AiTurn(text = "t", modelId = "m"), oldTurn)
        val turnDefault = AiTurn::class.java.getConstructor(
            String::class.java, List::class.java, AiUsage::class.java, String::class.java, int, marker,
        ).newInstance("t", null, null, null, 0b1110, null)
        assertEquals(AiTurn(text = "t"), turnDefault)

        val turn = AiTurn(text = "t", finishReason = AiFinishReason.LENGTH, reasoningCarry = "sig")
        val turnCopy = AiTurn::class.java
            .getMethod("copy", String::class.java, List::class.java, AiUsage::class.java, String::class.java)
            .invoke(turn, "u", emptyList<AiToolCall>(), null, "")
        assertEquals(turn.copy(text = "u"), turnCopy)
        val turnCopyDefault = AiTurn::class.java.getMethod(
            "copy\$default", AiTurn::class.java, String::class.java, List::class.java, AiUsage::class.java, String::class.java,
            int, Any::class.java,
        )
        assertEquals(turn.copy(text = "u"), turnCopyDefault.invoke(null, turn, "u", null, null, null, 0b1110, null), "copy(text = \"u\")")
    }
}
