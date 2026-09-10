package ai.rever.boss.plugin.api

/**
 * Read-only access to the AI provider configuration the user set up in
 * Secret Manager → AI (also available from Settings → AI Providers). Plugins reuse
 * provider connections and credentials while owning their own model selection;
 * [activeConfig] supplies a ready-to-use generation default for consumers that need one.
 *
 * The implementation is backed by the plugin that owns provider configuration (see
 * [LlmProviderSettingsAPI]), which stores credentials as secrets and resolves a
 * provider's key from the environment first, then from the stored secret.
 *
 * Like every provider on [PluginContext], this may be null — plugins must degrade
 * gracefully (hide AI affordances) when LLM access isn't available. It is also null
 * when the owning plugin isn't installed or hasn't finished registering.
 */
@HostImplemented
interface LlmProvider {
    /**
     * A ready-to-use default: the active provider connection with a resolved endpoint,
     * model and credential when that provider requires one.
     *
     * Returns null when no active provider, required credential or usable default model
     * can be resolved. A null value does not imply [configuredProviders] is empty:
     * consumers with their own model picker can still use a configured connection.
     */
    fun activeConfig(): LlmConfig?

    /**
     * Configured provider connections in display order, including keyless local services.
     * Useful for a consumer-owned provider/model picker, not a list of ready-made requests.
     * Every required credential must already be resolved; omit providers whose required
     * credential is missing. A blank [LlmConfig.apiKey] means no credential is required,
     * not that credential resolution is pending.
     * A connection may have a blank [LlmConfig.modelId] when its format sends model selection
     * separately from the endpoint; the consumer must supply a model before calling it.
     * This returns one connection per provider, not one per model. Formats that put the
     * model in the endpoint path (see [LlmConfig.baseUrl]) return the resolved default
     * model's endpoint, or omit that provider until a default can be resolved. To choose
     * another model on such a connection, route through an [AiGatewayAPI] implementation
     * supporting provider/model overrides: set `AiRequest.extras["providerId"]` and
     * [AiRequest.EXTRAS_KEY_MODEL_OVERRIDE]. The gateway owns model-path substitution;
     * changing [LlmConfig.modelId] alone does not update its endpoint. This cannot make an
     * omitted connection callable: a model-dependent provider still needs a resolved
     * default connection first. [activeConfig] retains its usable-default-or-null contract.
     */
    fun configuredProviders(): List<LlmConfig> = emptyList()

    /**
     * Every configured provider's available models, grouped by provider.
     *
     * Deliberately not built from [configuredProviders]: that returns [LlmConfig], which
     * carries a live API key, so a caller that only wants to list what it could ask for
     * would have to be handed every provider's credential just to read a model id off the
     * side. [AiProviderModels] carries none — plain id/name/context-length data, safe for
     * a picker that has no business holding a key.
     *
     * A provider whose model list has never been fetched (never configured, or a fetch
     * that has not landed yet) is simply absent from the result rather than reported with
     * an empty list — the two mean different things, and collapsing them would make "no
     * models" indistinguishable from "haven't looked yet".
     * A known catalog does not guarantee a callable connection: a model-in-path provider
     * may appear here while [configuredProviders] omits it for lacking a resolved default
     * model. Consumers must check connection availability before offering a model as usable.
     *
     * Default empty, the same reason [configuredProviders] degrades rather than throws:
     * an implementor older than this method has nothing to report, not a
     * `NoSuchMethodError` waiting for whoever calls it.
     */
    fun availableModels(): List<AiProviderModels> = emptyList()
}

/**
 * One provider's available models, as [LlmProvider.availableModels] groups them.
 *
 * Credential-free by construction — see [LlmProvider.availableModels] for why this
 * exists separately from [LlmConfig].
 */
@HostImplemented
data class AiProviderModels(
    /** Stable provider id, matching [LlmConfig.providerId]. Treat as an open set. */
    val providerId: String,
    /** Human-readable provider name, e.g. "Anthropic". */
    val providerName: String,
    val models: List<AiAvailableModel>,
)

/** One model a provider currently offers, without a credential attached. */
@HostImplemented
data class AiAvailableModel(
    /** Model id to send in a request, e.g. "claude-opus-5". */
    val id: String,
    /** Display name, falling back to [id] when the provider reports nothing better. */
    val displayName: String,
    /** Maximum input tokens, when the provider reports it. */
    val contextLength: Int? = null,
)

/**
 * A provider connection, optional credential and generation defaults. A value returned
 * by [LlmProvider.activeConfig] has a usable default model; a connection returned by
 * [LlmProvider.configuredProviders] may require the consumer to supply its own model.
 */
