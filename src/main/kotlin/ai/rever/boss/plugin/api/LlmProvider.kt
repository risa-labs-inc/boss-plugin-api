package ai.rever.boss.plugin.api

/**
 * Read-only access to the LLM provider settings the user configured in the host
 * (Settings → LLM Providers). Lets plugins reuse the host's API keys and selected
 * model instead of managing their own credentials.
 *
 * The implementation is backed by the host's LLM settings store; a provider's key
 * resolves from the environment first, then the value saved in settings.
 *
 * Like every provider on [PluginContext], this may be null — plugins must degrade
 * gracefully (hide AI affordances) when LLM access isn't available.
 */
interface LlmProvider {
    /**
     * The active LLM configuration — the provider currently selected in LLM
     * settings, populated with its API key, endpoint, and selected model.
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
data class LlmConfig(
    /** Stable provider id, e.g. "ANTHROPIC", "OPENAI", "TOGETHER". */
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
 */
enum class LlmApiFormat {
    /** Anthropic Messages API (`x-api-key`, `/v1/messages`, top-level `system` + `messages`). */
    ANTHROPIC_MESSAGES,

    /**
     * OpenAI-compatible Chat Completions (`Authorization: Bearer`,
     * `/v1/chat/completions`, `messages` with a `system` role). Together AI is
     * wire-compatible with this format.
     */
    OPENAI_CHAT
}
