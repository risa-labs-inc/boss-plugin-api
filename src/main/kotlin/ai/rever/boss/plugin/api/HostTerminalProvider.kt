package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow

/**
 * Provider exposed to plugins for opening terminal sessions on the BOSS host.
 *
 * When the active host is **local**, plugins should call their default PTY
 * spawn path (e.g. construct a `ProcessTtyConnector` directly via `pty4j`).
 * [openSession] returns `null` in that case as a signal.
 *
 * When the active host is **remote**, [openSession] returns a
 * [HostTerminalSession] backed by the server's gRPC `TerminalService`. The
 * plugin wraps it in its terminal library's `TtyConnector` abstraction (the
 * plugin is the one that depends on `bossterm-compose`; this API
 * intentionally does not so that plugin-api-core stays terminal-library-agnostic).
 *
 * Plugin developers never branch on local-vs-remote — they call [openSession]
 * unconditionally; the host decides what to return.
 */
interface HostTerminalProvider {
    /** Diagnostic — true if the currently active host is reached over the network. */
    val isRemote: Boolean

    /** Human-readable identifier of the active host. "local" or "tcp://nas:5800". */
    val displayName: String

    /**
     * Open a session on the active host. Returns null when the active host is
     * local — the plugin should use its own local PTY path. Throws on remote
     * connection errors.
     */
    suspend fun openSession(
        workingDirectory: String? = null,
        cols: Int = 80,
        rows: Int = 24,
        command: List<String>? = null,
        environment: Map<String, String> = emptyMap(),
    ): HostTerminalSession?

    /** List existing sessions on the active host (for reattach UI). Empty when local. */
    suspend fun listSessions(): List<HostTerminalSessionInfo>

    /** Reattach to an existing session by id. Null if gone or local-mode. */
    suspend fun attach(sessionId: String): HostTerminalSession?
}

/** A live PTY session on a host. Outlives a particular consumer — close() to terminate. */
interface HostTerminalSession {
    val id: String
    val info: HostTerminalSessionInfo

    suspend fun sendInput(bytes: ByteArray)

    /**
     * Output bytes from the PTY. Hot stream — emissions start on collection.
     * If the host supports it and [sinceChunkId] is provided, the server
     * replays buffered output since that chunk id (for reattach).
     */
    fun output(sinceChunkId: Long? = null): Flow<HostTerminalChunk>

    suspend fun resize(cols: Int, rows: Int)

    suspend fun close()
}

data class HostTerminalSessionInfo(
    val id: String,
    val workingDirectory: String,
    val command: List<String>,
    val createdAt: Long,
    val isAlive: Boolean,
)

data class HostTerminalChunk(
    /** Monotonic per-session sequence number. Use as `sinceChunkId` to resume. */
    val chunkId: Long,
    val data: ByteArray,
    val timestamp: Long,
    val isExit: Boolean = false,
    val exitCode: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HostTerminalChunk) return false
        return chunkId == other.chunkId && data.contentEquals(other.data) &&
            timestamp == other.timestamp && isExit == other.isExit && exitCode == other.exitCode
    }
    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isExit.hashCode()
        result = 31 * result + exitCode
        return result
    }
}

/**
 * Registry to map local terminal tab IDs to remote gRPC session IDs (UUIDs).
 * This enables automatic terminal reattachment.
 */
object TerminalSessionRegistry {
    private val terminalToSession = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun register(terminalId: String, sessionId: String) {
        terminalToSession[terminalId] = sessionId
    }

    fun getSessionId(terminalId: String): String? {
        return terminalToSession[terminalId]
    }

    fun remove(terminalId: String) {
        terminalToSession.remove(terminalId)
    }

    fun clear() {
        terminalToSession.clear()
    }
}
