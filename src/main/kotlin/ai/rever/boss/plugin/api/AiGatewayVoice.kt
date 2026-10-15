package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow

/**
 * Speech-to-text and text-to-speech through the gateway. Reach it with `gateway as? AiGatewayVoiceAPI`;
 * advertised by [CAPABILITY_VOICE] in [AiGatewayAPI.capabilities]. A new non-host type: gate on
 * `minApiVersion` 1.0.101. Provider and model selection use the same [AiRequest] extras keys as chat
 * ([AiRequest.EXTRAS_KEY_PROVIDER_ID], [AiRequest.EXTRAS_KEY_MODEL_OVERRIDE]); a call never crosses providers.
 */
interface AiGatewayVoiceAPI {
    /** Never throws except `CancellationException`; failures are `Result.failure(AiVoiceException)`. */
    suspend fun transcribe(request: AiTranscriptionRequest): Result<AiTranscription>

    /**
     * Cold flow, never throws. Emits [AiSpeechChunk.Audio] chunks (mp3) then exactly one terminal
     * [AiSpeechChunk.Completed] or [AiSpeechChunk.Failed]. Cancelling the collector aborts the HTTP request.
     */
    fun speak(request: AiSpeechRequest): Flow<AiSpeechChunk>

    /**
     * The talk-time allowance of the model named by [AiRequest.EXTRAS_KEY_MODEL_OVERRIDE] in [extras], read from
     * the provider's model catalog (smallest remaining period). `success(null)` when the model reports no
     * allowance. Never throws except `CancellationException`; failures are `Result.failure(AiVoiceException)`.
     * Use it to show quota before the first call; later calls report it on [AiTranscription.quota] and
     * [AiSpeechChunk.Completed.quota]. Default: `success(null)`.
     */
    suspend fun quota(extras: Map<String, String> = emptyMap()): Result<AiVoiceQuota?> = Result.success(null)

    /**
     * Starts a live voice call: the provider creates a WebRTC room and dispatches its agent worker into it,
     * and returns what the client needs to join. The worker calls [AiLiveCallRequest.agentUrl] with
     * [AiLiveCallRequest.agentToken] for each reply; talk time is metered by the provider's audio calls.
     * Never throws except `CancellationException`; failures are `Result.failure(AiVoiceException)`, with
     * [AiVoiceError.ALLOWANCE_EXCEEDED] (and a quota) when under a minute is left.
     * Default: `Result.failure(AiVoiceException(unsupported_provider))`.
     */
    suspend fun startLiveCall(request: AiLiveCallRequest): Result<AiLiveCall> =
        Result.failure(AiVoiceException(AiVoiceError(AiVoiceError.UNSUPPORTED_PROVIDER, "This provider does not support live calls.")))

    companion object {
        /** [AiGatewayAPI.capabilities] entry: the gateway implements [AiGatewayVoiceAPI]. */
        const val CAPABILITY_VOICE: String = "voice"
    }
}

/** [format] is one of wav, mp3, webm, ogg, flac, m4a. [extras] keys: providerId, modelOverride. toString omits audio. */
class AiTranscriptionRequest(
    val audio: ByteArray,
    val format: String,
    val language: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60_000,
) {
    override fun toString(): String = "AiTranscriptionRequest(format=$format, audio.size=${audio.size}, language=$language)"
}

/**
 * [billedSeconds] is the talk time charged to the allowance, when the provider reports it. [quota] is the
 * allowance left after this call, when the provider reports it.
 */
class AiTranscription(
    val text: String,
    val billedSeconds: Int? = null,
    val quota: AiVoiceQuota? = null,
) {
    override fun toString(): String = "AiTranscription(text.length=${text.length}, billedSeconds=$billedSeconds, quota=$quota)"
}

