package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow

/**
 * Drives a locally installed coding-agent CLI - Claude Code, Codex - headlessly,
 * authenticated by **that CLI's own terminal login** rather than by a key any plugin
 * holds.
 *
 * This is the companion to [AiGatewayAPI], not a replacement for it, and the split is
 * not stylistic. [AiGatewayAPI] is a stateless completion interface: [AiRequest] has no
 * working directory, no session to resume, no subagent and no permission mode, and
 * [AiChunk] has no element for a tool call. A CLI agent session has all five. Stretching
 * the completion types to carry them would make every consumer of the simple case pay
 * for the complicated one, so the complicated one gets its own interface.
 *
 * What it buys is the auth path: a user who has run `claude` or `codex login` in a
 * terminal already has working AI, and reaching it needs no API key, no organisation
 * spend and nothing stored. A plugin that only wants a completion should still use
 * [AiGatewayAPI] - the gateway routes that through here when the user has chosen a CLI
 * engine, so the simple case stays simple.
 *
 * Obtained like any plugin API and **resolved lazily, per use**, never cached at
 * `register()`:
 *
 * ```kotlin
 * val cli = context.getPluginAPI(AiCliSessionAPI::class.java) ?: return  // hide AI affordances
 * cli.run(AiCliSessionSpec(engineId = AiCliSessionAPI.ENGINE_CLAUDE, prompt = "..."))
 *     .collect { event -> ... }
 * ```
 *
 * Plugin load order is not guaranteed, so resolving once at registration caches a null
 * forever.
 */
interface AiCliSessionAPI {

    /**
     * The CLI engines this implementation knows how to drive, in display order.
     *
     * Cheap and synchronous: this is the static list, not a probe. An engine appearing
     * here says nothing about whether its binary is installed - ask [health] for that.
     * Treat the list as open; ids other than the [ENGINE_CLAUDE]/[ENGINE_CODEX]
     * constants are expected and should be rendered from [AiCliEngine.displayName]
     * rather than matched against.
     */
    fun engines(): List<AiCliEngine>

    /**
     * Whether [engineId]'s binary is present and runnable right now.
     *
     * Spawns the CLI's `--version`, so it is not free - call it when showing readiness,
     * not per keystroke. An unknown [engineId] answers [AiCliHealth.NotInstalled] rather
     * than throwing, because to a user the two are the same thing.
     */
    suspend fun health(engineId: String): AiCliHealth

    /**
     * Run one turn and stream what the agent does, as it does it.
     *
     * **The flow never throws**, except [kotlinx.coroutines.CancellationException]. Every
     * failure - a missing binary, a crashed process, a turn that stalls - arrives as
     * [AiCliEvent.Failed] and the flow then completes normally. Without that guarantee a
     * caller collecting into a panel would need both a `Failed` branch and a `catch {}`
     * to avoid taking the panel down.
     *
     * Exactly one terminal event is emitted, [AiCliEvent.Completed] or
     * [AiCliEvent.Failed]. Cancelling the collection kills the CLI process **and its
     * descendants** - MCP servers it spawned inherit the pipe and would otherwise linger
     * holding it open.
     *
     * [approve] answers a tool call the CLI cannot decide on its own. A headless CLI cannot
     * show a permission prompt, so without one every tool that is neither pre-allowed nor
     * pre-denied simply fails; with one, the implementation stands up a loopback bridge,
     * hands the CLI a permission-prompt tool, and calls back here. Passing null means
     * "decide from the allow and deny lists alone".
     *
     * **It is consulted whenever [AiCliEngine.supportsApprovals] is true for this engine,
     * and never otherwise.** In particular it does *not* require the caller to supply
     * [AiCliSessionSpec.mcpConfigJson]: the implementation adds its own bridge entry to
     * whatever config the caller passed, or synthesises one when the caller passed none.
     * An engine with its own sandbox and no per-call prompt ignores the callback entirely,
     * which is why that flag is a fact on the engine rather than something to infer.
     *
     * **It is scoped to this call.** A request arriving from a process that outlived its
     * turn is denied without ever reaching [approve], so a stale agent cannot raise a
     * question against whatever the user is looking at now.
     *
     * [approve] is invoked on a thread the implementation owns, **not** on the collecting
     * coroutine's context, because the CLI is holding a connection open waiting for the
     * answer. It may suspend for as long as it needs - it is expected to be a user prompt -
     * but it must be safe to call off the collector's thread, and it must not assume it can
     * touch UI state directly.
     *
     * It must not throw: an exception is treated as a denial, because a question the CLI
     * never gets an answer to stalls the whole turn.
     *
     * [kotlinx.coroutines.CancellationException] is handled separately, and not by
     * propagating - the CLI is holding a connection open, so *something* has to answer it or
     * the turn hangs until the idle timeout. Cancellation means the turn is being abandoned
     * (a panel closing, the user switching away), so the call is refused with a reason that
     * says exactly that. The distinction is not cosmetic: telling the model "the user denied
     * this" would have it apologise for a request nobody ever saw, and a caller that turns
     * denials into guidance would advise raising a permission level over it.
     */
    fun run(
        spec: AiCliSessionSpec,
        approve: (suspend (AiCliApprovalAsk) -> AiCliApprovalAnswer)? = null,
    ): Flow<AiCliEvent>

