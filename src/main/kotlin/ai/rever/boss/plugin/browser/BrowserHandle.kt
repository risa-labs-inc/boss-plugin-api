package ai.rever.boss.plugin.browser

import androidx.compose.runtime.Composable
import ai.rever.boss.plugin.api.HostImplemented

/**
 * Types of form fields that can be detected.
 */
enum class FormFieldType {
    USERNAME,   // Username or login field
    PASSWORD,   // Password field
    EMAIL,      // Email field
    TEXT,       // Generic text field
    UNKNOWN     // Cannot determine
}

/**
 * Information about a form field detected in the browser.
 * Used for secret auto-fill integration.
 */
data class FormFieldInfo(
    /** Type of the form field (username, password, email, etc.) */
    val fieldType: FormFieldType,
    /** Field name attribute */
    val fieldName: String,
    /** Field id attribute */
    val fieldId: String,
    /** Field placeholder text */
    val fieldPlaceholder: String,
    /** Current field value */
    val fieldValue: String,
    /** Parent form's action URL if in a form */
    val parentFormAction: String?,
    /** HTML input type attribute */
    val inputType: String,
    /** Autocomplete attribute value */
    val autocomplete: String
) {
    fun isPasswordField(): Boolean = fieldType == FormFieldType.PASSWORD
    fun isUsernameField(): Boolean = fieldType == FormFieldType.USERNAME || fieldType == FormFieldType.EMAIL
}

/**
 * Information about the context where a right-click occurred in the browser.
 */
data class BrowserContextMenuInfo(
    /** URL of link if right-clicked on a link, null otherwise */
    val linkUrl: String? = null,
    /** Selected text if any text was selected, null otherwise */
    val selectedText: String? = null,
    /** Whether the click was on an editable element (input field, textarea, etc.) */
    val isEditable: Boolean = false,
    /** Whether there's a video element at the click position */
    val hasVideo: Boolean = false,
    /** Whether there's an image at the click position */
    val hasImage: Boolean = false,
    /** Image URL if right-clicked on an image, null otherwise */
    val imageUrl: String? = null,
    /** Current page URL */
    val pageUrl: String = "",
    /** Current page title */
    val pageTitle: String = "",
    /** Form field info if right-clicked on a form field (for secret auto-fill) */
    val formFieldInfo: FormFieldInfo? = null
)

/**
 * Callback for handling context menu requests from the browser.
 */
typealias ContextMenuCallback = (info: BrowserContextMenuInfo) -> Unit

/**
 * Describes a popup/new-tab navigation, preserving the HTTP method and body.
 *
 * For most popups [postData] is null (the popup is a plain GET). When a page
 * submits a form with `target="_blank"`, [postData] carries the request body
 * so the host can replay the POST in the new tab's initial load (otherwise
 * the destination server would receive a GET and miss the form data).
 */
data class PopupNavigation(
    /** Destination URL of the popup. */
    val url: String,
    /** POST body bytes, or null for a GET navigation. */
    val postData: ByteArray? = null,
    /** Content-Type for [postData] (e.g. "application/x-www-form-urlencoded"). */
    val contentType: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PopupNavigation) return false
        if (url != other.url) return false
        if (contentType != other.contentType) return false
        if (postData == null) return other.postData == null
        if (other.postData == null) return false
        return postData.contentEquals(other.postData)
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + (postData?.contentHashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}

