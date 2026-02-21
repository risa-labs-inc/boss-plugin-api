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
        @Suppress("UNCHECKED_CAST")
        eventsOfType(FileChangeEvent::class.java as Class<FileChangeEvent>)

    /**
     * Subscribe to project selection events.
     *
     * @return Flow of project selection events
     */
    fun projectChanges(): Flow<ProjectChangeEvent> =
        @Suppress("UNCHECKED_CAST")
        eventsOfType(ProjectChangeEvent::class.java as Class<ProjectChangeEvent>)

    /**
     * Subscribe to window focus events.
     *
     * @return Flow of window focus events
     */
    fun windowFocusChanges(): Flow<WindowFocusEvent> =
        @Suppress("UNCHECKED_CAST")
        eventsOfType(WindowFocusEvent::class.java as Class<WindowFocusEvent>)

    /**
     * Subscribe to plugin lifecycle events.
     *
     * @return Flow of plugin lifecycle events
     */
    fun pluginLifecycleEvents(): Flow<PluginLifecycleEvent> =
        @Suppress("UNCHECKED_CAST")
        eventsOfType(PluginLifecycleEvent::class.java as Class<PluginLifecycleEvent>)

    /**
     * Subscribe to tab events.
     *
     * @return Flow of tab events
     */
    fun tabEvents(): Flow<TabEvent> =
        @Suppress("UNCHECKED_CAST")
        eventsOfType(TabEvent::class.java as Class<TabEvent>)

    /**
     * Subscribe to authentication events.
     *
     * @return Flow of authentication events
     */
    fun authEvents(): Flow<AuthEvent> =
        @Suppress("UNCHECKED_CAST")
        eventsOfType(AuthEvent::class.java as Class<AuthEvent>)

    /**
     * Subscribe to terminal session events.
     *
     * @return Flow of terminal session events
     */
    fun terminalSessionEvents(): Flow<TerminalSessionEvent> =
        @Suppress("UNCHECKED_CAST")
        eventsOfType(TerminalSessionEvent::class.java as Class<TerminalSessionEvent>)
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
