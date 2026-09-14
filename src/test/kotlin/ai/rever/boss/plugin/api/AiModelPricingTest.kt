package ai.rever.boss.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiModelPricingTest {
    @Test
    fun `explicit zero rates remain a priced model`() {
        val pricing = pricing(input = 0.0, output = 0.0, fetchedAt = 0, validUntil = 0)

        assertEquals(0.0, pricing.inputUsdPer1M)
        assertEquals(0.0, pricing.outputUsdPer1M)
        assertEquals(pricing.fetchedAtEpochMs, pricing.validUntilEpochMs)
    }

    @Test
    fun `invalid rates and validity windows are rejected`() {
        for (rate in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.0, -0.01)) {
            assertFailsWith<IllegalArgumentException> { pricing(input = rate) }
            assertFailsWith<IllegalArgumentException> { pricing(output = rate) }
        }
        assertFailsWith<IllegalArgumentException> {
            pricing(fetchedAt = 20, validUntil = 19)
        }
        assertFailsWith<IllegalArgumentException> {
            pricing(fetchedAt = -1, validUntil = 20)
        }
        assertFailsWith<IllegalArgumentException> { pricing(providerId = " ") }
        assertFailsWith<IllegalArgumentException> { pricing(modelId = "") }
        assertFailsWith<IllegalArgumentException> { pricing(providerId = " OPENROUTER") }
        assertFailsWith<IllegalArgumentException> { pricing(modelId = "openai/gpt-5\n") }
        assertFailsWith<IllegalArgumentException> { pricing(source = "\t") }
    }

    @Test
    fun `both pricing companions can be discovered on one provider`() {
        val owner: LlmProvider = object : LlmProvider, LlmModelPricingAPI, AiGatewayPricingAPI {
            override fun activeConfig(): LlmConfig? = null
            override fun modelPricing(providerId: String, modelId: String): AiModelPricing? = null

            override fun modelPricing(request: AiRequest): AiModelPricing? = null
        }

        val pricingOwner = assertIs<LlmModelPricingAPI>(owner)
        assertNull(pricingOwner.modelPricing("OPENROUTER", "openai/gpt-5"))
        assertNull(assertIs<AiGatewayPricingAPI>(owner).modelPricing(AiRequest()))

        val plain: Any = object : LlmProvider {
            override fun activeConfig(): LlmConfig? = null
        }
        assertNull(plain as? LlmModelPricingAPI)
        assertNull(plain as? AiGatewayPricingAPI)
    }

    @Test
    fun `copy revalidates rates and extras default empty`() {
        val pricing = pricing()

        assertEquals(emptyMap(), pricing.extras)
        assertFailsWith<IllegalArgumentException> { pricing.copy(inputUsdPer1M = -1.0) }
    }

    @Test
    fun `catalog source literal is stable`() {
        assertEquals("provider-catalog", AiModelPricing.SOURCE_PROVIDER_CATALOG)
    }

    @Test
    fun `equal cards have equal hashes and expiry is inclusive`() {
        val card = pricing()
        assertEquals(card, card.copy())
        assertEquals(card.hashCode(), card.copy().hashCode())
        assertFalse(card.isValidAt(9))
        assertTrue(card.isValidAt(10))
        assertTrue(card.isValidAt(20))
        assertFalse(card.isValidAt(21))
        assertTrue(pricing(fetchedAt = 0, validUntil = 0).isValidAt(0))
        assertTrue(pricing(validUntil = Long.MAX_VALUE).isValidAt(Long.MAX_VALUE))
    }

    @Test
    fun `catalog factory returns null for every invalid field and preserves valid data`() {
        fun row(provider: String = "OPENROUTER", model: String = "openai/gpt-5",
                input: Double = 1.0, output: Double = 2.0, source: String = "provider-catalog",
                fetched: Long = 10, until: Long = 20) =
            AiModelPricing.orNull(provider, model, input, output, source, fetched, until)
        assertEquals(pricing(), row())
        assertNull(row(provider = " "))
        assertNull(row(model = ""))
        assertNull(row(provider = "OPENROUTER "))
        assertNull(row(model = "openai/gpt-5\t"))
        assertNull(row(source = "\t"))
        assertNull(row(fetched = -1))
        assertNull(row(until = 9))
        for (rate in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            assertNull(row(input = rate))
            assertNull(row(output = rate))
        }
        assertEquals(pricing(input = 0.0, output = 0.0, fetchedAt = 0, validUntil = 0),
            row(input = 0.0, output = 0.0, fetched = 0, until = 0))
        val extras = mapOf("future-metadata" to "opaque")
        assertEquals(extras, AiModelPricing.orNull("p", "m", 1.0, 2.0,
            "provider-catalog", 0, 1, extras)?.extras)
    }

    @Test
    fun `catalog factory snapshots producer metadata without interpreting future rates`() {
        val metadata = mutableMapOf("cached-input-usd-per-1m" to "future-format")
        val card = AiModelPricing.orNull("p", "m", 1.0, 2.0, "provider-catalog", 0, 1, metadata)!!
        val originalHash = card.hashCode()
        metadata["cached-input-usd-per-1m"] = "changed"
        metadata["new-key"] = "new-value"
        assertEquals(mapOf("cached-input-usd-per-1m" to "future-format"), card.extras)
        assertEquals(originalHash, card.hashCode())
    }

    @Test
    fun `factory canonicalizes signed zero and accepts non-expiring rows`() {
        val card = AiModelPricing.orNull("p", "m", -0.0, -0.0, "provider-catalog", 0, Long.MAX_VALUE)!!
        assertEquals(0.0, card.inputUsdPer1M)
        assertEquals(0.0, card.outputUsdPer1M)
        assertTrue(card.isValidAt(Long.MAX_VALUE))
    }

    @Test
    fun `source labels reject URLs whitespace and malformed labels`() {
        for (source in listOf("https://example.invalid", "provider catalog", "Provider", "-provider", "provider-")) {
            assertFailsWith<IllegalArgumentException> { pricing(source = source) }
            assertNull(AiModelPricing.orNull("p", "m", 1.0, 2.0, source, 0, 1))
        }
        assertEquals("catalog-v2", pricing(source = "catalog-v2").source)
    }

    private fun pricing(
        input: Double = 1.0,
        output: Double = 2.0,
        fetchedAt: Long = 10,
        validUntil: Long = 20,
        providerId: String = "OPENROUTER",
        modelId: String = "openai/gpt-5",
        source: String = AiModelPricing.SOURCE_PROVIDER_CATALOG,
    ) = AiModelPricing(
        providerId = providerId,
        modelId = modelId,
        inputUsdPer1M = input,
        outputUsdPer1M = output,
        source = source,
        fetchedAtEpochMs = fetchedAt,
        validUntilEpochMs = validUntil,
    )
}