/**
 * The name a page-event script's bridge is bound to: a **function parameter in its own scope**, not
 * a property on `window`.
 *
 * The host wraps the script it is given and passes the bridge in, so the script just uses the name:
 *
 * ```
 * // your script, as handed to setPageEventScript
 * document.addEventListener('submit', function () {
 *     __bossPageEvent.emit(JSON.stringify({ kind: 'submit' }));   // one String argument
 * }, true);
 * ```
 *
 * It is an OBJECT with a single `emit(string)` method, not a callable: `__bossPageEvent(json)` is a
 * TypeError. A non-string argument is coerced by the bridge layer. `emit()` with no argument is a
 * TypeError from the bridge and posts nothing; arguments beyond the first are ignored.
 *
 * **Why a parameter and not a `window` property.** A documented global would be reachable by every
 * script on the page, which for a channel whose first consumer posts a password means three
 * separate problems: a page could replace the property and receive the payload itself, forge events
 * into the plugin's sink, and detect BOSS by probing for the name. A binding in the script's own
 * scope has none of those properties - there is nothing on `window` to read, replace, or test for.
 * The host does use a `window` slot to hand the object over - a script cannot be passed arguments -
 * but under a random per-injection name that it reads into a local and deletes **before invoking the
 * script body**, in that order and in the same evaluation.
 *
 * That ordering is part of the contract rather than an implementation detail: a delete placed after
 * the script body is skipped whenever the script throws, which leaves a live bridge object reachable
 * on `window` for the rest of the document - the exact state this design exists to prevent, reached
 * by a syntax error.
 *
 * What the random name does NOT buy is protection from enumeration: `Object.keys(window)` in the gap
 * between the host writing the slot and the script deleting it finds the key whatever it is called.
 * That gap exists only for the one-off injection into a document already running page script; at
 * document start no page code has executed. The name carries no recognisable prefix, so what
 * enumeration can find there is an anonymous key rather than a "this is BOSS" bit.
 *
 * A `const val`, so the literal is compiled into both sides and there is no runtime lookup to get
 * wrong - which also means renaming it would be a compile error at no consumer and a silently dead
 * bridge at every one. `PageEventScriptContractTest` pins the value.
 *
 * See [BrowserHandle.setPageEventScript].
 */
const val PAGE_EVENT_BRIDGE = "__bossPageEvent"

@HostImplemented
interface BrowserHandle {
    /**
     * Unique identifier for this browser handle.
     */
    val id: String

    /**
     * Whether this browser handle is still valid.
     *
     * Returns false if:
     * - The browser has been disposed
     * - The browser was closed externally
     * - The browser engine was reinitialized
     */
    val isValid: Boolean

    /**
     * Load a URL in the browser.
     *
     * @param url The URL to load
     */
    suspend fun loadUrl(url: String)

    /**
     * Load a URL and suspend until the page finishes loading (best-effort: returns
     * after a bounded timeout even if load doesn't complete). Default implementation
     * just delegates to [loadUrl] without waiting.
     *
     * @param url The URL to load
     */
    suspend fun loadUrlAndWait(url: String) { loadUrl(url) }

    /**
     * Execute JavaScript in the page's main frame and return its value (or null on
     * error / no frame). Default returns null for handles that don't support it.
     *
     * @param script The JavaScript to evaluate
     */
    suspend fun executeJavaScript(script: String): Any? = null

    /**
     * Get the current URL.
     *
     * @return The current URL, or empty string if invalid
     */
    fun getCurrentUrl(): String

    /**
     * Get the current page title.
     *
     * @return The current title, or empty string if invalid
     */
    fun getTitle(): String

    /**
     * Add a listener for navigation events.
     *
     * Called when the browser navigates to a new URL.
     *
     * @param listener Callback receiving the new URL
     */
    fun addNavigationListener(listener: (String) -> Unit)

    /**
     * Remove a navigation listener.
     */
    fun removeNavigationListener(listener: (String) -> Unit)

    /**
     * Add a listener for title changes.
     *
     * @param listener Callback receiving the new title
     */
    fun addTitleListener(listener: (String) -> Unit)

    /**
     * Remove a title listener.
     */
    fun removeTitleListener(listener: (String) -> Unit)

    /**
     * Add a listener for favicon changes.
     *
     * @param listener Callback receiving the favicon URL (or null)
     */
    fun addFaviconListener(listener: (String?) -> Unit)

    /**
     * Remove a favicon listener.
     */
    fun removeFaviconListener(listener: (String?) -> Unit)

    /**
     * Navigate back in history.
     */
    fun goBack()

    /**
     * Navigate forward in history.
     */
    fun goForward()

    /**
     * Reload the current page.
     */
    fun reload()

    /**
     * Stop the current page load.
     */
    fun stop()

    /**
     * Check if back navigation is possible.
     */
    fun canGoBack(): Boolean

    /**
     * Check if forward navigation is possible.
     */
    fun canGoForward(): Boolean

    // ============================================================
    // ZOOM CONTROLS
    // ============================================================

