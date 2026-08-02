package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.Flow

/**
 * Event bus for application-wide state change notifications.
 *
 * This allows plugins to react to application events without polling,
 * enabling efficient event-driven architecture for dynamic plugins.
 */
interface ApplicationEventBus {

    /**
     * Subscribe to all application events.
     *
     * @return Flow of all application events
     */
    fun events(): Flow<ApplicationEvent>

    /**
     * Subscribe to events of a specific type.
     *
     * @param eventType The type of events to receive
     * @return Flow of filtered events
     */
    fun <T : ApplicationEvent> eventsOfType(eventType: Class<T>): Flow<T>

    /**
     * Publish an event to all subscribers.
     * Note: Only certain events may be published by plugins (custom events).
     *
     * @param event The event to publish
     */
    fun publish(event: ApplicationEvent)

    /**
     * Subscribe to file change events.
     *
     * @return Flow of file change events
     */
    fun fileChanges(): Flow<FileChangeEvent> =
        eventsOfType(FileChangeEvent::class.java)

    /**
     * Subscribe to project selection events.
     *
     * @return Flow of project selection events
     */
    fun projectChanges(): Flow<ProjectChangeEvent> =
        eventsOfType(ProjectChangeEvent::class.java)

    /**
     * Subscribe to window focus events.
     *
     * @return Flow of window focus events
     */
    fun windowFocusChanges(): Flow<WindowFocusEvent> =
        eventsOfType(WindowFocusEvent::class.java)

    /**
     * Subscribe to plugin lifecycle events.
     *
     * @return Flow of plugin lifecycle events
     */
    fun pluginLifecycleEvents(): Flow<PluginLifecycleEvent> =
        eventsOfType(PluginLifecycleEvent::class.java)

    /**
     * Subscribe to tab events.
     *
     * @return Flow of tab events
     */
    fun tabEvents(): Flow<TabEvent> =
        eventsOfType(TabEvent::class.java)

    /**
     * Subscribe to authentication events.
     *
     * @return Flow of authentication events
     */
    fun authEvents(): Flow<AuthEvent> =
        eventsOfType(AuthEvent::class.java)

    /**
     * Subscribe to terminal session events.
     *
     * @return Flow of terminal session events
     */
    fun terminalSessionEvents(): Flow<TerminalSessionEvent> =
        eventsOfType(TerminalSessionEvent::class.java)
}

/**
 * Process-global registry for the single application event bus.
 *
 * The bus implementation and the host-side `publishSystemEvent` bridge live in the
 * `composeApp` module, whose package is NOT shared/parent-first. In-process plugins are
 * loaded by classloaders that can resolve their own copies of those host classes, so the
 * host and a plugin may each see a different `ApplicationEventBusImpl` class — and thus a
 * different per-classloader `instance` singleton. The result: the host emits system events
 * to one instance while plugins subscribe to another, and the events are silently dropped.
 *
 * This registry lives in `ai.rever.boss.plugin.api`, which IS a shared/parent-first package
 * (see `PluginClassLoader.defaultSharedPackages`), so there is exactly ONE copy across the
 * host and every in-process plugin. The bus implementation registers itself here on creation,
 * and the host publishes system events through [systemPublisher]. This guarantees host-emitted
 * events reach the same bus instance that plugins subscribe to, regardless of classloader.
 */
object ApplicationEventBusRegistry {
    /** The single shared bus instance, or null until the first one is created. */
    @Volatile
    var bus: ApplicationEventBus? = null

    /**
     * Publishes a host/system [ApplicationEvent] onto the shared bus, bypassing the
     * plugin-facing [ApplicationEventBus.publish] gate. Set by the bus implementation when
     * it is created; null until then (in which case host system events are a no-op).
     */
    @Volatile
    var systemPublisher: ((ApplicationEvent) -> Unit)? = null
}

/**
 * Base interface for all application events.
 */
sealed interface ApplicationEvent {
    /**
     * Timestamp when the event occurred (epoch milliseconds).
     */
    val timestamp: Long
        get() = System.currentTimeMillis()
}

/**
 * Event emitted when a file changes (created, modified, deleted).
 */
