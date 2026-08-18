package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * As with [AiGatewayTypesTest]: `apiCheck` guards the shape of these declarations, so
 * these cover the hand-written behaviour it cannot see.
 */
class AiCliSessionTypesTest {

    /** The smallest thing that satisfies the interface, for the default-body cases. */
    private class MinimalCliSessions(
        private val events: List<AiCliEvent> = emptyList(),
    ) : AiCliSessionAPI {
        var lastSpec: AiCliSessionSpec? = null

        override fun engines(): List<AiCliEngine> = listOf(AiCliEngine("claude", "Claude Code CLI"))

        override suspend fun health(engineId: String): AiCliHealth =
            if (engineId == "claude") AiCliHealth.Ready("2.1.0") else AiCliHealth.NotInstalled()

        override fun run(
            spec: AiCliSessionSpec,
            approve: (suspend (AiCliApprovalAsk) -> AiCliApprovalAnswer)?,
        ): Flow<AiCliEvent> {
            lastSpec = spec
            return if (events.isEmpty()) emptyFlow() else flowOf(*events.toTypedArray())
        }
    }

    // ==================== redaction ====================

    @Test
    fun `a spec never renders the credentials it carries`() {
        // The accidental path is what this guards: an interpolated log line, an exception
        // message, a crash report. envOverrides carries the API key and mcpConfigJson every
        // resolved connector secret, so the generated toString would put both in all three.
        val spec =
            AiCliSessionSpec(
                engineId = AiCliSessionAPI.ENGINE_CLAUDE,
                prompt = "summarise this",
                envOverrides = mapOf("ANTHROPIC_AUTH_TOKEN" to "sk-live-do-not-print-me"),
                mcpConfigJson = """{"mcpServers":{"drive":{"env":{"TOKEN":"ya29-secret"}}}}""",
            )

        val rendered = "$spec"

        assertFalse(rendered.contains("sk-live-do-not-print-me"), rendered)
        assertFalse(rendered.contains("ya29-secret"), rendered)
        assertContains(rendered, "redacted")
        // Still has to be useful for debugging, or it gets replaced by an interpolation
        // of the fields - which is the failure mode this exists to prevent.
        assertContains(rendered, AiCliSessionAPI.ENGINE_CLAUDE)
        assertContains(rendered, "1 redacted")
    }

    @Test
    fun `a spec with nothing secret says so rather than saying redacted`() {
        // "none" vs "<redacted>" is the difference between "this turn attached no
        // connectors" and "it attached some and we are not showing you" while reading a log.
        val rendered = "${AiCliSessionSpec(engineId = "codex", prompt = "hi")}"

        assertContains(rendered, "mcpConfig=none")
        assertContains(rendered, "env=none")
    }

    @Test
    fun `a spec does not render the prompt or the page context it carries`() {
        // Neither is a credential, but both are user content: a page the user was reading,
        // a selection they made. Sizes answer "was context attached" without putting the
        // content itself into a log line.
        val spec =
            AiCliSessionSpec(
                engineId = "claude",
                prompt = "what does this say about my salary",
                systemPrompt = "# Current page\nURL: https://payroll.example/me?token=abc",
            )

        val rendered = "$spec"

        assertFalse(rendered.contains("salary"), rendered)
        assertFalse(rendered.contains("payroll.example"), rendered)
        assertContains(rendered, "chars")
    }

    // ==================== default bodies ====================

    @Test
    fun `an implementation that does not offer selection degrades instead of failing`() {
        // selectedEngineId/selectEngine have default bodies so an implementation predating
        // them - or one that simply does not serve gateway requests - still satisfies the
        // interface. Answering null means "no engine selected", which routes callers back
        // to their HTTP provider rather than to a crash.
        val minimal = MinimalCliSessions()

        assertNull(minimal.selectedEngineId())
        minimal.selectEngine("claude")
        assertNull(minimal.selectedEngineId(), "the no-op default must not appear to have stored anything")
    }