    /**
     * Get the current zoom level.
     *
     * @return Zoom level where 1.0 = 100%, 0.5 = 50%, 2.0 = 200%
     */
    fun getZoomLevel(): Double

    /**
     * Set the zoom level.
     *
     * @param level Zoom level where 1.0 = 100%, 0.5 = 50%, 2.0 = 200%
     */
    fun setZoomLevel(level: Double)

    /**
     * Zoom in by one step (typically 10%).
     */
    fun zoomIn()

    /**
     * Zoom out by one step (typically 10%).
     */
    fun zoomOut()

    /**
     * Reset zoom to 100%.
     */
    fun resetZoom()

    /**
     * Add a listener for zoom level changes.
     *
     * @param listener Callback receiving the new zoom level (1.0 = 100%)
     */
    fun addZoomListener(listener: (Double) -> Unit)

    /**
     * Remove a zoom listener.
     */
    fun removeZoomListener(listener: (Double) -> Unit)

    // ============================================================
    // LOADING STATE
    // ============================================================

    /**
     * Check if the browser is currently loading a page.
     *
     * @return true if loading, false otherwise
     */
    fun isLoading(): Boolean

    /**
     * Add a listener for loading state changes.
     *
     * @param listener Callback receiving true when loading starts, false when loading finishes
     */
    fun addLoadingListener(listener: (Boolean) -> Unit)

    /**
     * Remove a loading listener.
     */
    fun removeLoadingListener(listener: (Boolean) -> Unit)

    // ============================================================
    // SECURITY
    // ============================================================

    /**
     * Check if the current page is served over HTTPS.
     *
     * @return true if the URL scheme is "https", false otherwise
     */
    fun isSecure(): Boolean

    // ============================================================
    // CONTEXT MENU
    // ============================================================

    /**
     * Set a callback to be invoked when the user right-clicks in the browser.
     *
     * The callback receives information about the click context (link URL, selected text, etc.)
     * and should display an appropriate context menu.
     *
     * @param callback The callback to invoke on right-click, or null to use default behavior
     */
    fun setContextMenuCallback(callback: ContextMenuCallback?)

    // ============================================================
    // SECRET AUTO-FILL
    // ============================================================

