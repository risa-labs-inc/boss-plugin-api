package ai.rever.boss.plugin.api

/**
 * Host capability for driving isolated browser sessions, exposed to plugins.
 *
 * This is the piece that must live in the host: it's backed by the process-wide
 * browser engine. A plugin obtains an isolated/headless browser (optionally with
 * auth pre-seeded), then drives it via [RpaBrowserSession]. All engine-specific
 * concerns (profile creation, cookie domain rules, headless lifecycle, disk
 * eviction) stay behind this interface, so plugin code never touches JxBrowser.
 *
 * Obtained via [PluginContext.rpaBrowserProvider]; null when unavailable (e.g.
 * the browser engine isn't licensed, or on an unsupported platform). Callers
 * must handle null gracefully.
 */
interface RpaBrowserProvider {
    /**
     * Open an isolated browser session: create/resolve the profile, seed
     * cookies/headers, create the browser. Returns null if unavailable.
     */
    suspend fun openSession(spec: RpaBrowserSpec): RpaBrowserSession?

    /** Create/refresh a persistent named profile and seed its auth. */
    suspend fun seedNamedProfile(profileId: String, auth: RpaAuthSpec?): RpaProfileInfo

    /** Delete a persistent named profile. */
    fun deleteProfile(profileId: String): Boolean

    /** List persistent named profiles. */
    fun listProfiles(): List<RpaProfileInfo>
}

/** What kind of browser session to open. */
data class RpaBrowserSpec(
    val profile: RpaProfileChoice,
    val auth: RpaAuthSpec?,
    /** Headless (offscreen, isolated). Visible mode is host-defined and may ignore isolation. */
    val headless: Boolean,
)

/**
 * A live, isolated browser the plugin drives. Navigation and JS execution are
 * performed by the host (where the engine lives); the plugin supplies the
 * automation logic.
 */
interface RpaBrowserSession {
    /** Resolved profile id (ephemeral uuid or the named id). */
    val profileId: String

    /** Navigate and wait for load. */
    suspend fun navigate(url: String)

    /** Execute JS in the page, returning its value (or null). */
    suspend fun executeJavaScript(script: String): Any?

    /** Close the browser and release/destroy the profile if ephemeral. */
    suspend fun close()
}

// ---------------------------------------------------------------------------
// Profile / auth value types — transport-independent, shared by the provider.
// ---------------------------------------------------------------------------

sealed class RpaProfileChoice {
    /** Fresh isolated profile per session, destroyed on close. */
    object Ephemeral : RpaProfileChoice()
    /**
     * Persistent profile reused across sessions.
     *
     * Note: seeded auth (cookies in particular) is written to the browser
     * profile on disk by the engine, unencrypted at the Chromium layer — i.e.
     * credentials live at rest for the profile's lifetime. Use [Ephemeral] for
     * one-off runs, and delete named profiles you no longer need.
     */
    data class Named(val profileId: String) : RpaProfileChoice()
}

/**
 * Auth/state to seed into a profile before a session starts.
 *
 * Scoping caveat: [cookies] are domain-scoped (each [RpaCookieSpec] names its
 * domain), but [headers] and [basicAuth] are injected on **every** request the
 * profile makes — they are NOT restricted to a target host. If the run navigates
 * or issues cross-origin sub-requests, those headers (incl. the `Authorization`
 * derived from [basicAuth]) are sent to other origins too. Prefer domain-scoped
 * cookies for credentials, and only use [headers]/[basicAuth] for runs you keep
 * pointed at a single trusted origin.
 */
data class RpaAuthSpec(
    val cookies: List<RpaCookieSpec> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val basicAuth: RpaBasicAuth? = null,
)

data class RpaCookieSpec(
    /** Cookie domain (a full URL is also accepted; scheme/port/path are stripped). */
    val url: String,
    val name: String,
    val value: String,
    val path: String = "/",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val expirationEpochMs: Long = 0,
    val sameSite: String = "LAX",
)

data class RpaBasicAuth(val username: String, val password: String)

/** Metadata about a persistent named profile. */
data class RpaProfileInfo(
    val profileId: String,
    val diskBytes: Long,
    val lastUsedMs: Long,
    val exists: Boolean,
)
