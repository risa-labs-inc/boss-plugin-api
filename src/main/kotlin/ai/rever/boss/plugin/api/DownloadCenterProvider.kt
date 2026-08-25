package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow

/**
 * What a tracked transfer belongs to. Drives the verb the host shows
 * ("Installing", "Updating", "Downloading BOSS").
 *
 * An open set: the host switches on it, so a new constant is a host-contract
 * change gated by `minBossVersion`, not something the api jar can add alone.
 */
@HostImplemented
enum class TransferKind {
    PLUGIN_INSTALL,
    PLUGIN_UPDATE,
    APP_UPDATE,
    OTHER
}

/**
 * Where a transfer has got to.
 *
 * [INSTALLING] is the one phase that cannot be cancelled: once a plugin's jar
 * swap has begun, abandoning it leaves the plugin unloaded, so the host disables
 * the action rather than offering one that can corrupt an install. Everything
 * else can be abandoned - a download is bytes, and a downloaded-but-uninstalled
 * app update is a file to delete.
 *
 * **There is deliberately no failure phase.** A transfer that fails ends, and the
 * row goes with it; saying why is the caller's job, through whatever it already
 * uses to report one (a toast, a panel banner, a status message). A row that
 * lingered to show an error would need its own dismissal, and a progress bar is
 * the wrong surface for a message the user has to read. The cost of the choice is
 * that a vanished row does not distinguish success from failure on its own - so a
 * caller whose failure has no other surface should give it one.
 */
@HostImplemented
enum class TransferPhase {
    /** Resolving what to fetch (release lookup, store metadata). No bytes yet. */
    PREPARING,

    /** Bytes are arriving. [TransferInfo.progress] is meaningful when non-null. */
    DOWNLOADING,

    /** Downloaded; unloading/loading, verifying, or otherwise committing it. */
    INSTALLING,

    /**
     * Downloaded and waiting for the user to press Install.
     *
     * **Reported by the host, for the application's own update.** A plugin has no
     * way to hear that Install was pressed - [DownloadCenterProvider.begin] takes
     * a cancel action and nothing else - so a plugin setting this phase would show
     * a button that does nothing. Plugins go from [DOWNLOADING] to [INSTALLING].
     */
    READY_TO_INSTALL
}

/**
 * One transfer currently in flight, as the host's download center sees it.
 *
 * @property id stable key for the transfer - the pluginId for plugin work,
 *   so a plugin can ask "is this one busy?" without bookkeeping of its own.
 * @property progress fraction in 0..1, or null while indeterminate (size
 *   unknown, or a phase that is not a download).
 * @property cancellable derived by the host: a cancel action was supplied AND
 *   the transfer is not being installed. Never set by a reporter - it is a
 *   constructor parameter because the host builds these, not an input.
 *
 * Extend this with a body-level `val` or a method, NEVER a constructor
 * parameter: a new parameter moves the synthetic constructor and `copy$default`
 * descriptors, so a plugin compiled against the old shape gets
 * `NoSuchMethodError` (the same rule `AiRequest.extras` documents).
 */
@HostImplemented
data class TransferInfo(
    val id: String,
    val title: String,
    val kind: TransferKind,
    val phase: TransferPhase,
    val detail: String? = null,
    val progress: Float? = null,
    val cancellable: Boolean = false
)

/**
 * Control surface for one transfer the caller started.
 *
 * Every handle must be closed with [done] - use try/finally, so a failure or a
 * cancellation cannot strand a row in the status bar forever.
 */
@HostImplemented
interface TransferHandle {
    /**
     * Report download progress as a fraction in 0..1. Values outside are clamped.
     *
     * Not coalesced by the host: every call publishes new state that wakes the
     * status bar, the dialog, and every plugin observing [DownloadCenterProvider.transfers].
     * Report on whole-percent steps or at most ~10x a second - a per-8KiB loop on
     * a 40 MB jar is ~5,000 emissions, each a real recomposition.
     */
    fun progress(fraction: Float)

    /** Move the transfer to a new phase. [TransferPhase.INSTALLING] withdraws cancel. */
    fun phase(phase: TransferPhase)

    /** Remove the transfer. Idempotent, and safe to call from a finally block. */
    fun done()
}

/**
 * The host's download center: one place every in-flight transfer is reported
 * to, so the bottom status bar and its dialog can show all of them together
 * whether the host or a plugin started the work.
 *
 * Not to be confused with [DownloadDataProvider], which is the browser's file
 * downloads. This one is about plugin jars and the application itself.
 *
 * A plugin that fetches something long-running should report it here rather
 * than building its own indicator: the user gets one progress item, one dialog,
 * and one Cancel that works the same everywhere.
 *
 * Returns null from [PluginContext] on hosts without a download center.
 */
@HostImplemented
interface DownloadCenterProvider {
    /**
     * Everything in flight host-wide, plugin transfers and the application
     * update alike. Observe this to reflect work someone else started - a
     * plugin's own Install button should look busy when the same install was
     * triggered from a toast or from the host's own prompt.
     */
    val transfers: StateFlow<List<TransferInfo>>

    /**
     * Start tracking a transfer.
     *
     * Beginning an [id] that is already in flight does NOT create a second
     * entry: the returned handle is bound to the existing one and its [done]
     * is a no-op, so a fallback path nested inside an outer operation cannot
     * remove the row its caller still owns.
     *
     * Two things about [id]. The host namespaces a plugin-supplied one with the
     * calling plugin's id, so two plugins cannot collide on `"update"` and no
     * plugin can address another's transfer (or the host's). And it is the key a
     * plugin matches its own work by in [transfers], so the natural choice is the
     * pluginId being installed.
     *
     * Keep [title] and [detail] free of signed URLs, tokens and absolute paths:
     * every installed plugin can read [transfers].
     *
     * @param onCancel invoked when the user cancels; supply it only when the
     *   work can actually be abandoned safely. Null means no Cancel is offered.
     */
    fun begin(
        id: String,
        title: String,
        kind: TransferKind,
        detail: String? = null,
        onCancel: (() -> Unit)? = null
    ): TransferHandle
}

/**
 * Whether a transfer in this phase may be cancelled.
 *
 * The rule lived as prose in the api and as a separate expression in the host and
 * in the Toolbox's row rendering - two copies that can drift, with "Cancel offered
 * mid-jar-swap" as the failure mode. One property, so there is one answer.
 */
val TransferPhase.allowsCancel: Boolean
    get() = this != TransferPhase.INSTALLING