    /**
     * Fill credentials into form fields on the current page.
     *
     * **Deprecated and now a no-op that always returns false. Fill through [executeJavaScript]
     * instead, targeting the element you already identified.** Scheduled for removal.
     *
     * The signature is the problem: it cannot say WHICH field, so the host had to guess, and the
     * guess was wrong in the ways real login pages are actually built. Measured on
     * `accounts.google.com`:
     *
     * - That page carries a `display: none` password input. The guess took the first
     *   `input[type="password"]` in the DOM - that one - so the password went into a hidden field
     *   and the screen did not change.
     * - The visible identifier box was missed entirely. The selector was
     *   `[autocomplete="username"]`, an exact attribute match, and the field declares
     *   `autocomplete="username webauthn"`; `autocomplete` is a space-separated token list per the
     *   HTML spec.
     * - The last-resort strategy was "first text input in a `<form>` containing a password". That
     *   page has no `<form>` element at all.
     *
     * Fixing the guess was tried first. It is not worth keeping, because every caller already knows
     * what the guess was reconstructing: a right-click menu was raised on a specific field, and an
     * autofill suggestion is anchored to a specific box. Filling that element is strictly better
     * information than a heuristic can recover, which puts credential filling on the caller's side
     * of the boundary along with the rules for deciding a field is real - notably that a candidate
     * must be tested for a layout box rather than trusted for being in the DOM. See fluck-browser's
     * `CredentialFill` for a worked implementation.
     *
     * **How to fill instead, including the part that is easy to get wrong.** [executeJavaScript]
     * takes *source*, so the credential has to be interpolated into a script. It must be
     * **JSON-encoded, never concatenated**: a password containing a quote, a backslash, a newline
     * or U+2028 either breaks the script or closes the string literal and runs the remainder as
     * code - an injection whose payload is the user's own secret. Encode it, then select the
     * element you already identified rather than searching for one:
     *
     * ```
     * val js = """(function(){var e=document.activeElement;
     *   if(!e||e.tagName!=='INPUT')return 'nofield';
     *   e.value=${'$'}{jsonEncode(password)}; e.dispatchEvent(new Event('input',{bubbles:true}));
     *   return 'filled';})()"""
     * val outcome = handle.executeJavaScript(js)?.toString()
     * ```
     *
     * Return a sentinel like this rather than relying on the result being non-null:
     * [executeJavaScript] answers `null` for an unsupported handle, a missing frame, a thrown
     * script *and* a script that evaluated to null alike, so without one a failed fill is
     * indistinguishable from success. It is also main-frame only, exactly as the removed
     * implementation was, so nothing regressed there - but a field inside an iframe was never
     * fillable through this API and still is not.
     *
     * **Why a no-op body rather than deletion.** Deleting the declaration is a host-contract
     * change: it takes effect when BossConsole ships a copy without it, and from that point every
     * caller compiled against an older api throws `NoSuchMethodError` at the call site. That is
     * survivable where the call is guarded, and fluck-browser 1.2.19 does guard it - but 1.2.18 and
     * earlier call it bare inside a `launch`, where an `Error` reaches the coroutine uncaught and
     * the host tears the whole plugin down, closing every open browser tab. A user who takes the
     * host update before the plugin one is in exactly that position. Returning false gives every
     * such build a silent no-op instead, which is also strictly better than what they did before -
     * writing a password into a hidden input.
     *
     * **Which copy of this you are reading matters.** [BrowserHandle] is `@HostImplemented`: the
     * host compiles in its own copy and serves it parent-first, so *this jar's* body is shadowed at
     * runtime and changing it here is a compile-time signal only. Every runtime statement above -
     * that the call returns false, and that deleting the declaration would throw - is about the
     * host's copy, and lands with the BossConsole release carrying it. Gate on `minBossVersion`,
     * not `minApiVersion`.
     *
     * @return always false
     */
    @Deprecated(
        message =
            "Cannot express which field to fill, so the host had to guess and guessed wrong on " +
                "real login pages. Now a no-op returning false; fill via executeJavaScript, " +
                "targeting the element you already identified. Scheduled for removal.",
        // Deliberately no ReplaceWith: executeJavaScript is not a drop-in for this, and an
        // auto-applied quick fix would produce code that compiles and fills nothing.
    )
    suspend fun fillCredentials(username: String, password: String, fillBoth: Boolean = true): Boolean = false

    // ============================================================
    // PAGE EVENT CHANNEL
    // ============================================================

