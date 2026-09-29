package ai.rever.boss.plugin.api

/**
 * Typed decisions from System One models: Jev on OpenRouter, and Jev-compatible local
 * runtimes such as Ollaya. Registered by the AI gateway plugin; resolve it lazily, per call,
 * with `getPluginAPI(AiDecisionAPI::class.java)`, since plugin load order is not guaranteed.
 *
 * Plugin-to-plugin, deliberately not [HostImplemented]: it ships in the api jar alone, so
 * gate on `minApiVersion` 1.0.96.
 *
 * Bodies are SystemOne JSON text, passed through unmodified: `{model, state, questions}` in,
 * `{model, answers, usage}` out. Callers own request and response validation.
 */
interface AiDecisionAPI {
    /**
     * Providers that serve decision models, each with its models. Local providers are probed on
     * every call, with a short timeout; one that does not answer is still listed, with
     * [AiDecisionProvider.reachable] false and no models. Call it on demand (opening a picker, a
     * refresh), never from recomposition or a timer.
     */
    suspend fun decisionProviders(): List<AiDecisionProvider>

    /**
     * Posts [AiDecisionRequest.body] to exactly [AiDecisionRequest.providerId]. Never falls back
     * to another provider: a caller that picked a local model must not have its state sent to a
     * cloud one.
     *
     * Every failure is an [AiDecisionException]; implementations wrap anything else as
     * [AiDecisionException.UPSTREAM_ERROR] or [AiDecisionException.NETWORK_ERROR].
     * `CancellationException` is rethrown, never wrapped in `Result.failure`.
     */
    suspend fun decide(request: AiDecisionRequest): Result<AiDecisionReply>
}

/**
 * One decision call.
 *
 * Never add a constructor parameter: it moves the constructor and `copy$default`, which breaks
 * consumers built earlier. Extend through [extras], with a body-level view over it as
 * [AiRequest.modelOverride] does. Model-level options belong in the SystemOne [body].
 */
data class AiDecisionRequest(
    val providerId: String,
    /** SystemOne request JSON; its `model` names the model. */
    val body: String,
    /** Outside [MIN_TIMEOUT_MS]..[MAX_TIMEOUT_MS] fails with `INVALID_INPUT`; never clamped. */
    val timeoutMs: Long = 30_000,
    /** Enforced while streaming, not after buffering. `<= 0` fails with `INVALID_INPUT`. */
    val maxResponseBytes: Int = 1_048_576,
    /** Transport or gateway hints. Unknown keys are ignored, never rejected. Never credentials. */
    val extras: Map<String, String> = emptyMap(),
) {
    companion object {
        const val MIN_TIMEOUT_MS = 1_000L
        const val MAX_TIMEOUT_MS = 120_000L
    }
}

/** Never add a constructor parameter; extend through [extras], as on [AiDecisionRequest]. */
data class AiDecisionReply(
    /** The provider's SystemOne response JSON, unmodified. */
    val body: String,
    val providerId: String,
    val latencyMs: Long,
    /** Unknown keys are ignored. Never credentials. */
    val extras: Map<String, String> = emptyMap(),
)

/** Never add a constructor parameter; extend through [extras], as on [AiDecisionRequest]. */
data class AiDecisionProvider(
    val providerId: String,
    val providerName: String,
    /**
     * True only for loopback endpoints (127.0.0.0/8, ::1, localhost), so request state never
     * leaves this machine. Never true for a LAN address.
     */
    val local: Boolean,
    val reachable: Boolean,
    val models: List<AiDecisionModel>,
    /**
     * Why it is unusable, or where it listens; plain text, rendered verbatim. Never contains
     * keys, tokens or request content.
     */
    val detail: String? = null,
    /**
     * True when the provider is unusable only because no credential is configured, so a UI can
     * offer setup instead of a generic error. Implies [reachable] is false.
     */
    val needsCredential: Boolean = false,
    /** Unknown keys are ignored. Never credentials. */
    val extras: Map<String, String> = emptyMap(),
)

data class AiDecisionModel(
    val id: String,
    val displayName: String = id,
)

/** Failure from [AiDecisionAPI.decide]; [code] is one of the constants below. Treat as an open set. */
class AiDecisionException(
    val code: String,
    message: String,
) : Exception(message) {
    companion object {
        const val UNKNOWN_PROVIDER = "UNKNOWN_PROVIDER"
        const val MISSING_CREDENTIAL = "MISSING_CREDENTIAL"
        const val AUTH_ERROR = "AUTH_ERROR"
        const val INVALID_INPUT = "INVALID_INPUT"
        const val MODEL_NOT_FOUND = "MODEL_NOT_FOUND"
        const val RATE_LIMITED = "RATE_LIMITED"
        const val UPSTREAM_ERROR = "UPSTREAM_ERROR"
        const val TIMEOUT = "TIMEOUT"
        const val NETWORK_ERROR = "NETWORK_ERROR"
        const val RESPONSE_TOO_LARGE = "RESPONSE_TOO_LARGE"
        const val LOCAL_UNAVAILABLE = "LOCAL_UNAVAILABLE"
    }
}
