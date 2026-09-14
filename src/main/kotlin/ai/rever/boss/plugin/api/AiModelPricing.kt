package ai.rever.boss.plugin.api

/**
 * A provider-published USD rate card for one exact model.
 *
 * Rates are US dollars per one million input or output tokens. This deliberately models only
 * the two counts [AiUsage] can report. A route whose current catalog rate card contains a non-zero
 * request, image, cache, reasoning, tool or other charge must not publish this type until that
 * charge is represented; returning null is more accurate than an incomplete dollar estimate.
 * Conservative nulls are expected: the first producer supports only provider/model entries whose
 * catalog exposes both token rates and no unrepresented non-zero charge.
 *
 * [fetchedAtEpochMs] and [validUntilEpochMs] bound the catalog observation this came from. A
 * provider must stop returning the card after it expires. Non-expiring catalogs use
 * [Long.MAX_VALUE] as the expiry. A caller may keep a card obtained before
 * expiry for the turn already in progress, so every model call in that turn uses one rate snapshot.
 *
 * This is a new data class whose constructor is frozen from its first release. Future rate
 * categories belong in [extras], paired with a new usage/costing API that can interpret them. The
 * CLI-session equivalent is [AiCliPricing]; this separate native-route card carries provider,
 * model, provenance and freshness, and spells out USD in its rate names because it directly feeds
 * dollar budgets. Unlike the released [AiCliPricing], this native card rejects malformed rates
 * rather than clamping them: construction and `copy` both validate. Catalog producers should use
 * [orNull] to convert invalid rows to null.
 */
data class AiModelPricing(
    /** Stable provider id, matching [LlmConfig.providerId] exactly and case-sensitively. */
    val providerId: String,
    /** Exact case-sensitive model id the rate applies to; aliases are not inferred. */
    val modelId: String,
    /** US dollars per one million [AiUsage.inputTokens]. */
    val inputUsdPer1M: Double,
    /** US dollars per one million [AiUsage.outputTokens]. */
    val outputUsdPer1M: Double,
    /** Open provenance label, e.g. `provider-catalog`; use lowercase kebab-case, never a URL or credentials. */
    val source: String,
    /** When the provider catalog containing these rates was fetched, Unix epoch milliseconds. */
    val fetchedAtEpochMs: Long,
    /** Inclusive last instant at which a new turn may adopt this card, Unix epoch milliseconds. */
    val validUntilEpochMs: Long,
    /**
     * Forward-compatible metadata. This two-count API does not interpret any extra rate keys.
     * Reserved future USD-per-million-token keys are `cached-input-usd-per-1m` and
     * `cache-write-usd-per-1m`, encoded as finite, non-negative decimal strings. Their presence
     * does not make a card complete for this API: non-zero cache charges still require null.
     * Only a future paired usage/costing API may interpret these keys; consumers of that API
     * must ignore keys it does not define. Never put credentials in this map.
     */
    val extras: Map<String, String> = emptyMap(),
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(modelId.isNotBlank()) { "modelId must not be blank" }
        require(inputUsdPer1M.isFinite() && inputUsdPer1M.compareTo(0.0) >= 0) {
            "inputUsdPer1M must be finite and non-negative (negative zero is not canonical)"
        }
        require(outputUsdPer1M.isFinite() && outputUsdPer1M.compareTo(0.0) >= 0) {
            "outputUsdPer1M must be finite and non-negative (negative zero is not canonical)"
        }
        require(source.isNotBlank()) { "source must not be blank" }
        require(fetchedAtEpochMs >= 0L) { "fetchedAtEpochMs must be non-negative" }
        require(validUntilEpochMs >= fetchedAtEpochMs) {
            "validUntilEpochMs must not precede fetchedAtEpochMs"
        }
    }

    /** Inclusive observation window; equal timestamps describe a valid single instant. */
    fun isValidAt(nowEpochMs: Long): Boolean = nowEpochMs in fetchedAtEpochMs..validUntilEpochMs

    companion object {
        /**
         * [source] value for rates read directly from a provider's model catalog.
         * Inlined into consumers; this literal must never change and needs no runtime field lookup.
         */
        const val SOURCE_PROVIDER_CATALOG: String = "provider-catalog"

        /** Validates a catalog row without throwing on invalid fields. Does not check freshness. */
        fun orNull(
            providerId: String,
            modelId: String,
            inputUsdPer1M: Double,
            outputUsdPer1M: Double,
            source: String,
            fetchedAtEpochMs: Long,
            validUntilEpochMs: Long,
            extras: Map<String, String> = emptyMap(),
        ): AiModelPricing? = try {
            AiModelPricing(providerId, modelId, inputUsdPer1M, outputUsdPer1M, source,
                fetchedAtEpochMs, validUntilEpochMs, extras)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/**
 * Optional companion supplied by the plugin that owns the configured provider model catalog.
 *
 * A null result means no current complete rate card exists for this exact provider/model pair.
 * It never means free. Zero rates are returned only when the provider catalog explicitly published
 * zero. Lookups are in-memory, synchronous and non-throwing so callers can snapshot pricing before
 * starting a turn without performing network work. Implementations must validate catalog data and
 * use [AiModelPricing.orNull] or convert any construction failure to null.
 *
 * Resolve this companion lazily from the configured [PluginContext.llmProvider] with
 * `as? LlmModelPricingAPI`; plugin registration order is not guaranteed. A consumer naming this
 * type must declare `minApiVersion` at least the release introducing these types and honour
 * the `minBossVersion` host-relay gate on [PluginContext.llmProvider]. A producer implementing this type must also declare
 * `minApiVersion` at least the release introducing these types.
 */
interface LlmModelPricingAPI {
    fun modelPricing(
        providerId: String,
        modelId: String,
    ): AiModelPricing?
}

/**
 * Optional pricing companion to [AiGatewayAPI].
 *
 * The gateway resolves the same route [request] would use, including explicit provider/model
 * overrides and local CLI selection, then returns that route's current complete rate card.
 * A route-only [AiRequest] with no messages is valid. Implementations must not log or retain
 * request content during pricing lookup. Null means the route is unpriced, expired, unsupported,
 * or unavailable; it never means free.
 *
 * This does not reserve spend or promise that a call cannot cross a cap. It supports an estimated
 * USD budget checked between model calls: a caller prices reported [AiUsage] against the returned
 * snapshot, and an already-running call may finish above the cap. Before applying the snapshot to
 * [AiReply.modelId] or [AiTurn.modelId], the caller must compare that terminal model id with
 * [AiModelPricing.modelId] exactly. A mismatch means the completed call is unpriced; provider-side
 * fallback must not be charged at the requested model's rate. Replies do not identify the
 * provider: applying or re-looking-up a rate also requires the gateway to guarantee the provider
 * did not change. If that cannot be established, the completed call remains unpriced. A blank
 * terminal id means the provider did not report which model answered and is also unpriced. Once an in-flight call has
 * completed unpriced, a caller enforcing a dollar budget must stop before another model call;
 * silently skipping that spend would make the cap ineffective.
 *
 * Resolve [AiGatewayAPI] lazily through [PluginContext.getPluginAPI], then cast it with
 * `as? AiGatewayPricingAPI`; plugin registration order is not guaranteed. A consumer naming this
 * type must declare `minApiVersion` at least the release introducing these types. Lookups are
 * in-memory, synchronous and non-throwing: they must not perform network work, and invalid
 * route/catalog data must produce null.
 */
interface AiGatewayPricingAPI {
    fun modelPricing(request: AiRequest): AiModelPricing?
}