    /**
     * The engine the user chose to serve ordinary [AiGatewayAPI] requests, or null when
     * they chose an HTTP provider instead.
     *
     * Non-null means [AiGatewayAPI.complete] and [AiGatewayAPI.stream] go through this
     * CLI - and therefore through the user's own subscription - rather than through a
     * configured API key. That is deliberately an explicit choice and never a fallback:
     * a plugin quietly spending someone's Claude subscription because no key happened to
     * be configured is not a degradation, it is a surprise bill.
     *
     * **A non-null value here must be reflected as a non-null [AiGatewayAPI.activeModel].**
     * That is a requirement on whoever implements both, and the whole feature turns on it:
     * [AiAvailability.check] decides readiness purely from `activeModel()`, so a gateway
     * that reported null for a selected CLI engine would have every consumer hide its AI
     * affordance and send the user to Settings to paste a key - on a machine where
     * `complete` would have worked through their own subscription.
     */
    fun selectedEngineId(): String? = null

    /**
     * Choose the engine for ordinary [AiGatewayAPI] requests, or null to go back to the
     * configured HTTP provider. Returns whether the choice was applied.
     *
     * Intended for the settings surface that owns provider selection, which is also
     * responsible for clearing the *other* selection when this one is set. Two stores
     * holding "which provider is active" can disagree; one writer keeps them from it.
     *
     * The return value exists because the default body is a no-op, and a settings toggle
     * that silently springs back is worse than one that says "not supported". False means
     * the choice did not take - an implementation that does not serve gateway requests, or
     * an [engineId] it does not have - and the caller should show that rather than assume
     * it worked. Passing null is always applied, since deselecting cannot fail.
     */
    fun selectEngine(engineId: String?): Boolean = false

    companion object {
        /** [AiCliEngine.id] of the Claude Code CLI. */
        const val ENGINE_CLAUDE: String = "claude"

        /** [AiCliEngine.id] of the OpenAI Codex CLI. */
        const val ENGINE_CODEX: String = "codex"
    }
}

/**
 * One CLI this implementation can drive.
 *
 * Static description only. Whether the binary exists is [AiCliSessionAPI.health]'s
 * question, and the answer changes while the app is running - a user can install the CLI
 * without restarting BOSS.
 */
