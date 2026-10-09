package ai.rever.boss.plugin.api

import kotlinx.coroutines.CoroutineScope

/** A plugin-owned worker in BOSS's shared background process. No window or UI context is supplied. */
@HostImplemented
interface DaemonService {
    /**
     * Start once and return non-secret protocol/endpoint metadata. Startup failure cancels the
     * service scope. Configuration is persisted: use no credentials. Return transport auth tokens
     * through [request] instead of metadata.
     */
    suspend fun start(context: DaemonServiceContext, configuration: Map<String, String>): Map<String, String>

    /** Plugin-defined, versioned control protocol. The host does not persist or log payloads. */
    suspend fun request(method: String, payload: String): String

    /** Drain owned work before returning. The loader remains open until this completes. */
    suspend fun stop()
}

@HostImplemented
interface DaemonServiceContext {
    /** Host-owned SupervisorJob on Dispatchers.IO; one failed child does not cancel siblings. */
    val scope: CoroutineScope
    /** Existing owner-private directory, stable across UI reload/update and explicit service stop. */
    val dataDirectory: String
}

/** A connection is a view, not ownership of the worker. Dropping it never stops background work. */
@HostImplemented
interface DaemonServiceConnection {
    /** Includes host-owned boss.service.instanceId: stable on reconnect, new after restart. */
    val endpoints: Map<String, String>
    /** Transport/rejection failures throw Exception; cancellation propagates. Never log payloads. */
    suspend fun request(method: String, payload: String = ""): String
    suspend fun stop()
}

/** Bound by the host to the loaded plugin's identity and exact JAR. */
@HostImplemented
interface DaemonServiceProvider {
    /**
     * Reconnect to an existing service or start a public no-arg [DaemonService] implementation.
     * IDs belong to this plugin. Repeated calls retain the original entry point, configuration and
     * code until explicit stop/start. Entry points must resolve from the plugin's own JAR.
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