data class FileChangeEvent(
    val filePath: String,
    val changeType: FileChangeType,
    val projectPath: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Type of file change.
 */
enum class FileChangeType {
    CREATED,
    MODIFIED,
    DELETED,
    RENAMED
}

/**
 * Event emitted when project selection changes.
 */
data class ProjectChangeEvent(
    val projectPath: String?,
    val previousProjectPath: String?,
    val windowId: String,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Event emitted when window focus changes.
 */
data class WindowFocusEvent(
    val windowId: String,
    val hasFocus: Boolean,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Event emitted for plugin lifecycle changes.
 */
data class PluginLifecycleEvent(
    val pluginId: String,
    val lifecycleState: PluginLifecycleState,
    val reason: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Plugin lifecycle states.
 */
enum class PluginLifecycleState {
    LOADING,
    LOADED,
    ENABLED,
    DISABLED,
    UNLOADING,
    UNLOADED,
    ERROR
}

/**
 * Event emitted for tab operations.
 */
data class TabEvent(
    val tabId: String,
    val tabType: TabEventType,
    val panelId: String? = null,
    val windowId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Type of tab event.
 */
enum class TabEventType {
    OPENED,
    CLOSED,
    SELECTED,
    DESELECTED,
    TITLE_CHANGED,
    MOVED
}

/**
 * Event emitted for authentication state changes.
 */
data class AuthEvent(
    val authState: AuthEventState,
    val userId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Authentication event states.
 */
enum class AuthEventState {
    SIGNED_IN,
    SIGNED_OUT,
    SESSION_EXPIRED,
    SESSION_REFRESHED
}

/**
 * Custom event that plugins can publish.
 * Use this for plugin-to-plugin communication.
 */
data class CustomPluginEvent(
    val sourcePluginId: String,
    val eventName: String,
    val payload: Map<String, Any?> = emptyMap(),
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Event emitted for terminal session lifecycle changes.
 */
data class TerminalSessionEvent(
    val sessionId: String,
    val eventType: TerminalSessionEventType,
    val terminalId: String? = null,
    val windowId: String? = null,
    val title: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Type of terminal session event.
 */
enum class TerminalSessionEventType {
    CREATED,
    DESTROYED,
    TITLE_CHANGED
}

/**
 * Navigation and engagement activity in the integrated browser.
 *
 * **This event deliberately carries only [domain] — never the full URL, path, query
 * string, or page title.** The reduction to a registrable domain happens in the host,
 * before the event is constructed, so a full URL never enters the event at all. That is
 * the safety property: consumers cannot recover the page a user was on, only the site.
 *
 * BOSS is used in healthcare contexts, so do **not** widen this event with a `url`,
 * `path`, `query`, or `title` field. A consumer that needs page-level detail should be
 * treated as a new privacy decision, not an additive change. Note that downstream
 * analytics scrubbers deny properties named `url`/`uri`/`href`/`link` outright, so such a
 * field would in practice be silently dropped rather than delivered.
 *
 * The engagement fields answer "how much is this site used" without saying what was on
 * the screen: how long a visit lasted, how much of that was active rather than left open
 * in a background tab, and how deep into a site the user got.
 *
 * @property browserEventType what happened. Treat as open — always keep an `else` branch.
 * @property domain registrable domain (eTLD+1), lowercased and `www.`-stripped —
 *   e.g. `"availity.com"` for `https://portal.availity.com/auth?patient=123`.
 * @property windowId the BOSS window the browser is hosted in, when known.
 * @property navigationType how the user got here, on [BrowserEventType.PAGE_VIEWED].
 * @property dwellMs wall-clock time the page was open, on [BrowserEventType.PAGE_LEFT].
 * @property activeMs the portion of [dwellMs] the page was actually focused and receiving
 *   input. A page left open in a background tab overnight has a huge [dwellMs] and a tiny
 *   [activeMs]; reporting only the former would badly overstate engagement.
 * @property pageIndexInVisit 1-based position of this page within an unbroken run of
 *   navigations on the same [domain] — navigation depth, without the paths that produced
 *   it. Resets when the user leaves the site.
 */
data class BrowserEvent(
    val browserEventType: BrowserEventType,
    val domain: String,
    val windowId: String? = null,
    val navigationType: BrowserNavigationType? = null,
    val dwellMs: Long? = null,
    val activeMs: Long? = null,
    val pageIndexInVisit: Int? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Type of browser navigation/engagement event.
 *
 * Treat as **open**: always keep an `else` branch. Adding a constant here becomes a
 * `minBossVersion` change once a BossConsole release pins this api version, because the
 * host's filtered copy of this package shadows the runtime jar parent-first.
 */
enum class BrowserEventType {
    PAGE_VIEWED,
    PAGE_LEFT,
    TAB_OPENED,
    TAB_CLOSED,
    TAB_ACTIVATED
}

/**
 * How a navigation was initiated.
 *
 * Distinguishes deliberate destinations (typed, bookmark) from incidental ones (a link,
 * a reload) so engagement figures aren't inflated by refreshes. Treat as **open**.
 */
enum class BrowserNavigationType {
    TYPED,
    LINK,
    BACK_FORWARD,
    RELOAD,
    OTHER
}

/**
 * A user interaction *inside* a page in the integrated browser.
 *
 * **Structural attributes only.** This event describes the shape of what was interacted
 * with — the kind of element and where it sits in the document — and never the content
 * of the page or of the interaction. The host's collector is written so the following are
 * not merely stripped but never read out of the DOM in the first place:
 *
 * - element text, `textContent`, `innerText`, `placeholder`, `title`, `alt`
 * - `aria-label` and any other label, and `id` or `class` attributes
 * - input `value` — for any field, of any type
 * - `href`, `src`, `action`, and every other URL-bearing attribute
 * - clipboard contents on [BrowserInteractionType.COPY] / [BrowserInteractionType.PASTE],
 *   which record only that it happened
 *
 * That exclusion list is the whole design. In a healthcare deployment the page body is
 * PHI: a label reads "Patient MRN", an input value *is* the MRN, and an `id` is routinely
 * `patient-4417`. Reporting which *kind* of control was clicked at which *position* is
 * safe; reporting anything the page rendered is not. Widening this event is a privacy
 * decision requiring review, not an additive change.
 *
 * @property interactionType what the user did. Treat as open — always keep an `else`.
 * @property domain registrable domain (eTLD+1) the interaction happened on.
 * @property elementTag lowercased HTML tag, e.g. `"button"`, `"a"`, `"input"`.
 * @property elementRole ARIA **role** — a structural classification like `"tab"` or
 *   `"menuitem"`. Not `aria-label`, which is a human-readable label and is never read.
 * @property inputType the `type` attribute of an input (`"checkbox"`, `"text"`, …), which
 *   says what kind of control it is and never what was entered into it.
 * @property fieldName a form field's `name` attribute — a schema identifier chosen by the
 *   site's developer, not user data. Host-sanitized: length-capped, restricted charset,
 *   long digit runs redacted.
 * @property elementPath bounded structural path of **tag names and sibling positions
 *   only**, e.g. `"form>div:2>button:1"`. Carries no ids, classes, or text, so it
 *   distinguishes controls on a page without describing them.
 * @property scrollDepthPercent furthest scroll reached, quantised to 25/50/75/100.
 * @property repeatCount how many times the interaction repeated in quick succession —
 *   the signal behind [BrowserInteractionType.RAGE_CLICK].
 * @property windowId the BOSS window the browser is hosted in, when known.
 */
data class BrowserInteractionEvent(
    val interactionType: BrowserInteractionType,
    val domain: String,
    val elementTag: String? = null,
    val elementRole: String? = null,
    val inputType: String? = null,
    val fieldName: String? = null,
    val elementPath: String? = null,
    val scrollDepthPercent: Int? = null,
    val repeatCount: Int? = null,
    val windowId: String? = null,
    override val timestamp: Long = System.currentTimeMillis()
) : ApplicationEvent

/**
 * Kind of in-page interaction.
 *
 * Treat as **open**: always keep an `else` branch. Adding a constant here becomes a
 * `minBossVersion` change once a BossConsole release pins this api version.
 */
enum class BrowserInteractionType {
    CLICK,
    RAGE_CLICK,
    SCROLL_DEPTH,
    FIELD_FOCUSED,
    FORM_SUBMITTED,
    COPY,
    PASTE
}