    @Test
    fun `run defaults to no approval callback`() {
        // The overwhelmingly common call is a turn with no gated tools. It must not require
        // the caller to pass null explicitly.
        val minimal = MinimalCliSessions()

        runBlocking { minimal.run(AiCliSessionSpec(engineId = "claude", prompt = "hi")).toList() }

        assertEquals("hi", minimal.lastSpec?.prompt)
    }

    // ==================== open-set contracts ====================

    @Test
    fun `an unknown event is ignorable without an exhaustive match`() {
        // AiCliEvent is an ordinary class hierarchy, not a sealed one, precisely so adding
        // a case later does not break an already-compiled consumer. This is what such a
        // consumer looks like: it handles what it knows and falls through on the rest.
        val events =
            listOf(
                AiCliEvent.Started("s-1"),
                AiCliEvent.TextDelta("he"),
                AiCliEvent.ToolUse("t-1", "Bash", "{}"),
                AiCliEvent.TextDelta("llo"),
                AiCliEvent.Completed("s-1"),
            )

        val text = StringBuilder()
        var terminal = 0
        for (event in events) {
            when (event) {
                is AiCliEvent.TextDelta -> text.append(event.text)
                is AiCliEvent.Completed, is AiCliEvent.Failed -> terminal++
                else -> Unit
            }
        }

        assertEquals("hello", text.toString())
        assertEquals(1, terminal, "exactly one terminal event per turn")
    }

    @Test
    fun `denials keep null and empty apart`() {
        // Null means the engine did not report the field - an older CLI. Empty means it was
        // asked and refused nothing. A caller that explains refusals to the user has to keep
        // the two apart or it invents a refusal that never happened.
        assertNull(AiCliEvent.Completed("s").permissionDenials)
        assertEquals(emptyList(), AiCliEvent.Completed("s", permissionDenials = emptyList()).permissionDenials)
        // And a failed turn still reports what it refused, or exactly those turns regress.
        assertEquals(
            listOf(AiCliDeniedCall("Bash", "tu-1")),
            AiCliEvent.Failed("boom", listOf(AiCliDeniedCall("Bash", "tu-1"))).permissionDenials,
        )
    }

    @Test
    fun `health distinguishes not installed from broken`() {
        // Two different fixes: one is "install it", the other is "your install is broken".
        // Collapsing them sends half of users somewhere that cannot help.
        val healths =
            runBlocking {
                val api = MinimalCliSessions()
                listOf(api.health("claude"), api.health("codex"))
            }

        assertTrue(healths[0] is AiCliHealth.Ready)
        assertEquals("2.1.0", (healths[0] as AiCliHealth.Ready).version)
        assertTrue(healths[1] is AiCliHealth.NotInstalled)
    }

    @Test
    fun `a session id is engine-scoped, which the spec has somewhere to say`() {
        // Resuming across engines hard-fails the turn - a codex thread id is not a claude
        // session id. Nothing here can enforce that, so this just pins that both the engine
        // and the id travel together and a caller can compare them.
        val spec = AiCliSessionSpec(engineId = "codex", prompt = "go", sessionId = "thread-9")

        assertEquals("codex", spec.engineId)
        assertEquals("thread-9", spec.sessionId)
        assertNull(AiCliSessionSpec(engineId = "claude", prompt = "go").sessionId)
    }

    @Test
    fun `pricing keeps absent and zero apart`() {
        // Null means "the engine's own accounting is right"; zeroed means "this turn costs
        // nothing" - a subscription login. Collapsing them makes a subscription turn quote
        // the vendor's list price, which is a fabricated number in front of a cost cap.
        assertNull(AiCliSessionSpec(engineId = "claude", prompt = "x").pricing)
        assertEquals(
            AiCliPricing(0.0, 0.0),
            AiCliSessionSpec(engineId = "claude", prompt = "x", pricing = AiCliPricing(0.0, 0.0)).pricing,
        )
    }
}