data class AiCliEngine(
    /** Stable id passed back as [AiCliSessionSpec.engineId]. Treat as an open set. */
    val id: String,
    /** Human-readable name, e.g. `Claude Code CLI`. */
    val displayName: String,
    /** One line on what it is and how it authenticates, for a settings row. */
    val description: String = "",
    /** What to tell a user who does not have it, e.g. the install command. */
    val installHint: String = "",
    /**
     * Whether this engine can ask the user about a tool call, i.e. whether passing
     * `approve` to [AiCliSessionAPI.run] does anything.
     *
     * A fact rather than something to infer, because inferring it wrongly is silent: a
     * caller that passes an approval callback to an engine which cannot use one gets a turn
     * where every tool outside `allowedTools` simply fails, the callback is never invoked,
     * and nothing anywhere says why. To the user that reads as the agent refusing at random.
     *
     * False for an engine with its own sandbox and no per-call prompt - Codex is the case
     * that exists today. Such an engine also ignores [AiCliSessionSpec.allowedTools],
     * `disallowedTools` and `mcpConfigJson`.
     */
    val supportsApprovals: Boolean = false,
    /**
     * Facts this type does not model yet, for the same reason [AiCliSessionSpec.extras]
     * exists: this is a data class, so a new constructor parameter later moves
     * `copy$default` and hands compiled callers a `NoSuchMethodError`.
     *
     * The one most likely to be wanted next is which models an engine accepts. Unknown keys
     * are ignored, never rejected.
     */
    val extras: Map<String, String> = emptyMap(),
)

/**
 * Whether an engine can run right now.
 *
 * An ordinary class hierarchy rather than a sealed one, for the reason [AiChunk]
 * documents: adding a case to a sealed type breaks every already-compiled `when`. Handle
 * the three below and treat anything else as not ready.
 */
abstract class AiCliHealth private constructor() {
    /** Found and runnable; [version] as the CLI reported it. */
    class Ready(val version: String) : AiCliHealth()

    /** No binary found. [hint] is [AiCliEngine.installHint], for a message with a fix in it. */
    class NotInstalled(val hint: String = "") : AiCliHealth()

    /** Found but it would not run - a broken install, a timeout, a non-zero exit. */
    class Failed(val message: String) : AiCliHealth()
}

/**
 * Everything one turn needs, and nothing about how to present it.
 *
 * The caller owns policy: which tools are allowed, what the agent is told it may do,
 * which MCP servers attach, what context rides along. This type carries those decisions
 * to the engine; it does not make any of them. In particular [systemPrompt] is composed
 * by the caller - the engine appends it verbatim and never adds context of its own.
 */
