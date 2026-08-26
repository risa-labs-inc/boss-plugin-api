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
 * @property owner which plugin reported this, or null for a host-initiated transfer.
 *   Also host-set. It exists because [id] alone cannot answer "is this mine": your own
 *   ids come back unqualified and so do the host's, so a plugin installing `com.foo`
 *   while the host installs `com.foo` sees two rows reading `com.foo`. Both are real -
 *   two downloads are running - but only one is yours, and this says which.
 *
 * Extend this with a body-level `val` or a method, NEVER a constructor
 * parameter: a new parameter moves the synthetic constructor and `copy$default`
 * descriptors, so a plugin compiled against the old shape gets
 * `NoSuchMethodError` (the same rule `AiRequest.extras` documents).
 *
 * The consequence of that escape hatch: **do not `copy()` a TransferInfo you
 * received.** `copy()` and `equals` cover constructor parameters only, so any field
 * added later per the rule above is silently dropped by the copy and invisible to
 * equality. Read it, pass it on whole, or build a fresh one.
 */
@HostImplemented
data class TransferInfo(
    val id: String,
    val title: String,
    val kind: TransferKind,
    val phase: TransferPhase,
    val detail: String? = null,
    val progress: Float? = null,
    val cancellable: Boolean = false,
    val owner: String? = null
)

/**
 * Control surface for one transfer the caller started.
 *
 * Every handle must be closed with [done] - use try/finally, so a failure or a
 * cancellation cannot strand a row in the status bar forever.
 *
 * **The host does not sweep.** A plugin disposed mid-transfer, or unloaded by an api
 * hot swap, leaves its row behind unless its own `finally` ran - which it does when the
 * work is on the plugin's scope, since cancelling that scope unwinds through it. Work
 * started off that scope has no such guarantee, and a handle held across a hot swap
 * belongs to the old classloader: report through a fresh one after a reload rather than
 * reusing it.
 */
@HostImplemented
interface TransferHandle {
    /**
     * Report download progress as a fraction in 0..1.
     *
     * Values outside the range are clamped; a NON-FINITE value (`NaN` or an infinity)
     * is treated as indeterminate rather than clamped, because `coerceIn` propagates
     * `NaN` straight to the bar. That is the likely caller bug rather than a
     * hypothetical: `bytesRead.toFloat() / contentLength` is `NaN` when the length is
     * absent or zero - the same missing `Content-Length` this interface already warns
     * about.
     *
     * Not coalesced by the host: every call publishes new state that wakes the
     * status bar, the dialog, and every plugin observing [DownloadCenterProvider.transfers].
     * Report on whole-percent steps or at most ~10x a second - a per-8KiB loop on
     * a 40 MB jar is ~5,000 emissions, each a real recomposition.
     */
    fun progress(fraction: Float)

    /**
     * Move the transfer to a new phase. [TransferPhase.INSTALLING] withdraws cancel.
     *
     * **Clears the progress fraction**, in both directions and by design. Leaving
     * [TransferPhase.DOWNLOADING] must drop it, or a stale `0.6` renders under
     * "Installing" for the whole jar swap; and re-entering it drops it too, which is
     * the route back to indeterminate - call this before a second attempt whose
     * response may not carry a Content-Length, or its bar sits at whatever the first
     * attempt reached while new bytes arrive.
     */
    fun phase(phase: TransferPhase)

    /**
     * Replace the row's detail line, or clear it with null.
     *
     * [DownloadCenterProvider.begin] takes the first one, but the text a row most
     * wants to change is exactly this - "12.4 / 40 MB", "2 of 7 files" - and a
     * multi-step install would otherwise show its first step's text for the whole run.
     * The title does not change: it names the thing, not the step.
     */
    fun detail(text: String?)

    /**
     * Remove the transfer. Idempotent, and safe to call from a finally block.
     *
     * Call it promptly once cancelled. The host keeps the row until then - it cannot
     * know the work has stopped - so a slow socket leaves a live-looking bar behind a
     * Cancel that was already pressed. The host swallows repeat presses (the action
     * is single-shot), so the caller owes only the [done].
     */
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
     *
     * **Readable by every installed plugin, deliberately.** That is the price of one
     * shared status bar: which plugins the user installs and updates, when, and
     * whatever any reporter put in [TransferInfo.detail], are visible to all of them
     * - the same trade `PluginContext.applicationEventBus` makes for browser events.
     * Id qualification protects addressing, not reading. Gate at install time by
     * choosing which plugins you allow, and keep secrets out of the text you report;
     * narrowing this later (redacting other namespaces' detail) would be a breaking
     * behaviour change, so it is stated rather than assumed.
     */
    val transfers: StateFlow<List<TransferInfo>>

    /**
     * Start tracking a transfer.
     *
     * Beginning an [id] that is already in flight does NOT create a second entry.
     * The returned handle is bound to the existing one, and the whole rule is:
     *
     * - its [done] is a no-op - ALWAYS, not merely after the first call - so a
     *   fallback path nested inside an outer operation cannot remove the row its
     *   caller still owns;
     * - its [TransferHandle.progress] and [TransferHandle.phase] DO write the shared
     *   entry. That is deliberate for real nesting, where both are reporting the same
     *   work, and it is why joining is not a way to run two independent operations on
     *   one id: the second would drag the first's bar or withdraw its Cancel;
     * - [title], [kind], [detail] and [onCancel] of the joining call are ignored -
     *   the row keeps what the owner opened it with. A host that cannot tell the two
     *   apart may withdraw Cancel entirely rather than offer one that abandons the
     *   wrong operation;
     * - once the owner has called [done], later reports from a bound handle are
     *   no-ops. Nothing resurrects a finished row.
     *
     * **The [id] round trip.** The host qualifies a plugin-supplied id with the
     * calling plugin's id, so two plugins cannot collide on `"update"` and no plugin
     * can address another's transfer or the host's. That would remove the only join
     * key, so the qualification is invisible to its owner:
     *
     * - an id you pass to [begin] comes back to YOU unchanged in [transfers], which
     *   is what lets you match your own work by the id you chose;
     * - another plugin's rows appear qualified, so they are never mistaken for one
     *   of yours - and are not addressable by you;
     * - host-initiated transfers are NOT qualified, and the host keys plugin work by
     *   pluginId. That is what makes the headline case work: a plugin sees
     *   `id == "<some.plugin.id>"` for an install the host started, and can show its
     *   own button busy for it.
     *
     * A consequence worth stating: because both your ids and the host's come back
     * unqualified, two rows can read the same [id] - yours, and one the host started
     * for the same plugin. Those are two real transfers rather than a duplicate, and
     * [TransferInfo.owner] tells them apart. Read [id] as "some transfer for this
     * thing" and `owner` as "whose".
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
 * Whether this PHASE permits cancellation - half of the rule, not the answer.
 *
 * **Anything rendering a row must use [TransferInfo.cancellable]**, which is this
 * AND a cancel action having been supplied. Reaching for this one instead offers
 * Cancel on a transfer whose reporter passed `onCancel = null`, which is the same
 * bug class it exists to prevent, one level up.
 *
 * It is here so the phase half has one definition rather than being re-derived
 * wherever a host or a plugin asks. Note that centralisation is bounded by
 * parent-first shadowing: the host compiles this file in from its pinned jar, so a
 * later api release cannot change the rule for host-rendered rows - the host's copy
 * wins, as with `AnalyticsKt.track`.
 */
val TransferPhase.allowsCancel: Boolean
    get() = this != TransferPhase.INSTALLING
