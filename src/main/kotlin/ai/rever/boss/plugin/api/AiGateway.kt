package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow

/**
 * One AI interface for every plugin, so none of them has to speak a provider's
 * wire format.
 *
 * [LlmProvider] answers "which provider, which credential, which endpoint".
 * That left each consumer to build the request body, attach the credential the
 * way that provider wants it, and parse the reply, and three plugins ended up
 * with three copies of the same code. Worse, the copies branch on
 * [LlmApiFormat], which is an open set: a `when` that was exhaustive when it
 * compiled throws `NoWhenBranchMatchedException` the first time a newer constant
 * reaches it, so adding a provider silently broke consumers built before it.
 *
 * This interface removes the format from the caller's problem entirely. An
 * [AiRequest] says what to ask, never how to encode it; the implementation
 * resolves the active provider itself and owns the only `when` over
 * [LlmApiFormat] in the workspace. A plugin that goes through here keeps working
 * when a provider is added, without a rebuild.
 *
 * Obtained the same way as any other plugin API, and **resolved lazily**, not
 * cached at `register()`:
 *
 * ```kotlin
 * val ai = context.getPluginAPI(AiGatewayAPI::class.java) ?: return  // hide AI affordances
 * val reply = ai.complete(AiRequest(system = "...", messages = listOf(AiMessage.user("..."))))
 * ```
 *
 * Load order across plugins is not guaranteed, so resolving once at registration
 * can cache a null forever.
 *
 * Every entry point returns [Result] rather than throwing: a provider being
 * unreachable, unconfigured or out of quota is an ordinary outcome here, and a
 * plugin that treats it as one degrades instead of crashing a panel.
 */
interface AiGatewayAPI {

    /**
     * One request, one reply. The common case: a code edit, a fix, a chat turn.
     *
     * Fails when no provider is configured, which is not an error to report as a
     * crash - it means the user has not set a key up, and the caller should hide
     * its AI affordances.
     */
    suspend fun complete(request: AiRequest): Result<AiReply>

    /**
     * The same request, streamed as it arrives.
     *
     * Emits [AiChunk.Text] as tokens land and exactly one terminal element,
     * either [AiChunk.Completed] or [AiChunk.Failed]. Implementations that cannot
     * stream a given provider fall back to a single [AiChunk.Text] followed by
     * [AiChunk.Completed], so a caller never has to ask whether streaming is
     * supported.
     *
     * **The flow never throws**, except [kotlinx.coroutines.CancellationException].
     * Every failure arrives as [AiChunk.Failed] and the flow then completes normally.
     * Without that guarantee a caller collecting in a `LaunchedEffect` would need both
     * a `Failed` branch and a `catch {}` to avoid taking its panel down, which is the
     * outcome this interface exists to rule out.
     */
    fun stream(request: AiRequest): Flow<AiChunk>

    /**
     * A bounded tool-use loop: ask the model, run the tools it asks for, feed the
     * results back, repeat until it answers or [budget] stops it.
     *
     * [invoke] runs one tool call and is the caller's own code, so the gateway
     * never needs access to whatever the tools reach. Tool output is passed back
     * to the model as observation data rather than as a new instruction, which is
     * what keeps a tool result from reading as a prompt.
     *
     * Returns why it stopped as well as what it said - a caller that cannot tell
     * "answered" from "ran out of steps" will present a truncated run as a
     * finished one.
     *
     * Two bounds are in scope and they do not overlap: [AiRequest.timeoutMs] bounds
     * **one model turn**, [AiBudget.timeoutMs] bounds **the whole run**, and whichever
     * trips first wins.
     *
     * If [invoke] throws it is caught and fed back to the model as an
     * [AiToolOutcome] with `isError = true`, so one failing tool does not end the run -
     * the model is told and can try something else. An [AiToolOutcome] whose id matches
     * no outstanding call is passed through to the provider, which will reject it; the
     * ids come from [AiToolCall.id] and should be echoed unchanged.
     */
    suspend fun runAgent(
        request: AiRequest,
        tools: List<AiToolSpec>,
        budget: AiBudget = AiBudget(),
        invoke: suspend (AiToolCall) -> AiToolOutcome,
    ): Result<AiAgentResult>