/**
 * [agentUrl] is the https endpoint the call's worker posts each turn to, authenticated by [agentToken] (an opaque
 * per-call secret of 32 to 256 characters). [maxSeconds] is 60 to 3600 (default): a call ends at one hour or when the caller hangs up. [extras] keys: providerId. toString hides
 * the token.
 */
class AiLiveCallRequest(
    val agentUrl: String,
    val agentToken: String,
    val maxSeconds: Int = 3600,
    val extras: Map<String, String> = emptyMap(),
) {
    override fun toString(): String = "AiLiveCallRequest(agentUrl=$agentUrl, maxSeconds=$maxSeconds)"
}

/**
 * A started live call. [participantToken] is the client's room credential and expires at [expiresAt] (ISO-8601);
 * [quota] is the talk time left, when reported. toString hides the token.
 */
class AiLiveCall(
    val serverUrl: String,
    val participantToken: String,
    val room: String,
    val expiresAt: String,
    val quota: AiVoiceQuota? = null,
) {
    override fun toString(): String = "AiLiveCall(serverUrl=$serverUrl, room=$room, expiresAt=$expiresAt, quota=$quota)"
}

/** [voice] overrides the model's default voice. toString omits the text. */
class AiSpeechRequest(
    val text: String,
    val voice: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 60_000,
) {
    override fun toString(): String = "AiSpeechRequest(text.length=${text.length}, voice=$voice)"
}

/** Open hierarchy, not sealed: consumers ignore unknown nonterminal chunk types. */
abstract class AiSpeechChunk {
    /** A run of mp3 bytes, in order. */
    class Audio(val bytes: ByteArray) : AiSpeechChunk() {
        override fun toString(): String = "Audio(bytes.size=${bytes.size})"
    }

    /**
     * Terminal success. [billedSeconds] comes from the provider's accounting, when reported. [quota] is the
     * allowance left after this request, when reported.
     */
    class Completed(val billedSeconds: Int? = null, val quota: AiVoiceQuota? = null) : AiSpeechChunk() {
        override fun toString(): String = "Completed(billedSeconds=$billedSeconds, quota=$quota)"
    }

    /** Terminal failure. */
    class Failed(val error: AiVoiceError) : AiSpeechChunk() {
        override fun toString(): String = "Failed(error=$error)"
    }
}

/**
 * [code] is one of [AiVoiceError] constants; treat it as an open set. [message] is short and safe to show.
 * [quota] is set on [ALLOWANCE_EXCEEDED].
 */
class AiVoiceError(
    val code: String,
    val message: String,
    val httpStatus: Int? = null,
    val retryAfterMs: Long? = null,
    val quota: AiVoiceQuota? = null,
) {
    override fun toString(): String = "AiVoiceError(code=$code, httpStatus=$httpStatus, quota=$quota)"

    companion object {
        const val ALLOWANCE_EXCEEDED: String = "allowance_exceeded"
        const val BUSY: String = "busy"
        const val FORBIDDEN: String = "forbidden"
        const val WRONG_MODEL_KIND: String = "wrong_model_kind"
        const val UNSUPPORTED_PROVIDER: String = "unsupported_provider"
        const val UPSTREAM_ERROR: String = "upstream_error"
        const val NETWORK: String = "network"
        const val TIMEOUT: String = "timeout"
        const val INVALID_INPUT: String = "invalid_input"
    }
}

/**
 * Talk-time allowance in seconds. [resetsAt] is ISO-8601. [limitSeconds] and [usedSeconds] are null when the
 * source reports only what remains (per-call responses).
 */
class AiVoiceQuota(
    val limitSeconds: Long?,
    val usedSeconds: Long?,
    val remainingSeconds: Long,
    val resetsAt: String,
) {
    override fun toString(): String =
        "AiVoiceQuota(limit=$limitSeconds, used=$usedSeconds, remaining=$remainingSeconds, resetsAt=$resetsAt)"
}

class AiVoiceException(val error: AiVoiceError) : Exception(error.message)
