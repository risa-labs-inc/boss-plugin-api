package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Optional companion to [AiGatewayAPI] for callers that own their tool loop.
 * Resolve the gateway lazily and cast it to this interface. This new type intentionally
 * avoids adding a member to the host-implemented AiGatewayAPI.
 */
interface AiGatewayStreamingAPI {
    /**
     * Stream one model turn, never execute its tools. Pass every completed [rounds], oldest
     * first, exactly as for [AiGatewayAPI.step]. Text/reasoning are genuine provider deltas;
     * implementations must not simulate streaming by splitting a buffered answer.
     *
     * Exactly one Completed or Failed ends a normal collection. Cancellation propagates.
     * Tool calls are actionable only in Completed, after provider completion and argument
     * assembly; consumers must not execute partial calls. A nonstreaming provider may emit
     * Completed alone. Reasoning is only content the provider explicitly exposes, not a
     * request to reveal hidden reasoning, and is separate from the assistant answer.
     */
    fun streamStep(
        request: AiRequest,
        tools: List<AiToolSpec> = emptyList(),
        rounds: List<AiRound> = emptyList(),
    ): Flow<AiStepEvent> = flowOf(
        AiStepEvent.Failed(UnsupportedOperationException("This gateway does not stream tool turns.")),
    )
}

/** Open hierarchy: consumers must ignore unknown nonterminal event types. */
interface AiStepEvent {
    class Text(val text: String) : AiStepEvent
    class Reasoning(val text: String) : AiStepEvent
    class Completed(val turn: AiTurn) : AiStepEvent
    class Failed(val error: Throwable) : AiStepEvent
}