    /**
     * Install [script] into every main-frame document as its context is created, and deliver each
     * `emit` call it makes to [onEvent].
     *
     * **How the script is evaluated.** It is wrapped, and the bridge is passed in as a parameter
     * named [PAGE_EVENT_BRIDGE]. Effectively:
     *
     * ```
     * (function (__bossPageEvent) {
     *     // your script, verbatim
     * })(theBridge);
     * ```
     *
     * So write the script as a statement list that may use `__bossPageEvent.emit(json)` anywhere,
     * and do not look for it on `window` - it is not there. Two consequences worth knowing: a
     * top-level `return` is legal (it returns from the wrapper), and the wrapper does not isolate
     * anything else, so declare your own state carefully if the script can be evaluated twice.
     *
     * **The host injects; the caller decides what to look for.** The host evaluates the script and
     * forwards whatever string it hands the bridge. It does not parse the JSON, define an event
     * vocabulary, or know what any event means. That split is the lesson of [fillCredentials]
     * applied to reading instead of writing: a signature that forces the *host* to decide which
     * field matters gets the decision wrong on real pages, because the plugin is the side that
     * knows which box the user acted on.
     *
     * **What this adds that [executeJavaScript] cannot do.** Not read access - a plugin can already
     * read any page content by evaluating a script and taking its return value. What is new is
     * *timing*, in two ways a poll has no answer for:
     *
     * - **Document start.** [script] runs before the page's own scripts, so a listener it installs
     *   sees events the page would otherwise consume first.
     * - **A push.** An event can be delivered while the document that produced it is still alive.
     *   A form submit is followed by a navigation that destroys the JS context, so anything latched
     *   in the page for a later read is racing its own teardown.
     *
     * ## Contract
     *
     * - The callback's FIRST argument is the URL of the document that posted, read by the host at
     *   the moment of the call.
     *   It is authoritative, and it is what events should be attributed by - not a URL inside the
     *   JSON, which is only ever as trustworthy as the code that wrote it. Reading the handle's URL
     *   after the fact is worse still: a navigation the event itself started can overtake it, so a
     *   credential typed on one site can be attributed to the site the login landed on.
     * - [onEvent] is invoked on a **JxBrowser thread**, inside the page's own event dispatch, and
     *   MUST NOT block. Do nothing beyond a non-blocking enqueue.
     * - An exception thrown by [onEvent] is swallowed rather than propagated, because the thread it
     *   would unwind is the page's. That includes `CancellationException`, so do not rely on
     *   cancellation escaping from the sink.
     * - Delivery is in the order the script posted, per document. [onEvent] may be entered
     *   concurrently for *different* documents, so treat it as reentrant.
     * - **The host bounds each payload's size and the rate it will forward**, and drops what
     *   exceeds either rather than queueing it. Do not depend on the exact limits; do not assume a
     *   burst arrives complete.
     * - Main frame only. A form inside a cross-origin iframe is out of reach here, exactly as it is
     *   for [executeJavaScript].
     * - **The host DOES re-inject into the document already loaded** when this is first called, so a
     *   caller does not have to wait for the next navigation. Events posted by that injection are
     *   delivered normally, and may arrive before this call returns.
     * - **[script] is evaluated exactly once per document, and the host guarantees that** - it will
     *   not double-inject, so a script needs no cross-evaluation guard. Worth stating because the
     *   earlier advice to "write a guard" was not implementable: the wrapper gives each evaluation a
     *   fresh function scope, so a script-local flag is invisible to a second run, and the only slot
     *   shared across evaluations is `window` - which is exactly the detectability the parameter
     *   shape exists to remove.
     *
     *   The consequence to know instead: replacing the script does NOT retract the previous one from
     *   a document that is already live. The old generation keeps running there and its events
     *   arrive at the new sink; the replacement takes effect from the next document.
     * - Calling this again replaces the script and callback. **Either argument being null
     *   uninstalls**, and after that no further events are delivered - though as above, a script
     *   already evaluated in a live document is still there until it navigates.
     * - **Uninstall in your `dispose()`.** The host retains [onEvent], whose class comes from the
     *   plugin's classloader, so a live registration pins that classloader - which matters because
     *   the api layer is hot-swappable (unload-all, swap, reload-all). The host clears its own
     *   reference when the handle's browser goes away, but that is the handle's lifetime, not the
     *   plugin's.
     * - A handle whose browser is gone does nothing, and never throws.
     * - Coexists with [startCoBrowseCapture]. Both inject at document start and both deliver JSON
     *   over a bridge, and the host multiplexes the single underlying injection hook, so arming one
     *   does not switch off the other. Note the *inverted* posture though: co-browse has
     *   `maskInputs` so it never streams what the user typed, while this channel exists to carry
     *   exactly that. **The payload may be the user's plaintext secret: do not log it**, host-side
     *   or plugin-side, for the same reason [fillCredentials] warns against concatenating one into
     *   a script.
     * - **The host applies no policy of its own.** If a setting is supposed to govern whether this
     *   runs, the plugin enforces it by not installing a script; the host injects whatever it is
     *   given. Nothing here is gated on a user preference.
     * - **No origin scoping, deliberately.** One call means [script] runs on every main-frame
     *   document for the handle's lifetime - a genuinely different standing from [executeJavaScript],
     *   which is per-call and per-document. Stated rather than left to be discovered: a plugin
     *   installing a keystroke-capable listener here installs it on every site the user visits. An
     *   origin allowlist is additive later (an overload), and is the obvious next parameter if a
     *   consumer wants less than everywhere.
     * - **A drop is not signalled.** A consumer cannot tell "the user submitted nothing" from "the
     *   rate limit ate that event". The host logs a rate-limited line, which is a diagnostic rather
     *   than something a caller can act on.
     *
     * Because the host owns the implementation, a caller needs the **`minBossVersion`** of the
     * release carrying it, not `minApiVersion` alone: against an older host this is the no-op
     * default below, which silently delivers nothing.
     *
     * @param script JavaScript source, evaluated as the body of a function whose single parameter
     *   is named [PAGE_EVENT_BRIDGE]. Null to uninstall.
     * @param onEvent Receives the posting document's URL and the string the script passed to the
     *   bridge, or null to uninstall.
     */
    fun setPageEventScript(script: String?, onEvent: ((url: String, json: String) -> Unit)?) {
        // Default: no-op for hosts without the page-event channel. A caller gets silence, not an
        // error, which is why the gate is minBossVersion rather than minApiVersion.
    }