    /**
     * One model turn, for a caller that owns its own loop.
     *
     * [runAgent] is the right entry point for "ask, run tools, repeat until answered".
     * This is the primitive underneath it, for a caller whose loop is already part of
     * something else - a node in a graph, a step in a workflow - and whose stopping rules
     * are its own. Such a caller wants the model's tool calls handed back rather than run.
     *
     * Pass the plain conversation in [AiRequest.messages] and **every** completed tool
     * round in [rounds], oldest first.
     *
     * Tool rounds are separate from messages, and complete rather than just the latest,
     * for two reasons that are easy to get wrong:
     *
     * - a provider will not accept a tool result on its own. Anthropic rejects a
     *   `tool_result` whose `tool_use` was not replayed; the Responses API needs the
     *   `function_call` item beside its output. The correlation is structural, so a
     *   flattened "tool" message with no id cannot express it - which is why [AiMessage]
     *   has no tool role.
     * - passing only the most recent round loses earlier observations. A caller whose loop
     *   runs more than twice would silently show the model none of what its first tools
     *   returned, and it would re-call them or answer without the evidence. Nothing errors;
     *   the run just gets worse. Send the whole list every step.
     *
     * The default body exists so a plugin built against a later api keeps loading on an
     * older gateway, and degrades to a tool-less reply rather than failing.
     */
    suspend fun step(
        request: AiRequest,
        tools: List<AiToolSpec> = emptyList(),
        rounds: List<AiRound> = emptyList(),
    ): Result<AiTurn> =
        if (tools.isNotEmpty()) {
            // Never silently. Mapping complete() would return an AiTurn with empty
            // toolCalls, which this api documents as "a final answer" - so a caller that
            // advertised five tools would be told the model considered them and declined.
            // For a node in a graph that is a wrong result, not a degradation.
            Result.failure(
                UnsupportedOperationException(
                    "This AI gateway predates step() with tools. Check capabilities() for " +
                        "${CAPABILITY_TOOLS} before advertising tools.",
                ),
            )
        } else {
            complete(request).map { AiTurn(text = it.text, usage = it.usage, modelId = it.modelId) }
        }

    /**
     * What this implementation can actually do right now, for a caller that wants
     * to adapt rather than fail.
     *
     * Strings, not an enum, on purpose: a caller comparing against a constant it
     * compiled with is the exact trap this interface exists to remove. Unknown
     * entries are expected and must be ignored.
     */
    fun capabilities(): Set<String> = emptySet()

    /**
     * The provider a request would go to, or null when nothing is configured.
     *
     * For display only ("Ask Claude Opus 5"), and for deciding whether to show an
     * AI affordance at all. A caller that wants to *make* a request should just
     * make it and handle the failure, because the active provider can change
     * between the two calls.
     */
    fun activeModel(): AiModelInfo? = null

    companion object {
        /** [capabilities] entry: [stream] genuinely streams for the active provider. */
        const val CAPABILITY_STREAMING: String = "streaming"

        /** [capabilities] entry: [runAgent] can advertise tools to the active provider. */
        const val CAPABILITY_TOOLS: String = "tools"

        /** [capabilities] entry: [AiImage] parts are sent rather than dropped. */
        const val CAPABILITY_VISION: String = "vision"
    }
}

/**
 * What to ask, with no statement of how to encode it.
 *
 * There is deliberately no provider, endpoint, credential or wire format here.
 * Those are resolved per call from the user's configured providers, so a request
 * built once stays correct when the user switches provider - and a plugin cannot
 * accidentally pin itself to one vendor.
 */
