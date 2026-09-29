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
     * [AiDecisionProvider.reachable] false and no models.
     */
    suspend fun decisionProviders(): List<AiDecisionProvider>

    /**
     * Posts [AiDecisionRequest.body] to exactly [AiDecisionRequest.providerId]. Never falls back
     * to another provider: a caller that picked a local model must not have its state sent to a
     * cloud one. Failures are [AiDecisionException].
     */
    suspend fun decide(request: AiDecisionRequest): Result<AiDecisionReply>
}

/**
 * One decision call. Extend by adding a body-level `val`, never a constructor parameter: a new
 * parameter moves the constructor and `copy$default`, which breaks consumers built earlier.
 */
data class AiDecisionRequest(
    val providerId: String,
    /** SystemOne request JSON; its `model` names the model. */
    val body: String,
    val timeoutMs: Long = 30_000,
    val maxResponseBytes: Int = 1_048_576,
)

data class AiDecisionReply(
    /** The provider's SystemOne response JSON, unmodified. */
    val body: String,
    val providerId: String,
    val latencyMs: Long,
)

data class AiDecisionProvider(
    val providerId: String,
    val providerName: String,
    /** True when the endpoint is on this machine, so request state never leaves it. */
    val local: Boolean,
    val reachable: Boolean,
    val models: List<AiDecisionModel>,
    /** Why it is unusable, or where it listens; plain text for display. */
    val detail: String? = null,
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