    // ============================================================
    // CLIPBOARD OPERATIONS
    // ============================================================

    /**
     * Copy the currently selected text to the clipboard.
     */
    fun copySelection()

    /**
     * Paste text from the clipboard at the current cursor position.
     */
    fun paste()

    /**
     * Cut the currently selected text to the clipboard.
     */
    fun cut()

    /**
     * Select all text on the page.
     */
    fun selectAll()

    // ============================================================
    // POPUP AND NEW TAB HANDLING
    // ============================================================

    /**
     * Set a callback to be invoked when a link should open in a new tab.
     *
     * This handles:
     * - Cmd+Click (Mac) / Ctrl+Click (Windows/Linux) on links
     * - Links with target="_blank"
     * - window.open() calls from JavaScript
     *
     * @param callback Receives the URL to open in a new tab
     */
    fun setOpenInNewTabCallback(callback: (String) -> Unit)

    /**
     * Set a callback to be invoked when a link should open in a new tab,
     * with full request details (including POST body) preserved.
     *
     * Prefer this over [setOpenInNewTabCallback] when the popup may be the
     * result of a form-submit with `target="_blank"` (e.g. OncoEMR print) —
     * the host must replay the POST body on the new tab's first load,
     * otherwise the destination receives a GET and the server cannot
     * reconstruct the original request.
     *
     * If both callbacks are set, this one wins. If only the legacy one is set,
     * POST bodies are lost (URL-only handoff).
     *
     * Default implementation is a no-op so this method can be safely added
     * without breaking older hosts that compiled against an earlier API; such
     * hosts simply continue dropping POST bodies on popup→tab handoff.
     *
     * @param callback Receives a [PopupNavigation] describing the request.
     */
    fun setOpenInNewTabWithDataCallback(callback: (PopupNavigation) -> Unit) {
        // Default: no-op for hosts that don't support POST preservation.
    }

    // ============================================================
    // PICTURE IN PICTURE
    // ============================================================

    /**
     * Request Picture-in-Picture mode for videos on the current page.
     *
     * This finds the most appropriate video element and toggles PiP mode:
     * - On YouTube: uses the main video player
     * - Single video: uses that video
     * - Multiple videos: uses the largest visible one
     *
     * If PiP is already active, this exits PiP mode.
     */
    fun requestPictureInPicture()

    // ============================================================
    // FULLSCREEN VIDEO SUPPORT
    // ============================================================

    /**
     * Set up fullscreen handling for video content.
     *
     * When web content requests fullscreen (e.g., clicking fullscreen button on a YouTube video),
     * the browser content is moved to a separate fullscreen window. The plugin should display
     * a placeholder in the tab while in fullscreen mode.
     *
     * @param tabId Unique identifier for this tab (used for state tracking)
     * @param onEnterFullscreen Called when the browser enters fullscreen mode.
     *                          The plugin should show a placeholder UI.
     * @param onExitFullscreen Called when the browser exits fullscreen mode.
     *                         The plugin should restore normal browser display.
     */
    fun setFullscreenHandler(
        tabId: String,
        onEnterFullscreen: () -> Unit,
        onExitFullscreen: () -> Unit
    )

    /**
     * Request exit from fullscreen mode.
     *
     * Call this when the user clicks the fullscreen placeholder to return
     * the browser content to the tab.
     */
    fun requestExitFullscreen()

    // ============================================================
    // CO-BROWSE / TAB SHARING (DOM state-sync)
    // ============================================================

