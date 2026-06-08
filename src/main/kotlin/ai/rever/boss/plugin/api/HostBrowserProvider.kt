package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow

/**
 * Provider exposed to plugins for rendering a browser that runs on the active
 * BOSS host.
 *
 * When the active host is **local**, the plugin renders its own in-process
 * browser engine (e.g. JxBrowser) — [openSession] returns `null` as the signal.
 *
 * When the active host is **remote**, [openSession] returns a
 * [HostBrowserSession] backed by the server's gRPC `BrowserService`; the plugin
 * draws the streamed JPEG frames and forwards input. All gRPC/host wiring lives
 * in the platform implementation so plugin code stays free of `boss-ipc` and
 * host-internal types — exactly like [HostTerminalProvider].
 *
 * Plugin developers never branch on local-vs-remote at the transport level;
 * they observe [observeIsRemote] to pick which UI to render and call
 * [openSession] unconditionally.
 */
interface HostBrowserProvider {
    /** Human-readable identifier of the active host. "local" or "tcp://nas:5800". */
    val displayName: String

    /** Reactive: emits true while the active host for the plugin's window is remote. */
    fun observeIsRemote(): Flow<Boolean>

    /**
     * Open (or reattach to) a remote browser session keyed by [sessionId] (the
     * tab id). Stable ids let the server reattach to an existing page on
     * workspace restore instead of reloading. [width]/[height] are the initial
     * render dimensions. Returns null when the active host is local.
     */
    suspend fun openSession(
        sessionId: String,
        initialUrl: String,
        width: Int,
        height: Int,
    ): HostBrowserSession?
}

/**
 * A live remote browser session. [close] detaches this client; the server may
 * keep the underlying page alive for later reattach.
 */
interface HostBrowserSession {
    val id: String

    /** Hot stream of JPEG-encoded frames. Collection starts the server screencast. */
    fun frames(): Flow<ByteArray>

    suspend fun sendInput(
        type: String,
        x: Int,
        y: Int,
        deltaX: Int = 0,
        deltaY: Int = 0,
        keyText: String = "",
    )

    suspend fun resize(width: Int, height: Int)

    suspend fun navigate(url: String)

    suspend fun goBack()

    suspend fun goForward()

    suspend fun reload()

    /** Current committed URL on the server (for URL-bar resync after reattach). */
    suspend fun currentUrl(): String

    fun close()
}
