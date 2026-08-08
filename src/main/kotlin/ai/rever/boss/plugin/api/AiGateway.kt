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
     */
    suspend fun runAgent(
        request: AiRequest,
        tools: List<AiToolSpec>,
        budget: AiBudget = AiBudget(),
        invoke: suspend (AiToolCall) -> AiToolOutcome,
    ): Result<AiAgentResult>

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

        /** [capabilities] entry: [AiMessage.Image] parts are sent rather than dropped. */
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
     * Sampling temperature, or null to use the value the user chose for the
     * active provider. Prefer null: the user's setting is usually the right one,
     * and some models reject an explicit value.
     */
    val temperature: Float? = null,
    /** Output token ceiling, or null to use the user's configured value. */
    val maxTokens: Int? = null,
    /**
     * Wall-clock bound on the whole request. A caller rendering into a panel
     * wants a failure it can show, not an indefinite spinner.
     */
    val timeoutMs: Long = 120_000,
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
    /** `"user"`, `"assistant"`, or `"tool"`. Treat as an open set. */
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
        fun user(text: String): AiMessage = AiMessage(ROLE_USER, text)

        fun assistant(text: String): AiMessage = AiMessage(ROLE_ASSISTANT, text)

        const val ROLE_USER: String = "user"
        const val ROLE_ASSISTANT: String = "assistant"
        const val ROLE_TOOL: String = "tool"
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

/** Why a [AiGatewayAPI.runAgent] run stopped. Treat as an open set. */
enum class AiStopReason {
    /** The model answered with no further tool calls. The only clean finish. */
    COMPLETED,

    /** [AiBudget.maxSteps] reached. The answer is whatever it had said by then. */
    MAX_STEPS,

    /** [AiBudget.timeoutMs] elapsed. */
    TIMEOUT,

    /** [AiBudget.maxTokens] reached. */
    TOKEN_BUDGET,
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
