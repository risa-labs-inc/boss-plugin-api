package ai.rever.boss.plugin.api

import kotlinx.coroutines.CoroutineScope

/** A plugin-owned worker in BOSS's shared background process. No window or UI context is supplied. */
@HostImplemented
interface DaemonService {
    /** Start once and return named connection endpoints. Startup failure cancels the service scope. Configuration is persisted: use no credentials. */
    suspend fun start(context: DaemonServiceContext, configuration: Map<String, String>): Map<String, String>

    /** Plugin-defined, versioned request protocol. Never put credentials in endpoint metadata. */
    suspend fun request(method: String, payload: String): String

    /** Drain owned work before returning. The loader remains open until this completes. */
    suspend fun stop()
}

@HostImplemented
interface DaemonServiceContext {
    val scope: CoroutineScope
    val dataDirectory: String
}

/** A connection is a view, not ownership of the worker. Dropping it never stops background work. */
@HostImplemented
interface DaemonServiceConnection {
    val endpoints: Map<String, String>
    suspend fun request(method: String, payload: String = ""): String
    suspend fun stop()
}

/** Bound by the host to the loaded plugin's identity and exact JAR. */
@HostImplemented
interface DaemonServiceProvider {
    /**
     * Reconnect to an existing service or start a public no-arg [DaemonService] implementation.
     * IDs belong to this plugin. Repeated calls retain the running worker and its original code.
     * The host snapshots the JAR, so UI reload and updates cannot invalidate running workers.
     * Changes to the worker's own protocol need explicit version negotiation; no automatic restart
     * may discard running tasks. UI-dependent approvals must pause while no UI is connected.
     */
    suspend fun connect(
        serviceId: String,
        entryPoint: String,
        configuration: Map<String, String> = emptyMap(),
    ): DaemonServiceConnection
}
