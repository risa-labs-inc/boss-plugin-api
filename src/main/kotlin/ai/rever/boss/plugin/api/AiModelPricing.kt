package ai.rever.boss.plugin.api

/**
 * A provider-published USD rate card for one exact model.
 *
 * Rates are US dollars per one million input or output tokens. This deliberately models only
 * the two counts [AiUsage] can report. A route whose current catalog rate card contains a non-zero
 * request, image, cache, reasoning, tool or other charge must not publish this type until that
 * charge is represented; returning null is more accurate than an incomplete dollar estimate.
 *
 * [fetchedAtEpochMs] and [validUntilEpochMs] bound the catalog observation this came from. A
 * provider must stop returning the card after it expires. A caller may keep a card obtained before
 * expiry for the turn already in progress, so every model call in that turn uses one rate snapshot.
 *
 * This is a new data class whose constructor is frozen from its first release. Future rate
 * categories belong in [extras], paired with a new usage/costing API that can interpret them.
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
    /** Open provenance label, e.g. `provider-catalog`; never a credential-bearing URL. */
    val source: String,
    /** When the provider catalog containing these rates was fetched, Unix epoch milliseconds. */
    val fetchedAtEpochMs: Long,
    /** Inclusive last instant at which a new turn may adopt this card, Unix epoch milliseconds. */
    val validUntilEpochMs: Long,
    /** Forward-compatible metadata. Unknown keys must be ignored and must not affect costing. */
    val extras: Map<String, String> = emptyMap(),
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(modelId.isNotBlank()) { "modelId must not be blank" }
        require(inputUsdPer1M.isFinite() && inputUsdPer1M >= 0.0) {
            "inputUsdPer1M must be finite and non-negative"
        }
        require(outputUsdPer1M.isFinite() && outputUsdPer1M >= 0.0) {
            "outputUsdPer1M must be finite and non-negative"
        }
        require(source.isNotBlank()) { "source must not be blank" }
        require(fetchedAtEpochMs >= 0L) { "fetchedAtEpochMs must be non-negative" }
        require(validUntilEpochMs >= fetchedAtEpochMs) {
            "validUntilEpochMs must not precede fetchedAtEpochMs"
        }
    }

    companion object {
        /** [source] value for rates read directly from a provider's model catalog. */
        const val SOURCE_PROVIDER_CATALOG: String = "provider-catalog"
    }
}

/**
 * Optional companion supplied by the plugin that owns the configured provider model catalog.
 *
 * A null result means no current complete rate card exists for this exact provider/model pair.
 * It never means free. Zero rates are returned only when the provider catalog explicitly published
 * zero. Lookups are in-memory, synchronous and non-throwing so callers can snapshot pricing before
 * starting a turn without performing network work. Implementations must validate catalog data and
 * convert any [AiModelPricing] construction failure to null.
 *
 * Resolve this companion lazily from the configured [PluginContext.llmProvider] with
 * `as? LlmModelPricingAPI`; plugin registration order is not guaranteed. A consumer naming this
 * type must declare the API release that introduced it as its `minApiVersion`.
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
 * overrides and local CLI selection, then returns that route's current complete rate card. Null
 * means the route is unpriced, expired, unsupported, or unavailable; it never means free.
 *
 * This does not reserve spend or promise that a call cannot cross a cap. It supports an estimated
 * USD budget checked between model calls: a caller prices reported [AiUsage] against the returned
 * snapshot, and an already-running call may finish above the cap. Before applying the snapshot to
 * [AiReply.modelId] or [AiTurn.modelId], the caller must compare that terminal model id with
 * [AiModelPricing.modelId] exactly. A mismatch means the completed call is unpriced; provider-side
 * fallback must not be charged at the requested model's rate.
 */
interface AiGatewayPricingAPI {
    fun modelPricing(request: AiRequest): AiModelPricing?
}
