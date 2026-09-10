package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs

class AiGatewayStreamingTest {
    @Test
    fun `default streaming companion refuses unsupported tool turns explicitly`() = runBlocking<Unit> {
        val gateway = object : AiGatewayStreamingAPI {}
        val events = gateway.streamStep(
            AiRequest(messages = listOf(AiMessage.user("look around"))),
            listOf(AiToolSpec("browse", "Read a page")),
        ).toList()
        assertIs<UnsupportedOperationException>(assertIs<AiStepEvent.Failed>(events.single()).error)
    }
}
