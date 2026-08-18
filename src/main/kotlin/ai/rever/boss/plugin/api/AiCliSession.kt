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
     * Cheap, synchronous and non-throwing: this is the static list, not a probe. An engine
     * appearing here says nothing about whether its binary is installed - ask [health] for
     * that.
     * Treat the list as open; ids other than the [ENGINE_CLAUDE]/[ENGINE_CODEX]
     * constants are expected and should be rendered from [AiCliEngine.displayName]
     * rather than matched against.
     */
    fun engines(): List<AiCliEngine>

    /**
     * Whether [engineId]'s binary is present and runnable right now.
     *
     * Spawns the CLI's `--version`, so it is not free - call it when showing readiness, not
     * per keystroke. Who caches is the implementation's business, so a caller rendering a
     * settings row should not assume it is memoised.
     *
     * **Never throws**, matching [run]'s promise: an unknown [engineId], a probe that times
     * out, a binary that blows up - all answer [AiCliHealth]. To a user an engine this build
     * does not have and one they never installed are the same thing, and a readiness check
     * that needs its own `runCatching` is an api failing to do its job.
     */
    suspend fun health(engineId: String): AiCliHealth

    /**
     * Run one turn and stream what the agent does, as it does it.
     *
     * **Neither `run` nor the flow it returns ever throws**, except
     * [kotlinx.coroutines.CancellationException] on collection. Every failure - a missing
     * binary, an unknown [AiCliSessionSpec.engineId], a working directory that does not
     * exist, a crashed process, a turn that stalls - arrives as [AiCliEvent.Failed] and the
     * flow then completes normally. `run` itself is included deliberately: it is not
     * suspending, so an implementation that validated the spec eagerly would throw at the
     * *call site*, outside the flow, where the `Failed` branch a caller wrote cannot catch
     * it - defeating the guarantee for the failure most likely to be checked early. Without that guarantee a
     * caller collecting into a panel would need both a `Failed` branch and a `catch {}`
     * to avoid taking the panel down.
     *
     * Exactly one terminal event is emitted, [AiCliEvent.Completed] or
     * [AiCliEvent.Failed]. Cancelling the collection kills the CLI process **and its
     * descendants** - MCP servers it spawned inherit the pipe and would otherwise linger
     * holding it open.
     *
     * **Cold, and collect it once.** Nothing is spawned until collection starts, and each
     * collection starts a *new* turn with a new process - so a flow hoisted into a variable
     * and collected from two places, or later wrapped in a `shareIn` for a second view, runs
     * the turn twice and spends twice. Events are emitted on the implementation's own IO
     * context, not the collector's.
     *
     * [approve] answers a tool call the CLI cannot decide on its own. A headless CLI cannot
     * show a permission prompt, so without one every tool that is neither pre-allowed nor
     * pre-denied simply fails; with one, the implementation stands up a loopback bridge,
     * hands the CLI a permission-prompt tool, and calls back here. Passing null means
     * "decide from the allow and deny lists alone".
     *
     * **It is consulted when supplied and [AiCliEngine.supportsApprovals] is true for this
     * engine, and never otherwise.** In particular it does *not* require the caller to supply
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
     * [tools] are the caller's own tools, served to the agent on the same bridge and routed
     * the same way. The implementation owns the transport - a loopback MCP server, one token
     * per run, denial of anything it cannot place - and the caller owns what the tools are,
     * what they do and what they say. [approve] is not one of them only because its name has
     * to go into the engine's permission-prompt flag; everything else about it is the same
     * mechanism.
     *
     * That split is what lets a caller add a tool without the implementation changing. It
     * also means a hosted tool reaches the agent **only** where the engine takes a caller's
     * MCP config, which [AiCliEngine.supportsHostedTools] states rather than leaves to be
     * inferred.
     *
     * **[approve]** must not throw: an exception is treated as a denial, because a question
     * the CLI never gets an answer to stalls the whole turn. (A hosted tool's `handle` has a
     * different policy - see [AiCliHostedTool.unroutableAnswer] - which is why this names the
     * callback rather than saying "it".)
     *
     * [kotlinx.coroutines.CancellationException] is handled separately, and not by
     * propagating - the CLI is holding a connection open, so *something* has to answer it or
     * the turn hangs until the idle timeout. Cancellation means the turn is being abandoned
     * (a panel closing, the user switching away), so the call is refused with a reason that
     * says exactly that. The refusal is what the CLI receives; the turn still ends, because
     * the collection was cancelled and the process killed. The distinction is not cosmetic: telling the model "the user denied
     * this" would have it apologise for a request nobody ever saw, and a caller that turns
     * denials into guidance would advise raising a permission level over it.
     */
    /*
     * This parameter list is FROZEN at 1.0.78, and it is the least evolvable declaration in
     * the file: an abstract method with defaulted parameters, so a fourth input would move
     * the abstract descriptor (breaking every implementation) as well as `run$default`
     * (breaking every caller). `tools` arriving late in review is evidence the pressure is
     * real, so the escape route is worth naming rather than discovering.
     *
     * Data-shaped additions go in [AiCliSessionSpec.extras]. Anything that cannot - another
     * callback, say - arrives as a NEW overload carrying a default body that delegates here,
     * which leaves both descriptors untouched. Never by adding a parameter to this one.
     */
    fun run(
        spec: AiCliSessionSpec,
        approve: (suspend (AiCliApprovalAsk) -> AiCliApprovalAnswer)? = null,
        tools: List<AiCliHostedTool> = emptyList(),
    ): Flow<AiCliEvent>

    /**
     * The name the agent will see for one of the caller's [AiCliHostedTool]s.
     *
     * The implementation namespaces hosted tools under its own MCP server, so the caller
     * cannot construct this itself without hardcoding a name that is not its to choose. It
     * needs the answer because a tool the agent may call usually has to be pre-allowed by
     * that exact string - and getting it wrong does not fail loudly: the agent is told it
     * lacks permission to use the tool, and says so to a user who never saw why.
     *
     * Per engine, because MCP's `mcp__server__tool` shape is Claude Code's convention and
     * not a cross-CLI standard: an engine that serves hosted tools some other way would need
     * a different answer, and adding the parameter afterwards is the binary-breaking change
     * this file warns about everywhere else.
     *
     * The default returns the bare name, for an implementation that does not namespace. An
     * implementation that DOES serve hosted tools **must** override it, or it hands callers
     * the one answer guaranteed to produce the silent failure above.
     */
    fun qualifiedToolName(
        engineId: String,
        tool: String,
    ): String = tool

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
     * it worked.
     *
     * Passing null is always applied, since deselecting cannot fail - which is why the
     * default body answers `engineId == null` rather than a flat false. A no-op
     * implementation still has nothing to deselect, so reporting a failure there would have
     * a settings surface show "not supported" for the one operation that cannot fail.
     */
    fun selectEngine(engineId: String?): Boolean = engineId == null

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
     * that exists today. Such an engine also ignores [AiCliSessionSpec.allowedTools] and
     * `disallowedTools`, which is the security-relevant half: a caller writing a deny list
     * reasonably believes it is enforced, and those fields say so too.
     *
     * `mcpConfigJson` belongs to [supportsHostedTools] rather than here, because the two can
     * vary: an engine could serve a caller's MCP config without offering a permission-prompt
     * tool, which is the case that flag was added for.
     */
    val supportsApprovals: Boolean = false,
    /**
     * Whether this engine accepts a caller's own MCP tools, i.e. whether
     * [AiCliSessionAPI.run]'s `tools` reach the agent.
     *
     * Separate from [supportsApprovals] because they are separate capabilities, even though
     * today's two engines happen to have both or neither: approvals additionally need the
     * engine to take a permission-prompt tool by name, and an engine could plausibly serve
     * MCP without that. Collapsing them would make the first such engine a silent
     * mis-advertisement rather than a new flag.
     */
    val supportsHostedTools: Boolean = false,
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
    /**
     * Found and runnable; [version] as the CLI reported it.
     *
     * **Runnable is not authenticated.** A probe runs the CLI's `--version`, which succeeds
     * for an install that has never been logged in - so a user who ran the installer and
     * stopped there gets `Ready`, a settings row saying the engine is good to go, and a turn
     * that then dies in [AiCliEvent.Failed]. Keep a "try it and see" path rather than
     * treating this as a promise the next turn will work, and expect a future case here for
     * "installed, not signed in" once it can be detected: this hierarchy is open precisely
     * so one can be added without breaking a compiled `when`.
     */
    class Ready(val version: String) : AiCliHealth()

    /**
     * No binary found. [hint] is [AiCliEngine.installHint], for a message with a fix in it.
     *
     * The defaulted parameter is the hazard [AiCliEvent] documents: a later addition moves the
     * constructor descriptor. Reading it through the getter is safe, which is what a caller
     * does - but do not take "construction is implementation-side" as making the addition
     * free, because it is not. The implementation is a *different jar*, compiled against a
     * pinned api version, while `ApiClassLoader` serves the newest installed one and swaps it
     * without a restart. So implementation-side IS the cross-version boundary: a gateway built
     * against 1.0.78 calling `NotInstalled(hint)` breaks against a 1.0.79 that added a
     * parameter, with no rebuild in between. Additions here follow the same rule as
     * [AiCliEvent] - a new sibling class, not a new parameter.
     */
    class NotInstalled(val hint: String = "") : AiCliHealth()

    /**
     * Found but it would not run - a broken install, a timeout, a non-zero exit.
     *
     * [message] carries the same obligation as [AiCliEvent.Failed.message]: it is built from
     * a failed process invocation, so it must not reproduce the environment that invocation
     * was given.
     */
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
     * A non-null path that is **not a directory fails the turn** with [AiCliEvent.Failed],
     * rather than being ignored. Ignoring it was the first shape and it is the wrong default:
     * the turn would run in the CLI's own directory, which with a writable [permissionMode]
     * means the agent edits files somewhere nobody chose - and that is not recoverable, while
     * "that directory does not exist" is a message a user can act on. Pass null deliberately
     * to mean "wherever the CLI defaults to".
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
     *
     * **Ignored entirely by an engine whose [AiCliEngine.supportsApprovals] is false.**
     */
    val allowedTools: List<String> = emptyList(),
    /**
     * Tools to refuse outright. Wins over [allowedTools] and over `approve`.
     *
     * **Ignored entirely by an engine whose [AiCliEngine.supportsApprovals] is false**, which
     * is the direction of this mistake that matters: a caller writing a deny list for such an
     * engine believes it is enforced and it is not. Check the flag before relying on it.
     */
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
     * **Ignored entirely by an engine whose [AiCliEngine.supportsHostedTools] is false**, so
     * a caller attaching a tool server to such an engine attaches it to nobody.
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
     * **Ten minutes, not three, because a working tool call looks exactly like a stalled
     * one.** Nothing is emitted between a tool call and its result, so a `Bash` step running
     * a test suite or a cold build is indistinguishable from a hang - and a caller never
     * spells this value out, so a default that cannot fit a build kills real turns with a
     * timeout message and no way to tell which it was.
     *
     * Zero or negative **disables** the watchdog, which makes bounding the turn the caller's
     * obligation rather than an aside: this is the one setting that can leave a panel waiting
     * forever, and the only other bound is cancelling the collection. Note
     * this is an *idle* timeout, not a wall clock: a turn that emits a token every few
     * seconds forever never trips it. There is deliberately no total-duration field, because
     * the caller already has a better one - cancelling the collection ends the turn and kills
     * the process - and two competing deadlines is how the shorter one ends up killing turns
     * the caller thought it had allowed.
     */
    val idleTimeoutMs: Long = 600_000,
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
 * Tokens a turn spent, as the engine reported them.
 *
 * Separate from [AiUsage] rather than reusing it: that type is released and has no cached
 * count, and adding a parameter to it now would break every compiled caller. Cached input is
 * worth carrying because it is priced differently - an order of magnitude cheaper - so a
 * total that folds it in cannot be re-priced by anyone downstream.
 *
 * The implementation has these numbers whether or not a caller asked for pricing: it cannot
 * apply [AiCliPricing] without them. Reporting them is the difference between a consumer
 * showing "1,203 tokens" and showing nothing the moment a user selects a CLI engine.
 *
 * The two totals have no defaults on purpose. `AiCliUsage()` would mean "the engine reported
 * zero tokens", which is indistinguishable from "the engine reported nothing" - and that case
 * already belongs to a null [AiCliEvent.Completed.usage]. Collapsing them is the same
 * fabricate-a-fact mistake the null-versus-empty rule on denials exists to prevent.
 */
