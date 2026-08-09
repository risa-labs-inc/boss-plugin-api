package ai.rever.boss.plugin.api

/**
 * Read-only access to the AI provider configuration the user set up in
 * Settings → AI Providers. Lets plugins reuse the configured API keys and selected
 * model instead of managing their own credentials.
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
     * The active LLM configuration — the provider currently selected in
     * Settings → AI Providers, populated with its API key, endpoint, and model.
     *
     * Returns null when no provider is selected or the selected provider has no
     * API key configured (i.e. nothing usable). Callers should hide AI
     * affordances when this is null.
     */
    fun activeConfig(): LlmConfig?

    /**
     * All providers that currently have an API key configured, in display order.
     * Useful for building a picker; most plugins only need [activeConfig].
     */
    fun configuredProviders(): List<LlmConfig> = emptyList()
}

/**
 * A resolved LLM configuration: which provider, its credential and endpoint, plus
 * the generation defaults the user picked — everything needed to make a request.
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
    /** API key for the provider (never blank when returned from [LlmProvider.activeConfig]). */
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
    /** Selected model id, e.g. "claude-3-5-sonnet-v2" or "gpt-4o". */
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