data class AiRequest(
    /**
     * Standing instructions for the model. Sent wherever the active provider puts
     * them, which is not the same place for every provider.
     */
    val system: String = "",
    /** The conversation so far, oldest first. */
    val messages: List<AiMessage> = emptyList(),
    /**
     * Sampling temperature, or null to **send none at all** and let the model apply
     * its own default.
     *
     * Prefer null. Not because a setting elsewhere fills it in - nothing does; there is
     * no temperature control in AI Providers, and [LlmConfig.temperature] is a
     * non-null field defaulting to 0.7 that no settings surface ever writes - but
     * because an unset sampling parameter is the right request far more often than any
     * fixed value is. Newer reasoning models reject `temperature` outright, and a
     * gateway that supplied a default on the caller's behalf made those models
     * unusable with a 400 naming a parameter the caller never set.
     */
    val temperature: Float? = null,
    /** Output token ceiling, or null to use the user's configured value. */
    val maxTokens: Int? = null,
    /**
     * Wall-clock bound on **one** request, so within [AiGatewayAPI.runAgent] this
     * bounds a single model turn rather than the run - [AiBudget.timeoutMs] bounds the
     * run, and whichever trips first wins. A caller rendering into a panel wants a
     * failure it can show, not an indefinite spinner.
     */
    val timeoutMs: Long = 120_000,
    /**
     * Provider-agnostic hints, for anything this type does not model yet.
     *
     * The escape hatch, and it exists because of a hard constraint rather than as a
     * convenience: every type here is a data class, so **adding a constructor parameter
     * later is a breaking change** - the synthetic constructor descriptor and
     * `copy$default` both move, and a plugin compiled against an earlier api gets
     * `NoSuchMethodError` on a call it never touched. `stopSequences`, `topP`,
     * `toolChoice` and a per-request model tier are all things this will want; without
     * somewhere to put them, each one costs an api release plus a host release plus a
     * rebuild of every consumer.
     *
     * Unknown keys are **ignored**, never rejected, so a hint added later degrades on an
     * older gateway instead of failing. Do not put credentials here.
     */
    val extras: Map<String, String> = emptyMap(),
)

/**
 * One turn in a conversation.
 *
 * A sealed hierarchy would be the natural shape, but it cannot cross this
 * boundary: adding a case to a sealed type is a breaking change for every
 * already-compiled plugin that matched on it. So this is an ordinary class with
 * a [role] and optional parts, and an unrecognised [role] is treated as user
 * text rather than rejected.
 */
data class AiMessage(
    /**
     * `"user"` or `"assistant"`. Treat as an open set: an unrecognised role is sent as
     * user text rather than rejected. There is no tool role - see the companion.
     */
    val role: String,
    /** The text of this turn. Empty when the turn carries only [images]. */
    val text: String = "",
    /**
     * Images to send with this turn, ignored unless
     * [AiGatewayAPI.CAPABILITY_VISION] is present. Dropped rather than failing,
     * because a caller that can degrade to text should not have to check first.
     */
    val images: List<AiImage> = emptyList(),
) {
    companion object {
        const val ROLE_USER: String = "user"
        const val ROLE_ASSISTANT: String = "assistant"

        fun user(text: String): AiMessage = AiMessage(ROLE_USER, text)

        fun assistant(text: String): AiMessage = AiMessage(ROLE_ASSISTANT, text)

        // There is deliberately no ROLE_TOOL. A tool result is only meaningful next to
        // the call it answers - Anthropic needs the tool_use replayed, OpenAI needs a
        // tool_call_id, Responses needs the function_call item - and this type carries
        // no id, so such a message would be unencodable. Tool results travel as
        // AiGatewayAPI.step's toolOutcomes, or inside runAgent, where the ids survive.
    }
}

/** An image to send with a message. Bytes, because a URL the model cannot reach is worse than no image. */
data class AiImage(
    /** Raw image bytes. */
    val bytes: ByteArray,
    /** IANA media type, e.g. `image/png`. */
    val mediaType: String,
) {
    // Generated equals/hashCode compare the array by identity, which makes two
    // equal images unequal. Spelled out so an AiMessage holding one behaves.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiImage) return false
        return mediaType == other.mediaType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * mediaType.hashCode() + bytes.contentHashCode()
}

/**
 * One completed tool round: the assistant turn that asked, and what the tools returned.
 *
 * The unit a multi-step transcript is made of. Kept as a pair rather than two parallel
 * lists because the pairing is the whole point - an outcome without its originating call is
 * something no provider will accept.
 */
data class AiRound(
    val turn: AiTurn,
    val outcomes: List<AiToolOutcome> = emptyList(),
)

/**
 * One assistant turn: what it said, what it wants to run, and what it cost.
 *
 * The unit [AiGatewayAPI.step] deals in. An empty [toolCalls] with [text] is a final
 * answer; a non-empty [toolCalls] means "run these and step me again with the results".
 */
data class AiTurn(
    val text: String = "",
    val toolCalls: List<AiToolCall> = emptyList(),
    val usage: AiUsage? = null,
    /** The model that answered, when the provider reports it. */
    val modelId: String = "",
)

/** What the model said, plus what it cost. */
data class AiReply(
    /** The reply text, already stripped of provider envelope. */
    val text: String,
    /** Token usage as the provider reported it, or null when it reported none. */
    val usage: AiUsage? = null,
    /** The model that answered, which may differ from the one requested. */
    val modelId: String = "",
)