data class AiCliUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    /**
     * Input tokens written to a provider's cache, which bill **above** the input rate rather
     * than below it. Zero when the provider does not report it.
     *
     * Here now rather than later because the implementation already reads it: Claude Code
     * reports `cache_creation_input_tokens` in the same block the cost comes from, so this was
     * a number being discarded rather than one being predicted.
     */
    val cacheWriteTokens: Int = 0,
    /**
     * Tokens spent on reasoning, where a provider reports them apart from output.
     *
     * Predicted rather than currently read, and included because [AiCliEvent.ThinkingDelta]
     * already exists on this interface - so an api that streams reasoning and then cannot
     * account for it is an obvious next gap, and this type is frozen from 1.0.78.
     */
    val reasoningTokens: Int = 0,
    /**
     * Counts this type does not model yet, keyed by name.
     *
     * Token accounting grows - the two-tier cache write on [AiCliPricing.extras] is the case
     * already in the wild - and this is a data class under a rule that forbids new parameters
     * after 1.0.78. Values are strings for the same reason [AiCliSessionSpec.extras] uses
     * them: the hatch has to outlive whatever type the next count wants to be.
     */
    val extras: Map<String, String> = emptyMap(),
    /**
     * Part of [inputTokens], not additional to it. Billed far cheaper where a provider says.
     *
     * The one count that keeps a default, because zero is a real answer here: a provider that
     * does not break out caching reports none, which is a different fact from the turn having
     * no tokens at all.
     */
    val cachedInputTokens: Int = 0,
) {
    /**
     * Every token the turn consumed.
     *
     * [cachedInputTokens] is deliberately absent because it is part of [inputTokens] already;
     * the other two are not, and dropping them was silently wrong by orders of magnitude - a
     * resumed session reporting 2,000 input, 800 output and 500,000 cache writes rendered
     * "2,800 tokens" for a turn that consumed about 502,000.
     *
     * Note this differs from [AiUsage.totalTokens], which sums two fields over a two-field
     * type where that IS the total. A reader who knows the released type would assume the same
     * formula here, which is how the bug got in.
     */
    val totalTokens: Int get() = inputTokens + outputTokens + cacheWriteTokens + reasoningTokens
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
    /**
     * Rate for [AiCliUsage.cachedInputTokens], or null to price them at [inputPer1M].
     *
     * Without this the breakdown [AiCliUsage] carries is unusable: an implementation that
     * knows 500k of a resumed session's input was cached still has to bill it at the full
     * rate, which on Anthropic's roughly tenfold discount quotes about `$1.56` for a turn that
     * cost about `$0.21`. A consumer with a spend cap then trips on a turn that cost a seventh
     * of the number it was given - the same fabricated-figure-in-front-of-a-cap failure the
     * null-versus-zero rule on `pricing` exists to prevent.
     */
    val cachedInputPer1M: Double? = null,
    /** Rate for [AiCliUsage.cacheWriteTokens], or null to price them at [inputPer1M]. */
    val cacheWritePer1M: Double? = null,
    /**
     * Rates this type does not model yet, keyed by name.
     *
     * Unlike [AiCliDeniedCall], a rate card demonstrably grows, and there is already a case in
     * the wild: Anthropic prices **two** cache-write tiers, five-minute and one-hour, at
     * different rates. Expressing that needs a rate here and a count on [AiCliUsage], which is
     * exactly the paired breaking change everything else in this file is organised to avoid.
     *
     * Unknown keys are ignored, never rejected.
     */
    val extras: Map<String, String> = emptyMap(),
)