@HostImplemented
data class LlmConfig(
    /**
     * Stable provider id, e.g. "ANTHROPIC", "OPENAI", "GOOGLE", "XAI", "MOONSHOT",
     * "TOGETHER", "CUSTOM". Treat as an open set — new providers are added without an
     * api change, so match defensively rather than exhaustively.
     */
    val providerId: String,
    /** Human-readable provider name, e.g. "Anthropic". */
    val displayName: String,
    /** The request/response wire format [baseUrl] speaks. */
    val apiFormat: LlmApiFormat,
    /**
     * Resolved provider credential. In [LlmProvider.activeConfig] and
     * [LlmProvider.configuredProviders], blank means the service requires no credential.
     * Consumers must omit credential headers rather than send an empty authorization value.
     */
    val apiKey: String,
    /**
     * Full endpoint URL to POST to, e.g. "https://api.anthropic.com/v1/messages"
     * or "https://api.openai.com/v1/chat/completions" — no path building needed;
     * [apiFormat] describes the payload/headers this endpoint expects.
     *
     * Two guarantees, because both are otherwise ambiguous and getting either wrong
     * is silent:
     * - **[modelId] is already interpolated** where the format puts the model in the
     *   path. For [LlmApiFormat.GOOGLE_GENERATIVE] this is the complete
     *   `…/v1beta/models/{model}:generateContent`; do not append [modelId] again.
     *   [modelId] is still supplied separately, for formats that take it in the body
     *   and for display.
     * - **This never contains the credential.** Even for formats whose credential is
     *   a query parameter, [apiKey] is handed over separately and the caller attaches
     *   it. A key embedded in a URL is the version that leaks into logs, proxy access
     *   logs and crash reports, and callers routinely log request URLs.
     */
    val baseUrl: String,
    /**
     * Default model id. Nonblank in [LlmProvider.activeConfig]; may be blank in
     * [LlmProvider.configuredProviders] for a model-independent endpoint, in which case
     * the consumer must select a model.
     */
    val modelId: String,
    /** Sampling temperature. */
    val temperature: Float = 0.7f,
    /** Max tokens to generate. */
    val maxTokens: Int = 2000
)

/**
 * The wire format an LLM endpoint expects, so callers can build the right payload
 * without hard-coding provider names.
 *
 * **Treat this as an open set: always include an `else` branch.** New constants are
 * added as providers are supported, and a `when` that was exhaustive when it compiled
 * throws `NoWhenBranchMatchedException` the first time a newer constant reaches it —
 * the compiler cannot warn a plugin that was built before the constant existed.
 *
 * Note the gate: this type lives in the package the host compiles in and serves
 * parent-first, so the host's pinned copy is what every plugin resolves. A plugin
 * using a constant added in api X must gate on `minBossVersion` for the host release
 * that pins X, not `minApiVersion` alone — otherwise the constant is missing at
 * runtime (`NoSuchFieldError`).
 */
@HostImplemented
enum class LlmApiFormat {
    /** Anthropic Messages API (`x-api-key`, `/v1/messages`, top-level `system` + `messages`). */
    ANTHROPIC_MESSAGES,

    /**
     * OpenAI-compatible Chat Completions (`Authorization: Bearer`,
     * `/v1/chat/completions`, `messages` with a `system` role). Together AI, xAI
     * and Moonshot (Kimi) are wire-compatible with this format.
     */
    OPENAI_CHAT,

    /**
     * Google Gemini generative language API (`?key=` query parameter,
     * `/v1beta/models/{model}:generateContent`, `contents` with `parts`).
     *
     * Added in api 1.0.70. Because [LlmApiFormat] is host-compiled and served
     * parent-first, gate on the `minBossVersion` of the host release that pins
     * 1.0.70 — `minApiVersion: 1.0.70` alone still resolves the host's older copy
     * and fails with `NoSuchFieldError`.
     *
     * See [LlmConfig.baseUrl]: the model is already interpolated into the URL for
     * this format, and the key is never in it.
     */
    GOOGLE_GENERATIVE,

    /**
     * OpenAI Responses API (`Authorization: Bearer`, `/v1/responses`, a single
     * `input` list rather than `messages`, `instructions` for the system prompt).
     *
     * Not interchangeable with [OPENAI_CHAT] despite the shared credential style:
     * the request and reply shapes differ, and a Chat Completions body posted to
     * `/v1/responses` is rejected. This is the format Codex speaks, and what
     * organisation-run gateways in front of Codex serve.
     *
     * Added in api 1.0.74. Same gate as [GOOGLE_GENERATIVE], for the same reason:
     * this enum is host-compiled and served parent-first, so `minApiVersion`
     * alone still resolves the host's older copy and fails with
     * `NoSuchFieldError`. Gate on the `minBossVersion` of the host release that
     * pins 1.0.74.
     *
     * Callers that go through `AiGatewayAPI` never touch this constant and need
     * no such gate, which is the point of that interface.
     */
    OPENAI_RESPONSES,
}
