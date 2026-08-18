package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
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
        var lastTools: List<AiCliHostedTool> = emptyList()
        var lastApprove: (suspend (AiCliApprovalAsk) -> AiCliApprovalAnswer)? = null

        override fun engines(): List<AiCliEngine> = listOf(AiCliEngine(AiCliSessionAPI.ENGINE_CLAUDE, "Claude Code CLI"))

        override suspend fun health(engineId: String): AiCliHealth =
            if (engineId == AiCliSessionAPI.ENGINE_CLAUDE) AiCliHealth.Ready("2.1.0") else AiCliHealth.NotInstalled()

        override fun run(
            spec: AiCliSessionSpec,
            approve: (suspend (AiCliApprovalAsk) -> AiCliApprovalAnswer)?,
            tools: List<AiCliHostedTool>,
        ): Flow<AiCliEvent> {
            lastSpec = spec
            lastTools = tools
            lastApprove = approve
            return events.asFlow()
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
        // The variable NAME survives while its value does not: that is what answers "which
        // endpoint did this turn reach" without printing the credential.
        assertContains(rendered, "ANTHROPIC_AUTH_TOKEN")
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
    fun `an approval ask does not render the arguments it carries`() {
        // The type most likely to be logged: an approval prompt is the moment something
        // unusual happened and someone reaches for a diagnostic line. Tool arguments are
        // model-authored and routinely carry a credential, so the generated toString would
        // bypass every other redaction in the file.
        val ask =
            AiCliApprovalAsk(
                toolName = "mcp__drive__query",
                inputJson = """{"token":"ya29-live-secret","q":"salary review"}""",
                toolUseId = "tu-7",
            )

        val rendered = "$ask"

        assertFalse(rendered.contains("ya29-live-secret"), rendered)
        assertFalse(rendered.contains("salary review"), rendered)
        // Still enough to identify which call it was, which is the whole point of logging it.
        assertContains(rendered, "mcp__drive__query")
        assertContains(rendered, "tu-7")
        assertContains(rendered, "chars")
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

    @Test
    fun `a spec does not render the file paths it carries`() {
        // Not credentials, but a home directory and a project layout are still the user's.
        // The override reports a count and "<set>"; without this, a later "make this more
        // useful" edit could put either into a log without failing anything.
        val rendered =
            "${AiCliSessionSpec(
                engineId = "claude",
                prompt = "x",
                workingDir = "/Users/someone/Development/secret-project",
                attachments = listOf("/Users/someone/Documents/offer-letter.pdf"),
            )}"

        assertFalse(rendered.contains("secret-project"), rendered)
        assertFalse(rendered.contains("offer-letter"), rendered)
        assertContains(rendered, "workingDir=<set>")
        assertContains(rendered, "attachments=1")
    }

    @Test
    fun `a spec renders the non-secret fields that explain a strange turn`() {
        // env names but not values: a variable name is not a credential, and "which endpoint
        // did this turn actually reach" is the first question when a turn goes to the wrong
        // provider. extras keys for the same reason - every future field arrives through it.
        val rendered =
            "${AiCliSessionSpec(
                engineId = "claude",
                prompt = "x",
                envOverrides = mapOf("ANTHROPIC_BASE_URL" to "https://gw.internal", "ANTHROPIC_AUTH_TOKEN" to "sk-live"),
                extras = mapOf("topP" to "0.9"),
                idleTimeoutMs = 5_000,
            )}"

        assertContains(rendered, "ANTHROPIC_BASE_URL")
        assertFalse(rendered.contains("sk-live"), rendered)
        assertFalse(rendered.contains("gw.internal"), rendered)
        assertContains(rendered, "topP")
        assertContains(rendered, "idleTimeoutMs=5000")
    }

    // ==================== defaults callers inherit silently ====================

    @Test
    fun `the defaults a caller never writes are pinned`() {
        // A caller never spells these out, so changing one is a behaviour change for every
        // consumer with no compile error anywhere. Pinning them makes such a change
        // deliberate rather than incidental.
        val spec = AiCliSessionSpec(engineId = "claude", prompt = "x")

        // Ten minutes, not three: a working tool call emits nothing while it runs, so a
        // default that cannot fit a build kills real turns. Raising it is what this
        // assertion is for - it made the change deliberate rather than incidental.
        assertEquals(600_000L, spec.idleTimeoutMs)
        assertEquals("", spec.permissionMode, "blank means the engine's own default, whatever that is")
        assertTrue(spec.disallowedTools.isEmpty())
        assertTrue(spec.allowedTools.isEmpty())
        assertNull(spec.pricing)
        assertEquals("", AiCliApprovalAnswer(allow = false).message)
        // An engine that does not say otherwise cannot ask the user about a tool call.
        // Defaulting the other way would have a caller advertise approvals it never gets.
        assertFalse(AiCliEngine("x", "X").supportsApprovals)
        assertFalse(
            AiCliEngine("x", "X").supportsHostedTools,
            "defaulting this true would have a caller serve tools to an engine that ignores them",
        )
        assertEquals("", AiCliEngine("x", "X").installHint)
        // false is the SAFE direction here and the dangerous one to flip: a final report
        // must flag a cap rather than cancel, because the money is already spent.
        assertFalse(AiCliEvent.CostUpdate(1.0).isFinal)
        assertTrue(spec.attachments.isEmpty())
        assertTrue(spec.extras.isEmpty())
        // A blank unroutableAnswer is documented to fall back to a generic refusal, so the
        // blank itself is the contract rather than an oversight.
        assertEquals("", AiCliHostedTool("ask", "d") { "x" }.unroutableAnswer)
    }

    @Test
    fun `the selection defaults answer honestly, including for a deselect`() {
        // Three reviews caught this: the doc says deselecting always succeeds, so a flat
        // false would have a settings surface report "not supported" for the one operation
        // that cannot fail. A no-op implementation still refuses an engine id.
        val minimal = MinimalCliSessions()

        assertTrue(minimal.selectEngine(null), "deselecting cannot fail, so it must not report failure")
        assertFalse(minimal.selectEngine(AiCliSessionAPI.ENGINE_CLAUDE))
    }

    @Test
    fun `an unnamespacing implementation answers the bare tool name`() {
        // The default, and the one whose failure is silent: a caller pre-allowing the wrong
        // string gets an agent that says it lacks permission to use a tool nobody can find.
        val minimal = MinimalCliSessions()

        assertEquals("Bash", minimal.qualifiedToolName(AiCliSessionAPI.ENGINE_CLAUDE, "Bash"))
    }

    @Test
    fun `a spec with a blank mcp config says none rather than redacted`() {
        // Blank and null are the same fact here - nothing attached - and rendering
        // "<redacted>" for a spec that attached nothing is a false claim in a log. Also the
        // case a mutation of isNullOrBlank to == null would otherwise survive.
        val rendered = "${AiCliSessionSpec(engineId = "claude", prompt = "x", mcpConfigJson = "")}"

        assertContains(rendered, "mcpConfig=none")
    }

    @Test
    fun `pricing renders as a fact, not as its rates`() {
        // The rates are the user's commercial terms, and the flag is what a log needs.
        val rendered =
            "${AiCliSessionSpec(
                engineId = "claude",
                prompt = "x",
                pricing = AiCliPricing(12.34, 56.78),
            )}"

        assertContains(rendered, "priced=true")
        assertFalse(rendered.contains("12.34"), rendered)
        assertFalse(rendered.contains("56.78"), rendered)
    }

    @Test
    fun `usage is reported apart from cost`() {
        // An engine on a subscription login reports tokens and no price, which is exactly
        // when a consumer still wants to show a count. Cached input is part of the input
        // total rather than additional to it, so a re-pricing downstream is possible.
        val completed = AiCliEvent.Completed("s", usage = AiCliUsage(inputTokens = 100, outputTokens = 20, cachedInputTokens = 60))

        assertEquals(120, completed.usage?.totalTokens)
        assertEquals(60, completed.usage?.cachedInputTokens)
        assertNull(completed.costUsd, "tokens without a price is the subscription case")
        assertNull(AiCliEvent.Completed("s").usage, "null means the engine reported none")
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
        // And it says so rather than pretending: a settings toggle that springs back with no
        // explanation is worse than one that reports "not supported".
        assertFalse(minimal.selectEngine("claude"), "the no-op default must not claim success")
        assertNull(minimal.selectedEngineId(), "the no-op default must not appear to have stored anything")
    }

    @Test
    fun `run defaults to no hosted tools`() {
        // The common call serves none, so it must not require an empty list at every site.
        val minimal = MinimalCliSessions()

        runBlocking { minimal.run(AiCliSessionSpec(engineId = "claude", prompt = "hi")).toList() }

        assertTrue(minimal.lastTools.isEmpty())
    }

    @Test
    fun `a hosted tool carries what it answers when nobody is left to ask`() {
        // The caller supplies this because only the caller knows how its tool's answers
        // read. A refused approval is a denial; a refused question is not, and returning an
        // error there would have the agent apologise about tooling to a user who never saw
        // a question.
        val tool =
            AiCliHostedTool(
                name = "ask",
                description = "Ask the user to choose",
                unroutableAnswer = "No answer - the conversation moved on.",
            ) { "chose: b" }

        assertEquals("ask", tool.name)
        assertEquals("No answer - the conversation moved on.", tool.unroutableAnswer)
        assertEquals("{}", tool.inputSchema, "a tool with no arguments still advertises a schema")
        assertEquals("chose: b", runBlocking { tool.handle("{}") })
    }

    @Test
    fun `run defaults to no approval callback`() {
        // The overwhelmingly common call is a turn with no gated tools. It must not require
        // the caller to pass null explicitly.
        val minimal = MinimalCliSessions()

        runBlocking { minimal.run(AiCliSessionSpec(engineId = "claude", prompt = "hi")).toList() }

        // The thing this test is named for. Asserting on the prompt instead passed
        // identically whether the default was null or a non-null stub.
        assertNull(minimal.lastApprove)
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
    fun `what the implementation refused is reported apart from what the engine refused`() {
        // A caller subtracts its own refusals before advising the user to raise a permission
        // level. It can attribute the ones it decided - those came through approve - but not
        // the ones the implementation refused without asking, and those land in
        // permissionDenials all the same. Unsubtracted, they become advice to escalate over
        // something done on the caller's behalf.
        val bridgeRefused = listOf(AiCliDeniedCall("Bash", "tu-9"))
        val completed =
            AiCliEvent.Completed(
                "s",
                permissionDenials = listOf(AiCliDeniedCall("Bash", "tu-9"), AiCliDeniedCall("Write", "tu-10")),
                deniedWithoutAsking = bridgeRefused,
            )

        assertEquals(bridgeRefused, completed.deniedWithoutAsking)
        // Empty, not null: unlike permissionDenials there is no "did not say" case, because
        // an implementation always knows what it itself refused.
        assertEquals(emptyList(), AiCliEvent.Completed("s").deniedWithoutAsking)
        assertEquals(emptyList(), AiCliEvent.Failed("boom").deniedWithoutAsking)
        // A failed turn carries them too: it can refuse things on the way to failing.
        assertEquals(
            bridgeRefused,
            AiCliEvent.Failed("boom", deniedWithoutAsking = bridgeRefused).deniedWithoutAsking,
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
        // The third state is the one that is easy to forget and the most confusing to hit:
        // the binary is there and will not run. "Install it" would be wrong advice.
        assertEquals("broken install", AiCliHealth.Failed("broken install").message)
        assertEquals("brew install codex", AiCliHealth.NotInstalled("brew install codex").hint)
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