data class AiCliSessionSpec(
    /** Which engine, from [AiCliSessionAPI.engines]. */
    val engineId: String,
    /** The user's message. Delivered on stdin, so its size is not an argv limit. */
    val prompt: String,
    /**
     * Appended to the agent's system prompt verbatim, or blank for none.
     *
     * Compose it caller-side. Anything untrusted in here - a web selection, terminal
     * output - should already be wrapped in boundary markers by whoever captured it;
     * the engine cannot tell trusted framing from quoted text and will not try.
     */
    val systemPrompt: String = "",
    /** Subagent to run as (Claude Code `--agent`), or blank for the default. */
    val agentName: String = "",
    /**
     * Session to resume, or null to start fresh.
     *
     * Engine-specific and **not portable**: a Codex thread id handed to `claude
     * --resume` hard-fails the turn. A caller that lets the user switch engines must
     * drop the stored id when the engine changes.
     */
    val sessionId: String? = null,
    /**
     * Directory the turn runs in, so it inherits that project's agent config, memory and
     * MCP servers. Null runs wherever the CLI defaults to.
     *
     * A path that is not a directory is **ignored rather than failing the turn**, and the
     * consequence is worth stating plainly: the turn then runs in the CLI's default
     * directory, which with a writable [permissionMode] means the agent edits files
     * somewhere the caller did not choose. A caller that passes a path it has not checked
     * should check it, or pass null deliberately.
     */
    val workingDir: String? = null,
    /**
     * Permission mode, passed through to the engine, or blank for its default.
     *
     * Engine-specific by design and not normalised here: Claude Code takes
     * `acceptEdits` and friends, Codex takes a sandbox level (`read-only`,
     * `workspace-write`). Normalising would mean this type deciding what a capability
     * level means, which is the caller's policy.
     */
    val permissionMode: String = "",
    /** Model id for the engine's `--model`, or blank for the user's own CLI default. */
    val modelId: String = "",
    /**
     * Tools to pre-allow. A headless turn cannot answer a permission prompt, so anything
     * not pre-allowed either routes to `approve` or fails. Project deny rules still
     * apply on top.
     */
    val allowedTools: List<String> = emptyList(),
    /** Tools to refuse outright. Wins over [allowedTools] and over `approve`. */
    val disallowedTools: List<String> = emptyList(),
    /**
     * Files the user attached.
     *
     * **Their parent directories are granted read access**, which is a real widening and
     * not obvious from the word "attachments": attaching one PDF exposes the whole folder
     * it sits in for the turn. It is what makes an attachment readable at all where the
     * engine grants by directory, but a caller staging a file from a large shared folder is
     * granting more than the file.
     */
    val attachments: List<String> = emptyList(),
    /**
     * Extra MCP servers for this turn, as the CLI's `--mcp-config` JSON, or null.
     *
     * **Secret-bearing.** Resolved connector credentials live in here, so the
     * implementation stages it as an owner-only temp file rather than passing it in
     * argv - `/proc/<pid>/cmdline` is world-readable on Linux - and deletes it when the
     * turn ends. Do not log it; see this class's [toString].
     */
    val mcpConfigJson: String? = null,
    /**
     * Environment overrides for the child process, e.g. pointing the CLI at a
     * compatible endpoint.
     *
     * **Secret-bearing**, same as [mcpConfigJson]. Setting any auth variable here makes
     * the caller responsible for the whole auth surface: the implementation first scrubs
     * every inherited variable the engine understands, so an inherited real key cannot
     * be sent to a third-party base URL, and then applies exactly these.
     */
    val envOverrides: Map<String, String> = emptyMap(),
    /**
     * Prices for costing this turn's token usage, or null to report only what the engine
     * reports itself.
     *
     * Null and zeroed are **different** and both meaningful: null means "the engine's own
     * accounting is right", zeroed means "this turn costs nothing" - a subscription login,
     * or a free endpoint. Collapsing them makes a subscription turn quote the vendor's
     * list price, which is a fabricated number in front of a cost cap.
     */
    val pricing: AiCliPricing? = null,
    /**
     * Kill the turn if it emits nothing for this long. A stalled tool or MCP call would
     * otherwise hang the caller's "sending" state forever.
     *
     * Zero or negative **disables** the idle watchdog, for a caller that owns its own
     * bound. Note this is an *idle* timeout, not a wall clock: a turn that emits a token
     * every few seconds forever never trips it. There is deliberately no total-duration
     * field, because the caller already has a better one - cancelling the collection ends
     * the turn and kills the process - and two competing deadlines is how the shorter one
     * ends up killing turns the caller thought it had allowed.
     */
    val idleTimeoutMs: Long = 180_000,
    /**
     * Engine-agnostic hints for anything this type does not model yet.
     *
     * The escape hatch, and it exists because of a hard constraint rather than as a
     * convenience: this is a data class, so **adding a constructor parameter later is a
     * breaking change** - the synthetic constructor descriptor and `copy$default` both
     * move, and a plugin compiled against an earlier api gets `NoSuchMethodError` on a
     * call it never touched.
     *
     * Unknown keys are **ignored**, never rejected, so a hint added later degrades on an
     * older implementation instead of failing. Do not put credentials here; unlike
     * [envOverrides] this is not treated as secret-bearing.
     */
    val extras: Map<String, String> = emptyMap(),
) {
    /**
     * Redacted deliberately.
     *
     * [envOverrides] carries API keys and [mcpConfigJson] every resolved connector
     * secret in cleartext. Nothing prints a spec today, but the generated `toString`
     * would put the user's secrets into the first diagnostic line or exception message
     * anyone adds later - this makes that impossible rather than merely currently-absent.
     */
    override fun toString(): String =
        "AiCliSessionSpec(engine=$engineId, agent=$agentName, model=$modelId, " +
            "permissionMode=$permissionMode, resuming=${sessionId != null}, " +
            "workingDir=${if (workingDir == null) "none" else "<set>"}, " +
            "prompt=${prompt.length} chars, system=${systemPrompt.length} chars, " +
            "allowed=${allowedTools.size}, disallowed=${disallowedTools.size}, " +
            "attachments=${attachments.size}, " +
            "mcpConfig=${if (mcpConfigJson.isNullOrBlank()) "none" else "<redacted>"}, " +
            // Names, values redacted. A variable name is not a credential, and "which
            // endpoint did this turn actually reach" is the first question when a turn goes
            // to the wrong provider - answerable from ANTHROPIC_BASE_URL being present.
            "env=${if (envOverrides.isEmpty()) "none" else envOverrides.keys.sorted()}, " +
            "priced=${pricing != null}, idleTimeoutMs=$idleTimeoutMs, " +
            // Keys only, though extras is documented as not secret-bearing: the debugging
            // question is "was the hint set", and every future field arrives through here,
            // so omitting it would make each one invisible in diagnostics from day one.
            "extras=${extras.keys.sorted()})"
}

