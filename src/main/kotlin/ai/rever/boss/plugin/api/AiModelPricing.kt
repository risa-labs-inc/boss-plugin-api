package ai.rever.boss.plugin.api

/**
 * A provider-published USD rate card for one exact model.
 *
 * Rates are US dollars per one million input or output tokens. This deliberately models only
 * the two counts [AiUsage] can report. A route whose current catalog rate card contains a non-zero
 * request, image, cache, reasoning, tool or other charge must not return a card through
 * [LlmModelPricingAPI] or [AiGatewayPricingAPI] until that charge is represented; returning null
 * is more accurate than an incomplete dollar estimate.
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
    /** Stable provider id: key catalogs on [LlmConfig.providerId] verbatim, case-sensitively. */
    val providerId: String,
    /** Exact [LlmConfig.modelId] the rate applies to, case-sensitively; aliases are not inferred. */
    val modelId: String,
    /** US dollars per one million [AiUsage.inputTokens]. */
    val inputUsdPer1M: Double,
    /** US dollars per one million [AiUsage.outputTokens]. */
    val outputUsdPer1M: Double,
    /**
     * Open provenance label, e.g. `provider-catalog`; lowercase kebab-case, never credentials.
     * The format check rejects URLs and whitespace; producers must still exclude secrets.
     */
    val source: String,
    /**
     * When the catalog was fetched on the local wall clock, Unix epoch milliseconds.
     * Inclusive lower bound for [isValidAt]; a clock rollback before this observation makes the
     * card unavailable until the clock catches up. Do not substitute a remote server timestamp.
     */
    val fetchedAtEpochMs: Long,
    /** Inclusive last instant at which a new turn may adopt this card, Unix epoch milliseconds. */
    val validUntilEpochMs: Long,
    /**
     * Forward-compatible metadata. This two-count API does not interpret any extra rate keys.
     * Reserved future USD-per-million-token keys are `cached-input-usd-per-1m` and
     * `cache-write-usd-per-1m`, encoded as finite, non-negative decimal strings. Their presence
     * does not make a card complete for this API: non-zero cache charges still require null.
     * Only a future paired usage/costing API may interpret these keys; consumers of that API
     * must ignore keys it does not define. Producers may prepare such cards for that future
     * interface, but must not return non-zero extra-charge cards through either current lookup.
     * These reserved values are opaque here and are not validated; the future API must validate
     * their format before costing. Never put credentials in this map.
     *
     * Direct constructors and `copy` retain this map: callers must supply immutable metadata and
     * never mutate its backing map. [orNull] copies producer-owned metadata into a snapshot.
     */
    val extras: Map<String, String> = emptyMap(),
) {
    init {
        require(providerId.isValidIdentifier()) { "providerId must not be blank, padded or contain control characters" }
        require(modelId.isValidIdentifier()) { "modelId must not be blank, padded or contain control characters" }
        require(inputUsdPer1M.isFinite() && inputUsdPer1M.compareTo(0.0) >= 0) {
            "inputUsdPer1M must be finite and non-negative (negative zero is not canonical)"
        }
        require(outputUsdPer1M.isFinite() && outputUsdPer1M.compareTo(0.0) >= 0) {
            "outputUsdPer1M must be finite and non-negative (negative zero is not canonical)"
        }
        require(source.matches(SOURCE_FORMAT)) {
            "source must be lowercase kebab-case"
        }
        require(fetchedAtEpochMs >= 0L) { "fetchedAtEpochMs must be non-negative" }
        require(validUntilEpochMs >= fetchedAtEpochMs) {
            "validUntilEpochMs must not precede fetchedAtEpochMs"
        }
    }

    /**
     * Inclusive local observation window; equal timestamps describe a valid single instant.
     * A clock rollback before [fetchedAtEpochMs] deliberately returns false until it catches up.
     */
    fun isValidAt(nowEpochMs: Long): Boolean = nowEpochMs in fetchedAtEpochMs..validUntilEpochMs

    companion object {
        private val SOURCE_FORMAT = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")

        /**
         * [source] value for rates read directly from a provider's model catalog.
         * Inlined into consumers; this literal must never change and needs no runtime field lookup.
         */
        const val SOURCE_PROVIDER_CATALOG: String = "provider-catalog"

        /**
         * Validates a catalog row without throwing on invalid fields. Does not check freshness.
         * Normalizes signed zero to canonical positive zero so an explicit free rate survives.
         */
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
            AiModelPricing(
                providerId = providerId,
                modelId = modelId,
                inputUsdPer1M = inputUsdPer1M + 0.0,
                outputUsdPer1M = outputUsdPer1M + 0.0,
                source = source,
                fetchedAtEpochMs = fetchedAtEpochMs,
                validUntilEpochMs = validUntilEpochMs,
                extras = extras.toMap(),
            )
        } catch (_: IllegalArgumentException) {
            null
        }

        private fun String.isValidIdentifier(): Boolean =
            isNotBlank() && this == trim() && none(Char::isISOControl)
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
 * type must declare `minApiVersion: 1.0.92` or newer and honour
 * the `minBossVersion` host-relay gate on [PluginContext.llmProvider]. A producer implementing
 * this type must also declare `minApiVersion: 1.0.92` or newer.
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
 * overrides and local CLI selection, then returns that route's current complete rate card. Callers
 * should pass a route-only `AiRequest(extras = routeExtras)` with blank system text and no messages,
 * then send the full request for inference with those exact extras. Implementations must not log or
 * retain request content during pricing lookup. Null means the route is unpriced, expired,
 * unsupported, or unavailable; it never means free.
 *
 * This does not reserve spend or promise that a call cannot cross a cap. It supports an estimated
 * USD budget checked between model calls: a caller prices reported [AiUsage] against the returned
 * snapshot, and an already-running call may finish above the cap. Before applying the snapshot to
 * [AiReply.modelId] or [AiTurn.modelId], the caller must compare that terminal model id with
 * [AiModelPricing.modelId] exactly. A mismatch means the completed call is unpriced; provider-side
 * fallback must not be charged at the requested model's rate. Replies do not identify the
 * provider. Missing or partially reported [AiUsage] must not be treated as free. A caller may use
 * local token estimates for an absent or non-positive dimension contradicted by observable
 * non-empty input or output; if it cannot estimate that dimension, the completed call is unpriced
 * and an enforcing caller must stop before another model call. A budget caller must
 * first confirm [AiGatewayAPI.CAPABILITY_PROVIDER_OVERRIDE] from
 * [AiGatewayAPI.capabilities] on the same live gateway instance used for lookup and inference;
 * do not cache that decision across gateway replacement, unload or downgrade. It must then pin
 * [AiRequest.EXTRAS_KEY_PROVIDER_ID] to [AiModelPricing.providerId] and
 * [AiRequest.EXTRAS_KEY_MODEL_OVERRIDE] to [AiModelPricing.modelId], and use those same extras for
 * inference. A gateway advertising the capability keeps those explicit route identifiers fixed for
 * the call; if that route becomes unavailable, the call fails instead of crossing providers.
 * Without the capability and an explicit route, the caller cannot establish provider identity and
 * the completed call remains unpriced. A blank terminal id is also unpriced.
 * Once an in-flight call has completed unpriced, a caller enforcing a dollar budget must stop
 * before another model call; silently skipping that spend would make the cap ineffective.
 *
 * Resolve [AiGatewayAPI] lazily through [PluginContext.getPluginAPI], then cast it with
 * `as? AiGatewayPricingAPI`; plugin registration order is not guaranteed. A consumer naming this
 * type must declare `minApiVersion: 1.0.92` or newer. Lookups are
 * in-memory, synchronous and non-throwing: they must not perform network work, and invalid
 * route/catalog data must produce null.
 */
interface AiGatewayPricingAPI {
    fun modelPricing(request: AiRequest): AiModelPricing?
}
