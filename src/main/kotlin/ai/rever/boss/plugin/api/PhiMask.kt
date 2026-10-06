package ai.rever.boss.plugin.api

/**
 * Local PHI masking served by the phi-mask plugin (phiscrub on loopback). Resolve per call:
 * `getPluginAPI(PhiMaskAPI::class.java)`; null means not installed.
 *
 * Plugin-to-plugin, not [HostImplemented]: gate on `minApiVersion` 1.0.98.
 *
 * Fails closed: every failure is `Result.failure` with a [PhiMaskException], never the input
 * returned as masked output. Text never leaves 127.0.0.1. `CancellationException` is rethrown,
 * never wrapped.
 */
interface PhiMaskAPI {
    /** Never throws except `CancellationException`; does not start the sidecar. */
    suspend fun status(): PhiMaskStatus

    /** Irreversible masking. Starts the sidecar on first use. */
    suspend fun redact(text: String, profile: PhiMaskProfile = PhiMaskProfile.FAST): Result<PhiMaskResult>

    /** Results in input order; fails as a whole, never partially. */
    suspend fun redactBatch(texts: List<String>, profile: PhiMaskProfile = PhiMaskProfile.FAST): Result<List<PhiMaskResult>>

    /** Reversible masking. Only this session can restore the ids it issued. */
    suspend fun openSession(): Result<PhiMaskSession>
}

/**
 * Holds the original values in the sidecar's memory only. Invalid after [close], after the
 * sidecar restarts, and when the phi-mask plugin unloads: calls then fail with
 * [PhiMaskException.SESSION_CLOSED]. Callers close it when done.
 */
interface PhiMaskSession {
    /** Replaces PHI with ids of the form `[[PHI:<16 hex>]]`, stable within this session. */
    suspend fun tokenize(text: String, profile: PhiMaskProfile = PhiMaskProfile.FAST): Result<PhiMaskResult>

    suspend fun tokenizeBatch(texts: List<String>, profile: PhiMaskProfile = PhiMaskProfile.FAST): Result<List<PhiMaskResult>>

    /** Restores ids this session issued; any other id is left as is. */
    suspend fun restore(text: String): Result<String>

    /** Idempotent; never throws except `CancellationException`. */
    suspend fun close()
}

/** Treat as an open set: a newer plugin may serve profiles this enum does not name yet. */
enum class PhiMaskProfile(val wireName: String) {
    /** Deterministic recognizers only; always installed. */
    FAST("fast"),

    /** BALANCED on ONNX Runtime; optional install. */
    REALTIME("realtime"),

    /** Deterministic plus the ML model; optional install. */
    BALANCED("balanced"),

    /** Every recognizer, highest recall; optional install. */
    STRICT("strict"),
    ;

    companion object {
        fun fromWireName(name: String): PhiMaskProfile? = entries.firstOrNull { it.wireName == name }
    }
}

/**
 * Never add a constructor parameter (it moves the constructor and `copy$default`); extend through
 * [extras]. [toString] omits [text] and [extras] values.
 */
data class PhiMaskResult(
    /** The masked text. Never the input unless nothing was detected. */
    val text: String,
    val detections: List<PhiDetection>,
    /** Unknown keys are ignored. Never PHI or credentials. */
    val extras: Map<String, String> = emptyMap(),
) {
    override fun toString(): String =
        "PhiMaskResult(text.length=${text.length}, detections=${detections.size}, extras.keys=${extras.keys})"
}

/**
 * A span in the ORIGINAL text: [start] inclusive, [end] exclusive, UTF-16 offsets. Never carries
 * the value. Never add a constructor parameter; extend through [extras].
 */
data class PhiDetection(
    val start: Int,
    val end: Int,
    /** phiscrub's type name, such as `NAME` or `MRN`. Open set. */
    val type: String,
    val confidence: Double,
    /** Unknown keys are ignored. Never PHI or credentials. */
    val extras: Map<String, String> = emptyMap(),
)

/** Never add a constructor parameter; extend through [extras]. */
data class PhiMaskStatus(
    val state: PhiMaskState,
    /** Empty unless [state] is [PhiMaskState.READY]. */
    val availableProfiles: List<PhiMaskProfile>,
    /** The phiscrub version, once known. */
    val version: String? = null,
    /** Fixed text for display: progress or why it failed. Never PHI or credentials. */
    val detail: String? = null,
    /** Unknown keys are ignored. Never PHI or credentials. */
    val extras: Map<String, String> = emptyMap(),
)

enum class PhiMaskState { NOT_STARTED, INSTALLING, STARTING, READY, FAILED }

/**
 * [code] is one of the constants below; treat as an open set. [message] is fixed text, safe to
 * display and log: it never contains input text, sidecar response bodies or credentials. No cause
 * is attached, so nothing upstream can leak through it.
 */
class PhiMaskException(
    val code: String,
    message: String,
) : Exception(message) {
    companion object {
        /** The sidecar is not installed, not running, or did not answer. */
        const val UNAVAILABLE = "UNAVAILABLE"

        /** The requested profile is not installed. */
        const val PROFILE_UNAVAILABLE = "PROFILE_UNAVAILABLE"

        /** The input or the response exceeds a size limit. */
        const val TOO_LARGE = "TOO_LARGE"

        /** The session was closed, or the sidecar restarted since it was opened. */
        const val SESSION_CLOSED = "SESSION_CLOSED"

        /** Anything else. */
        const val FAILED = "FAILED"
    }
}