/**
 * Per-million-token prices used to cost a turn.
 *
 * An implementation is expected to clamp a negative rate to zero rather than subtracting
 * from a running total. That is an obligation on the implementation, not a promise this
 * type can keep: it holds two `Double`s and has no behaviour of its own.
 */
data class AiCliPricing(
    val inputPer1M: Double,
    val outputPer1M: Double,
)

/**
 * A tool call the agent wants to make that nothing has pre-decided.
 *
 * Handed to caller-supplied code at exactly the moment someone reaches for a diagnostic
 * line, which is why [toString] is overridden - see the note there.
 */
data class AiCliApprovalAsk(
    /** Tool the agent is asking to run, e.g. `Bash` or `mcp__boss__browser_navigate`. */
    val toolName: String,
    /**
     * Arguments as a JSON object string, exactly as the agent produced them.
     *
     * **Treat as sensitive.** These are whatever the model chose to send: an
     * `mcp__drive__query` payload can carry a resolved connector token, a `Bash` command an
     * inline credential or a `Authorization: Bearer` header. Never render it into a log; put
     * it in front of the user, which is what it is for.
     */
    val inputJson: String,
    /**
     * The agent's own id for this call, when it sends one.
     *
     * What makes a later subtraction exact: two `Bash` calls in one turn are two events,
     * and matching on the name alone lets one verdict blank the other.
     */
    val toolUseId: String? = null,
) {
    /**
     * Redacted, for the same reason [AiCliSessionSpec.toString] is, and with a stronger
     * case.
     *
     * An approval prompt is the single most likely place for an implementation to write a
     * diagnostic line - it is the moment something unusual happened and a person is about to
     * be asked about it - and [inputJson] is model-authored arguments that routinely carry a
     * credential. One `logger.debug("asking about $ask")` would bypass every redaction in
     * this file.
     *
     * The asymmetry was only ever in the data classes: [AiCliEvent.ToolUse] and
     * [AiCliEvent.ToolResult] carry the same kind of content but are plain classes, so they
     * render as identity hashes and were safe by accident rather than by decision.
     */
    override fun toString(): String =
        "AiCliApprovalAsk(tool=$toolName, toolUseId=$toolUseId, input=${inputJson.length} chars)"
}

/** The answer to an [AiCliApprovalAsk]. */
data class AiCliApprovalAnswer(
    val allow: Boolean,
    /** Shown to the agent when denying, so it can try something else. */
    val message: String = "",
)

/** One tool call the engine refused, as it reported it. */
data class AiCliDeniedCall(
    val toolName: String,
    val toolUseId: String? = null,
)

