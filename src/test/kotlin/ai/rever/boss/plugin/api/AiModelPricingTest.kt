package ai.rever.boss.plugin.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

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
        for (rate in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.01)) {
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
        assertFailsWith<IllegalArgumentException> { pricing(source = "\t") }
    }

    @Test
    fun `provider and gateway pricing defaults are unavailable`() {
        val owner: LlmProvider = object : LlmProvider, LlmModelPricingAPI {
            override fun activeConfig(): LlmConfig? = null
            override fun modelPricing(providerId: String, modelId: String): AiModelPricing? = null
        }
        val gateway = object : AiGatewayPricingAPI {
            override fun modelPricing(request: AiRequest): AiModelPricing? = null
        }

        val pricingOwner = assertIs<LlmModelPricingAPI>(owner)
        assertNull(pricingOwner.modelPricing("OPENROUTER", "openai/gpt-5"))
        assertNull(gateway.modelPricing(AiRequest()))
    }

    @Test
    fun `copy revalidates rates and extras default empty`() {
        val pricing = pricing()

        assertEquals(emptyMap(), pricing.extras)
        assertFailsWith<IllegalArgumentException> { pricing.copy(inputUsdPer1M = -1.0) }
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