    /**
     * Start streaming rrweb DOM-capture events from this tab.
     *
     * Injects the rrweb recorder into the current page (and subsequent
     * navigations / same-origin frames) and registers a page→host bridge. Each
     * captured event is delivered to [onEvent] as a JSON string (an rrweb event:
     * full snapshot, incremental mutation, scroll, input, etc.). The first event
     * after start is a full snapshot; subsequent events are incremental.
     *
     * Used by the browser tab-sharing feature to mirror this tab to a remote
     * viewer. Only one capture should be active across shared tabs at a time —
     * the host switches the active source as the viewer changes focus.
     *
     * Default implementation is a no-op so the method can be added without
     * breaking older hosts; such hosts simply never emit capture events.
     *
     * @param onEvent Receives each rrweb event as a JSON string.
     * @param maskInputs When true, rrweb masks form-input values (maskAllInputs) so typed
     *   content is not streamed. Passwords are masked regardless. Default false.
     */
    fun startCoBrowseCapture(onEvent: (String) -> Unit, maskInputs: Boolean = false) {
        // Default: no-op for hosts that don't support DOM capture.
    }

    /**
     * Stop DOM capture started by [startCoBrowseCapture]: tears down the
     * recorder, the page-load injection hook, and the page→host bridge.
     * Idempotent. Default no-op.
     */
    fun stopCoBrowseCapture() {
        // Default: no-op.
    }

    /**
     * Whether DOM capture is currently active on this handle.
     */
    fun isCoBrowseCapturing(): Boolean = false

    /**
     * Apply one controlling-viewer semantic event to this tab's real page.
     *
     * [eventJson] is a small JSON object describing an action keyed by an rrweb
     * mirror node id, e.g. `{"kind":"click","id":42}`,
     * `{"kind":"input","id":7,"value":"hi"}`, `{"kind":"scroll","id":1,"x":0,"y":600}`.
     * The host resolves the node id against the live rrweb mirror and dispatches
     * the corresponding DOM event.
     *
     * No-op (returns null) unless remote control has been granted via
     * [setCoBrowseControlEnabled]. Returns a short status string such as
     * "ok" / "denied" / "stale", or null if unsupported.
     *
     * @param eventJson JSON describing the semantic control event.
     * @return A status string, or null on no-op / unsupported.
     */
    suspend fun applyCoBrowseControl(eventJson: String): String? = null

    /**
     * Grant or revoke remote control of this tab. When revoked,
     * [applyCoBrowseControl] becomes a no-op and the in-page guard rejects any
     * control event. Default no-op.
     *
     * @param granted true to allow remote control, false to revoke.
     */
    fun setCoBrowseControlEnabled(granted: Boolean) {
        // Default: no-op.
    }

    /**
     * Dispatch a native input event into the browser engine's input pipeline
     * (trusted input — indistinguishable from local user interaction, unlike
     * the synthetic DOM events of [applyCoBrowseControl]).
     *
     * [inputJson] is a small JSON object:
     * - mouse: `{"kind":"down|up|move|drag","x":10,"y":20,"button":0,"clicks":1}`
     *   (button: 0=primary, 1=middle, 2=secondary; x/y in viewport CSS px)
     * - wheel: `{"kind":"wheel","x":10,"y":20,"dx":0,"dy":-120}`
     * - key:   `{"kind":"keydown|keyup","key":"Enter","code":"KeyA","ch":"a",
     *            "shift":false,"ctrl":false,"alt":false,"meta":false}`
     *
     * No-op unless remote control has been granted via
     * [setCoBrowseControlEnabled]. Default no-op.
     *
     * @param inputJson JSON describing the native input event.
     */
    fun dispatchCoBrowseInput(inputJson: String) {
        // Default: no-op.
    }

    // ============================================================
    // DEVELOPER TOOLS
    // ============================================================

    /**
     * Show the browser's developer tools (DevTools).
     *
     * This opens JxBrowser's built-in DevTools window for debugging,
     * inspecting elements, network requests, console, etc.
     */
    fun showDevTools()

    /**
     * Composable content that renders the browser.
     *
     * This should be called within a Compose hierarchy to display
     * the browser content. The browser will fill the available space.
     */
    @Composable
    fun Content()

    /**
     * Dispose this browser handle and release resources.
     *
     * After calling this, [isValid] will return false and
     * all other methods will be no-ops.
     */
    fun dispose()
}