/**
 * What the agent did, streamed as it does it.
 *
 * Not a sealed interface, for the reason [AiChunk] documents: a new case would break every
 * already-compiled `when`. Handle the cases below and ignore anything else.
 *
 * **From 1.0.78 on, additions arrive as new sibling classes, never as new constructor
 * parameters.** [AiCliSessionSpec] has an `extras` hatch and these deliberately do not,
 * which could be read as "the events are already safe". They are not: adding a defaulted
 * parameter to [Completed] moves its constructor descriptor exactly as `copy$default` moves
 * for a data class. The blast radius is smaller - consumers read through getters, so only
 * code that *constructs* an event breaks - but "smaller" is not "none", and the constructing
 * code is every test double anyone has written against this.
 *
 * The parameter lists below were still being settled while 1.0.78 was unreleased, which is
 * the only window in which changing them was free. That window closes when it ships.
 */
abstract class AiCliEvent private constructor() {

    /**
     * The session exists. [sessionId] is what a later turn passes as
     * [AiCliSessionSpec.sessionId] to continue this conversation.
     */
    class Started(val sessionId: String) : AiCliEvent()

    /** A fragment of the answer. Fragments concatenate; they are not lines or words. */
    class TextDelta(val text: String) : AiCliEvent()

    /** A fragment of the agent's reasoning, when the engine streams it separately. */
    class ThinkingDelta(val text: String) : AiCliEvent()

    /** The agent started a tool call. [id] pairs it with its [ToolResult]. */
    class ToolUse(val id: String, val name: String, val input: String) : AiCliEvent()

    /** A tool call returned. */
    class ToolResult(val id: String, val content: String, val isError: Boolean) : AiCliEvent()

    /**
     * Running cost for the turn so far, when [AiCliSessionSpec.pricing] was supplied.
     *
     * @param isFinal true when this prices an already-finished, already-billed turn - some
     *   engines cost once, at the end. Crossing a cap on a final report must flag rather
     *   than cancel: there is nothing left to stop, and re-running would spend it again.
     */
    class CostUpdate(val estUsd: Double, val isFinal: Boolean = false) : AiCliEvent()

    /**
     * The turn finished. Terminal.
     *
     * @param permissionDenials what the engine refused this turn. **Null means the engine
     *   did not say** - an older CLI that does not report the field - which is a different
     *   fact from an empty list, meaning it was asked and refused nothing. A caller that
     *   explains refusals to the user has to keep the two apart or it will invent a
     *   refusal that never happened.
     * @param deniedWithoutAsking tool calls **this implementation** refused without ever
     *   consulting `approve`: a request from a process that outlived its turn, one carrying
     *   no turn identity at all, a callback that threw, a turn abandoned while a prompt was
     *   on screen. They still reach the engine as refusals and so still appear in
     *   [permissionDenials].
     *
     *   This exists because a caller that explains refusals to the user has to subtract its
     *   own. It can do that for the ones it decided - those came through `approve` - but not
     *   for these, and without them it sees a refusal it cannot attribute, concludes the
     *   engine refused on its own, and tells the user to raise a permission level over
     *   something done on their behalf. Wrong advice is worse than none, so subtract these
     *   as well as your own.
     *
     *   Empty rather than null: the implementation always knows what it refused, so unlike
     *   [permissionDenials] there is no "did not say" case to keep apart.
     */
    class Completed(
        val sessionId: String,
        val costUsd: Double? = null,
        val permissionDenials: List<AiCliDeniedCall>? = null,
        val deniedWithoutAsking: List<AiCliDeniedCall> = emptyList(),
    ) : AiCliEvent()

    /**
     * The turn failed. Terminal.
     *
     * Carries no session id on purpose - a turn can fail before one exists. A caller that
     * wants to resume after a failure retains [Started.sessionId], which arrives first
     * whenever there is one to have.
     *
     * @param permissionDenials as [Completed.permissionDenials]. Carried here too because
     *   a turn that ended in an error still reports what it refused, and dropping it
     *   silently regresses exactly those turns.
     * @param deniedWithoutAsking as [Completed.deniedWithoutAsking].
     */
    class Failed(
        val message: String,
        val permissionDenials: List<AiCliDeniedCall>? = null,
        val deniedWithoutAsking: List<AiCliDeniedCall> = emptyList(),
    ) : AiCliEvent()
}
