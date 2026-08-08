package ai.rever.boss.plugin.api

/**
 * Exchanges the signed-in BOSS session for a short-lived downstream credential,
 * without the plugin ever seeing the session itself.
 *
 * Some providers are reached with a credential nobody types in: the user is
 * already signed in to BOSS, and an organisation-run gateway will mint a
 * short-lived, model-scoped key for that identity. RISA's Codex GLM gateway works
 * this way. Such a provider has no API key to store, and its credential expires
 * in hours rather than never.
 *
 * The exchange has to happen host-side. Nothing on [PluginContext] exposes the
 * Supabase access token, deliberately: [AuthDataProvider] gives identity only and
 * [SupabaseDataProvider] proxies queries with the host attaching auth. This
 * interface keeps that boundary - the plugin gets the *downstream* credential and
 * never the session that bought it.
 *
 * **A broker is named by id, never by URL.** An `exchange(url)` signature would
 * hand every installed plugin a way to post the user's session token to a host of
 * its choosing, which is a session-exfiltration primitive with a friendly name.
 * The host owns the id to endpoint mapping; a plugin can only ask for a broker the
 * host already knows.
 *
 * Null like every other provider on [PluginContext], and empty
 * [availableBrokers] on a host that has no brokers configured. A plugin must
 * treat "no broker" as ordinary and hide the provider rather than failing.
 */
@HostImplemented
interface BrokeredCredentialProvider {

    /**
     * Brokers this host can exchange with, in no particular order.
     *
     * Empty when none is configured, which is the common case. A provider whose
     * broker is absent here cannot be used and should not be offered.
     */
    fun availableBrokers(): List<BrokerInfo> = emptyList()

    /**
     * Exchange the current session for a credential from [brokerId].
     *
     * Fails, rather than throwing, when the user is not signed in, the broker is
     * unknown to this host, the account is not entitled, or the broker is
     * unreachable. The failure message is written for the user, so it can be shown
     * as-is.
     *
     * Callers should honour [BrokeredCredential.refreshAfterSeconds] and not call
     * this per request: the credential is a real resource at the other end, and a
     * broker may bound how often it will mint one.
     */
    suspend fun exchange(brokerId: String): Result<BrokeredCredential>
}

/** A broker the host knows how to reach. */
@HostImplemented
data class BrokerInfo(
    /** Stable id passed to [BrokeredCredentialProvider.exchange]. */
    val id: String,
    /** Human-readable name for the UI, e.g. "RISA Codex GLM". */
    val displayName: String,
    /**
     * Whether this host currently has what it needs to use the broker, which is
     * normally "a user is signed in". False means [BrokeredCredentialProvider.exchange]
     * would fail now, so the UI can explain instead of offering a dead action.
     */
    val available: Boolean = true,
)

/**
 * A short-lived credential from a broker.
 *
 * Never persist it. It expires, it is cheap to re-obtain, and writing it to disk
 * turns a credential that self-heals into one that leaks. Hold it in memory until
 * [refreshAfterSeconds] elapses and then ask again.
 */
@HostImplemented
data class BrokeredCredential(
    /** The credential to send downstream, e.g. a bearer token. */
    val token: String,
    /**
     * How long this credential may be reused, in seconds, as the broker reported
     * it. A reused credential has less life left than a fresh one, so this is the
     * remaining window and not a constant.
     */
    val refreshAfterSeconds: Long,
    /**
     * When the credential stops working, as the broker stated it, or null when it
     * did not say. Informational: [refreshAfterSeconds] is what to act on, because
     * a broker may want it renewed well before it expires.
     */
    val expiresAt: String? = null,
)