/** Tokens spent on a request. Zero rather than absent when a provider reports partially. */
data class AiUsage(
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
) {
    val totalTokens: Int get() = inputTokens + outputTokens

    operator fun plus(other: AiUsage): AiUsage =
        AiUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens)
}

/**
 * One element of a streamed reply.
 *
 * Not a sealed interface, for the reason given on [AiMessage]: a new case would
 * break every compiled `when`. Callers should handle the three below and ignore
 * anything else.
 */
abstract class AiChunk private constructor() {
    /** A fragment of the reply. Fragments concatenate; they are not lines or words. */
    class Text(val text: String) : AiChunk()

    /** The stream finished normally. Always the last element on success. */
    class Completed(val reply: AiReply) : AiChunk()

    /** The stream ended early. Always the last element on failure. */
    class Failed(val error: Throwable) : AiChunk()
}

/**
 * A tool the model may call, described to it as JSON Schema.
 *
 * Same shape the MCP tool registry already uses, so a plugin can advertise its
 * `McpToolDefinition`s to a model without translating them.
 */
data class AiToolSpec(
    /** Name the model will use to call it. */
    val name: String,
    /** What it does, which is what the model chooses on. Worth writing carefully. */
    val description: String,
    /** JSON Schema for the arguments, as a JSON string. `{}` for no arguments. */
    val inputSchema: String = "{}",
)

/** The model's request to run one tool. [id] correlates it with its [AiToolOutcome]. */
data class AiToolCall(
    val id: String,
    val name: String,
    /** Arguments as a JSON object string, exactly as the model produced them. */
    val argumentsJson: String,
)

/** The result of one [AiToolCall], fed back to the model as data. */
data class AiToolOutcome(
    val id: String,
    /** What the tool produced, or the error text when [isError]. */
    val content: String,
    /** True when the call failed. The model is told, so it can try something else. */
    val isError: Boolean = false,
)

/** Bounds on one [AiGatewayAPI.runAgent] run. All are enforced; the first to trip wins. */
data class AiBudget(
    /** Model turns, not tool calls. One turn may ask for several tools. */
    val maxSteps: Int = 8,
    /** Wall clock for the whole run. */
    val timeoutMs: Long = 300_000,
    /** Total tokens across every turn, when the provider reports usage. */
    val maxTokens: Int = Int.MAX_VALUE,
)

/**
 * Why a [AiGatewayAPI.runAgent] run stopped.
 *
 * **Always write an `else` branch when matching on this.** It is an enum, and a `when`
 * that was exhaustive when it compiled throws `NoWhenBranchMatchedException` the first
 * time a newer constant reaches it - the same trap [LlmApiFormat] documents, and the one
 * this whole interface exists to remove. [UNKNOWN] is here so an `else` has something
 * sensible to fall back to, and so a future reason can be introduced without every
 * already-compiled caller having to be right about it.
 */
enum class AiStopReason {
    /** The model answered with no further tool calls. The only clean finish. */
    COMPLETED,

    /** [AiBudget.maxSteps] reached. The answer is whatever it had said by then. */
    MAX_STEPS,

    /** [AiBudget.timeoutMs] elapsed. */
    TIMEOUT,

    /** [AiBudget.maxTokens] reached. */
    TOKEN_BUDGET,

    /**
     * The provider ended the turn itself - a refusal, a content filter, or its own
     * output-length cap. Distinct from [COMPLETED] because the answer may be partial
     * or absent, and a caller should not present it as a finished result.
     */
    PROVIDER_STOPPED,

    /**
     * A reason this build does not know. Only produced by a gateway newer than the
     * caller; treat as "the run ended and the result may be incomplete".
     */
    UNKNOWN,
}

/** The outcome of an agent run, including why it ended. */
data class AiAgentResult(
    val text: String,
    val stopReason: AiStopReason,
    /** Model turns taken. */
    val steps: Int,
    /** Tool calls executed across every turn. */
    val toolCalls: Int,
    val usage: AiUsage = AiUsage(),
)

/** The provider and model a request would currently use. Display only. */
data class AiModelInfo(
    /** Stable provider id, e.g. `ANTHROPIC`. Treat as an open set. */
    val providerId: String,
    /** Human-readable provider name, e.g. `Anthropic`. */
    val providerName: String,
    /** Selected model id, e.g. `claude-opus-5`. */
    val modelId: String,
)