/**
 * One of the caller's own tools, served to the agent for the length of a turn.
 *
 * The implementation stands up the loopback MCP server, advertises this in its `tools/list`,
 * and routes a call back to [handle] with the same per-turn identity that guards approvals.
 * What the tool is called, what it does, and what it says are entirely the caller's.
 *
 * Not a data class: it holds a function, so `copy` and `equals` would be meaningless and the
 * generated `toString` would render a lambda where a reader wants a name.
 */
class AiCliHostedTool(
    /**
     * Name the agent calls it by. The agent sees it namespaced by the serving implementation,
     * which is what [AiCliSessionAPI.qualifiedToolName] answers.
     *
     * **Letters, digits and underscores.** MCP names are constrained in practice, so a name
     * with a space or a dot in it lands in the silent failure `qualifiedToolName` describes:
     * the agent is told it lacks permission to use a tool nobody can find.
     *
     * Two entries in one `tools` list sharing a name is **undefined** - do not do it; compose
     * from one source or de-duplicate first. A name colliding with the implementation's own
     * permission-prompt tool loses: that channel is what makes gated calls answerable at all,
     * and letting a caller shadow it would turn every approval into an unexplained failure.
     */
    val name: String,
    /** What it does, which is what the agent chooses on. Worth writing carefully. */
    val description: String,
    /** JSON Schema for the arguments, as a JSON string. `{}` for no arguments. */
    val inputSchema: String = "{}",
    /**
     * What to answer when the call cannot be routed to the turn that made it - a process
     * that outlived its turn, a caller with no identity, a handler that failed.
     *
     * The caller supplies it because only the caller knows how its tool's answers read. A
     * refused *approval* is a denial; a refused *question* is not - it is "nobody is waiting
     * for this any more", and returning an error there would have the agent apologise about
     * tooling to a user who never saw a question. Every outcome of a tool has to read as
     * something the agent can act on, and that sentence differs per tool.
     *
     * Blank falls back to a generic refusal.
     */
    val unroutableAnswer: String = "",
    /**
     * Run the call and return what the agent should see, as text.
     *
     * Invoked on a thread the implementation owns while the agent holds a connection open,
     * exactly as the approval callback is - so it may suspend for as long as it needs, must
     * be safe off the collector's thread, and must not touch UI state directly. A throw is
     * answered with [unroutableAnswer] rather than left as a connection the agent never gets
     * a reply on.
     */
    val handle: suspend (argumentsJson: String) -> String,
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

/**
 * One tool call the engine refused, as it reported it.
 *
 * Deliberately frozen with no `extras`, unlike [AiCliSessionSpec] and [AiCliEngine]. Its shape
 * is not ours to grow: it mirrors what an engine reports about a refusal, which is a tool name
 * and at most a call id. The same decision applies to [AiCliApprovalAsk] and
 * [AiCliApprovalAnswer] - a question and a verdict - where a third field would mean the
 * concept had changed rather than grown.
 */
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
 * No subclass here has `equals`, so a consumer testing an event stream matches structurally.
 * That is the trade against the sealed-and-data hazards below; [AiCliDeniedCall],
 * [AiCliUsage] and [AiCliPricing] are data classes and do compare by value.
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

    /**
     * The agent started a tool call. [id] pairs it with its [ToolResult].
     *
     * **[input] is as sensitive as [AiCliApprovalAsk.inputJson]** and for the same reason: it
     * is the same model-authored arguments, which routinely carry a credential. Being a plain
     * class only stops an interpolation of the *event* from leaking; it does nothing for
     * `logger.debug("tool input: " + event.input)`, which is the more likely line in a
     * consumer that renders a transcript.
     */
    class ToolUse(val id: String, val name: String, val input: String) : AiCliEvent()

    /**
     * A tool call returned.
     *
     * **[content] is sensitive**, same standard as [ToolUse.input]: it is whatever the tool
     * read or produced, which for a secrets tool or a file read is the secret itself.
     */
    class ToolResult(val id: String, val content: String, val isError: Boolean) : AiCliEvent()

    /**
     * Running cost for the turn so far, when [AiCliSessionSpec.pricing] was supplied.
     *
     * Cumulative, not a delta: each one is the cost of the turn so far, so a consumer
     * displays the latest rather than summing them. [Completed.costUsd] then supersedes the
     * last of these as the authoritative total.
     *
     * Emitted only when [AiCliSessionSpec.pricing] was supplied. An engine that reports its
     * own cost reports it once, on the terminal event.
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
     * @param costUsd the **authoritative total** for the turn, or null when neither the
     *   engine nor [AiCliSessionSpec.pricing] could produce one. It **supersedes** every
     *   prior [CostUpdate] rather than adding to it: a spend tracker that accumulates the
     *   updates and then adds this double-bills the turn, which is the same class of
     *   ambiguity the null-versus-zero rule on `pricing` exists to remove.
     * @param usage tokens the engine reported for the turn, or null when it reported none.
     *   Independent of [costUsd]: an engine on a subscription login reports tokens and no
     *   price, which is exactly the case where a consumer still wants to show a count.
     * @param deniedWithoutAsking tool calls **this implementation** refused rather than the
     *   caller: a request from a process that outlived its turn, one carrying no turn
     *   identity at all, a turn abandoned while a prompt was on screen, and a callback that
     *   threw or was cancelled instead of answering. They still reach the engine as refusals
     *   and so still appear in [permissionDenials].
     *
     *   Note the last case carefully: the callback *was* invoked, so a caller may have seen
     *   it start. What it did not do is produce a verdict, which is why the refusal is the
     *   implementation's and belongs here. Everything the caller actually decided is absent
     *   from this list, so subtracting it cannot double-count.
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
        val usage: AiCliUsage? = null,
    ) : AiCliEvent()

    /**
     * The turn failed. Terminal.
     *
     * Carries no session id on purpose - a turn can fail before one exists. A caller that
     * wants to resume after a failure retains [Started.sessionId], which arrives first
     * whenever there is one to have.
     *
     * @param message what failed, written for a person. **An implementation must not
     *   reproduce [AiCliSessionSpec.envOverrides] values or [AiCliSessionSpec.mcpConfigJson]
     *   content in it.** This is the direction the data actually leaves by, and a spawn
     *   failure rendered as the argv plus the environment it applied walks straight past
     *   every redaction on the way in - into a panel, and into whatever the caller logs.
     * @param costUsd as [Completed.costUsd], and authoritative in the same way: it supersedes
     *   every prior [CostUpdate] rather than adding to it. Without it a spend tracker could
     *   not tell whether the last update stood or should be discarded on a failure - and an
     *   idle-timeout kill is precisely a turn that spent money and then failed, so discarding
     *   under-bills it. Null means neither the engine nor [AiCliSessionSpec.pricing] could
     *   produce a figure, and the last [CostUpdate] is then the best available estimate.
     * @param usage as [Completed.usage], and carried here for a stronger reason than
     *   symmetry: the failures this api is built to expect are an idle-timeout kill and a
     *   process that dies mid-turn, and both can spend a great many tokens against the
     *   user's quota before they happen. A turn that failed is the one most likely to have
     *   cost something and the one least likely to be able to say so.
     * @param permissionDenials as [Completed.permissionDenials]. Carried here too because
     *   a turn that ended in an error still reports what it refused, and dropping it
     *   silently regresses exactly those turns.
     * @param deniedWithoutAsking as [Completed.deniedWithoutAsking].
     */
    class Failed(
        val message: String,
        val permissionDenials: List<AiCliDeniedCall>? = null,
        val deniedWithoutAsking: List<AiCliDeniedCall> = emptyList(),
        val usage: AiCliUsage? = null,
        val costUsd: Double? = null,
    ) : AiCliEvent()
}
