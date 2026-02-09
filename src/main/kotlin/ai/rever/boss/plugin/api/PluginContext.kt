package ai.rever.boss.plugin.api

import ai.rever.boss.plugin.browser.BrowserService
import kotlinx.coroutines.CoroutineScope

/**
 * Marker interface for plugin sandbox.
 *
 * This interface is defined here to avoid circular dependencies.
 * The actual implementation is in the plugin-sandbox module.
 *
 * Plugins can use this to record errors, heartbeats, and check health status.
 */
interface PluginSandboxRef {
    /**
     * Unique identifier of the plugin running in this sandbox.
     */
    val pluginId: String

    /**
     * Record that the plugin is alive and responsive.
     */
    fun recordHeartbeat()

    /**
     * Record that a successful operation completed.
     */
    fun recordSuccess()

    /**
     * Record an error that occurred in the plugin.
     */
    fun recordError(error: Throwable)
}

/**
 * Context provided to plugins for registration and runtime access.
 *
 * This interface abstracts the host application's services that plugins need.
 * Plugins should depend on this interface rather than concrete implementations.
 */
interface PluginContext {
    /**
     * Registry for panel registration.
     * Plugins use this to register their panel components.
     */
    val panelRegistry: PanelRegistry

    /**
     * Registry for tab type registration.
     * Plugins use this to register custom tab types.
     */
    val tabRegistry: TabRegistry

    /**
     * Coroutine scope tied to the plugin's lifecycle.
     * Use this for long-running operations that should be cancelled when the plugin is disposed.
     */
    val pluginScope: CoroutineScope

    /**
     * Optional reference to the plugin's sandbox for health reporting.
     *
     * Returns null if sandboxing is not enabled for this context.
     * Plugins can use this to record heartbeats and errors for health monitoring.
     */
    val sandbox: PluginSandboxRef?
        get() = null

    /**
     * Optional browser service for plugins that need embedded browser capabilities.
     *
     * Returns null if browser service is not available (e.g., JxBrowser not licensed
     * or browser engine failed to initialize).
     *
     * Plugins can use this to create browser instances for displaying web content.
     */
    val browserService: BrowserService?
        get() = null

    /**
     * The plugin's manifest, providing access to configuration declared in plugin.json.
     *
     * Returns null for built-in plugins that don't have a manifest file.
     * External plugins will always have their manifest available here.
     */
    val manifest: PluginManifest?
        get() = null

    // ============================================================
    // SERVICE PROVIDERS FOR DYNAMIC PLUGINS
    // These allow dynamic plugins to access host services without
    // requiring explicit injection at registration time.
    // ============================================================

    /**
     * Optional performance data provider for plugins that display performance metrics.
     *
     * Returns null if performance monitoring is not available.
     * Dynamic plugins can use this instead of static property injection.
     */
    val performanceDataProvider: PerformanceDataProvider?
        get() = null

    /**
     * Optional download data provider for plugins that display download status.
     *
     * Returns null if download management is not available.
     * Dynamic plugins can use this instead of explicit parameter injection.
     */
    val downloadDataProvider: DownloadDataProvider?
        get() = null

    /**
     * Optional bookmark data provider for plugins that manage bookmarks.
     *
     * Returns null if bookmark management is not available.
     * Dynamic plugins can use this instead of CompositionLocal access.
     */
    val bookmarkDataProvider: BookmarkDataProvider?
        get() = null

    /**
     * Optional workspace data provider for plugins that access workspace information.
     *
     * Returns null if workspace management is not available.
     */
    val workspaceDataProvider: WorkspaceDataProvider?
        get() = null

    /**
     * Optional split view operations for plugins that need to open tabs/workspaces.
     *
     * Returns null if split view operations are not available.
     */
    val splitViewOperations: SplitViewOperations?
        get() = null

    /**
     * Optional Git data provider for plugins that display git information.
     *
     * Returns null if git operations are not available.
     * Dynamic plugins can use this to access commit log, file status, etc.
     */
    val gitDataProvider: GitDataProvider?
        get() = null

    /**
     * Optional file system data provider for plugins that browse files.
     *
     * Returns null if file system operations are not available.
     * Dynamic plugins can use this for the codebase panel.
     */
    val fileSystemDataProvider: FileSystemDataProvider?
        get() = null

    /**
     * Optional secret data provider for plugins that manage secrets.
     *
     * Returns null if secret management is not available.
     * Dynamic plugins can use this for secret-manager and user-secret-list panels.
     */
    val secretDataProvider: SecretDataProvider?
        get() = null

    /**
     * Optional run configuration data provider for plugins that execute code.
     *
     * Returns null if run configuration is not available.
     * Dynamic plugins can use this for the run-configurations panel.
     */
    val runConfigurationDataProvider: RunConfigurationDataProvider?
        get() = null

    /**
     * Optional active tabs provider for plugins that display tab overview.
     *
     * Returns null if active tabs data is not available.
     * Dynamic plugins can use this for the topofmind panel.
     */
    val activeTabsProvider: ActiveTabsProvider?
        get() = null

    /**
     * Get the current window ID.
     *
     * Returns null if window ID is not available.
     * Dynamic plugins need this for window-scoped operations.
     */
    val windowId: String?
        get() = null

    /**
     * Get the currently selected project path.
     *
     * Returns null if no project is selected.
     * Dynamic plugins need this for project-specific operations.
     */
    val projectPath: String?
        get() = null

    /**
     * Optional authentication data provider for plugins that need auth state.
     *
     * Returns null if authentication services are not available.
     * Dynamic plugins can use this to check user roles and permissions.
     */
    val authDataProvider: AuthDataProvider?
        get() = null

    /**
     * Optional user management provider for admin plugins.
     *
     * Returns null if user management is not available or user is not admin.
     * Dynamic plugins can use this for the admin-role-management panel.
     */
    val userManagementProvider: UserManagementProvider?
        get() = null

    /**
     * Optional role management provider for admin plugins.
     *
     * Returns null if role management is not available or user is not admin.
     * Dynamic plugins can use this for the role-creation panel.
     */
    val roleManagementProvider: RoleManagementProvider?
        get() = null

    /**
     * Optional terminal content provider for the terminal panel plugin.
     *
     * Returns null if terminal functionality is not available.
     * Dynamic plugins can use this for the terminal panel.
     */
    val terminalContentProvider: TerminalContentProvider?
        get() = null

    /**
     * Optional panel event provider for plugins that need to trigger panel events.
     *
     * Returns null if panel event functionality is not available.
     */
    val panelEventProvider: PanelEventProvider?
        get() = null

    /**
     * Optional settings provider for plugins that need to open settings.
     *
     * Returns null if settings functionality is not available.
     */
    val settingsProvider: SettingsProvider?
        get() = null

    /**
     * Optional context menu provider for plugins that need context menu functionality.
     *
     * Returns null if context menu functionality is not available.
     * Dynamic plugins can use this to display context menus with the host app's styling.
     */
    val contextMenuProvider: ContextMenuProvider?
        get() = null

    /**
     * Optional log data provider for plugins that display captured logs.
     *
     * Returns null if log capture is not available.
     * Dynamic plugins can use this for the console panel instead of
     * accessing GlobalLogCapture directly (which doesn't work due to
     * classloader isolation in dynamic plugins).
     */
    val logDataProvider: LogDataProvider?
        get() = null

    /**
     * Optional Plugin Store API key provider for plugins that manage API keys.
     *
     * Returns null if plugin store API key management is not available.
     * Dynamic plugins can use this for creating CI/CD publishing keys.
     */
    val pluginStoreApiKeyProvider: PluginStoreApiKeyProvider?
        get() = null

    /**
     * Optional factory for creating tab update providers.
     *
     * Returns null if tab update functionality is not available.
     * Dynamic tab plugins can use this to update their tab's title, favicon,
     * and other metadata displayed in the tab bar.
     */
    val tabUpdateProviderFactory: TabUpdateProviderFactory?
        get() = null

    /**
     * Optional dashboard content provider for browser plugins.
     *
     * Returns null if dashboard content is not available.
     * Browser plugins can use this to display the host's dashboard
     * for about:blank pages instead of implementing their own.
     */
    val dashboardContentProvider: DashboardContentProvider?
        get() = null

    /**
     * Optional zoom settings provider for browser plugins.
     *
     * Returns null if zoom settings persistence is not available.
     * Browser plugins can use this to persist per-domain zoom levels.
     */
    val zoomSettingsProvider: ZoomSettingsProvider?
        get() = null

    /**
     * Optional URL history provider for browser plugins.
     *
     * Returns null if URL history is not available.
     * Browser plugins can use this for URL bar autocomplete suggestions.
     */
    val urlHistoryProvider: UrlHistoryProvider?
        get() = null

    /**
     * Optional screen capture provider for browser plugins.
     *
     * Returns null if screen capture functionality is not available.
     * Browser plugins can use this for:
     * - Getting internal browser tabs to display in screen capture picker
     * - Checking/requesting macOS screen capture permission
     *
     * The actual screen capture flow (StartCaptureSessionCallback, picker UI)
     * is handled by the plugin itself.
     */
    val screenCaptureProvider: ScreenCaptureProvider?
        get() = null

    /**
     * Optional terminal tab content provider for terminal tab plugins.
     *
     * Returns null if terminal tab functionality is not available.
     * Dynamic terminal tab plugins can use this to render persistent terminal content.
     */
    val terminalTabContentProvider: TerminalTabContentProvider?
        get() = null

    /**
     * Optional editor content provider for code editor tab plugins.
     *
     * Returns null if editor functionality is not available.
     * Dynamic editor plugins can use this to render code editor content.
     */
    val editorContentProvider: EditorContentProvider?
        get() = null

    // ============================================================
    // PHASE 4: CORE MISSING APIs
    // Event-driven architecture, user feedback, and plugin storage
    // ============================================================

    /**
     * Optional notification provider for displaying toasts/notifications.
     *
     * Returns null if notification functionality is not available.
     * Dynamic plugins can use this to show user feedback without
     * implementing their own notification UI.
     */
    val notificationProvider: NotificationProvider?
        get() = null

    /**
     * Optional application event bus for state change notifications.
     *
     * Returns null if event bus is not available.
     * Dynamic plugins can use this to react to application events
     * (file changes, project selection, etc.) without polling.
     */
    val applicationEventBus: ApplicationEventBus?
        get() = null

    /**
     * Optional plugin storage factory for persistent data.
     *
     * Returns null if plugin storage is not available.
     * Dynamic plugins can use this to save preferences and state
     * that persists across application restarts.
     */
    val pluginStorageFactory: PluginStorageFactory?
        get() = null

    /**
     * Optional generic dialog provider for common dialogs.
     *
     * Returns null if dialog functionality is not available.
     * Dynamic plugins can use this for text input, confirmation,
     * and choice dialogs with consistent styling.
     */
    val genericDialogProvider: GenericDialogProvider?
        get() = null

    /**
     * Optional navigation resolver provider for PSI-based code navigation.
     *
     * Returns null if navigation services are not available.
     * Dynamic editor plugins can use this to resolve Cmd+Click navigation
     * targets using the host's PSI infrastructure.
     */
    val navigationResolverProvider: NavigationResolverProvider?
        get() = null

    /**
     * Optional semantic token provider for PSI-based semantic highlighting.
     *
     * Returns null if semantic highlighting is not available.
     * Dynamic editor plugins can use this to get semantic tokens
     * (function calls, property accesses, etc.) from the host's PSI analysis.
     */
    val semanticTokenProvider: SemanticTokenProvider?
        get() = null

    /**
     * Optional navigation target provider for cursor positioning after file navigation.
     *
     * Returns null if navigation target functionality is not available.
     * Dynamic editor plugins can use this to listen for navigation events
     * and position their cursor at the target location (e.g., after Cmd+Click
     * go-to-definition or clicking a usage in the usages popup).
     */
    val navigationTargetProvider: NavigationTargetProvider?
        get() = null

    /**
     * Optional directory picker provider for file browser plugins.
     *
     * Returns null if directory picker functionality is not available.
     * Dynamic plugins can use this for the codebase panel's "Open Project" feature.
     */
    val directoryPickerProvider: DirectoryPickerProvider?
        get() = null

    /**
     * Optional project data provider for managing recent projects.
     *
     * Returns null if project management is not available.
     * Dynamic plugins can use this to track and select projects.
     */
    val projectDataProvider: ProjectDataProvider?
        get() = null

    // ============================================================
    // PLUGIN-TO-PLUGIN API ACCESS
    // These methods enable plugins to expose and consume APIs from
    // other plugins, supporting a decentralized plugin architecture.
    // ============================================================

    /**
     * Get a plugin API by its interface type.
     *
     * This allows plugins to access APIs exposed by other plugins without
     * direct compile-time dependencies. The API must have been registered
     * using [registerPluginAPI] by another plugin.
     *
     * Example usage:
     * ```kotlin
     * val editorApi = context.getPluginAPI(EditorPluginAPI::class.java)
     * editorApi?.openFile("/path/to/file.kt")
     * ```
     *
     * @param apiClass The interface class of the API to retrieve
     * @return The API implementation, or null if not registered
     */
    fun <T : Any> getPluginAPI(apiClass: Class<T>): T? = null

    /**
     * Register a plugin API for other plugins to consume.
     *
     * Plugins can expose their capabilities by registering API interfaces.
     * The API will be registered under all interfaces implemented by the
     * provided object.
     *
     * Example usage:
     * ```kotlin
     * class MyEditorAPI : EditorPluginAPI {
     *     override fun openFile(path: String) { ... }
     * }
     *
     * override fun register(context: PluginContext) {
     *     context.registerPluginAPI(MyEditorAPI())
     * }
     * ```
     *
     * @param api The API implementation to register
     */
    fun registerPluginAPI(api: Any) {}
}

/**
 * Interface for plugin modules.
 *
 * Each plugin module should expose an object implementing this interface
 * to allow the host application to register the plugin.
 */
interface Plugin {
    /**
     * Unique identifier for this plugin.
     */
    val pluginId: String

    /**
     * Human-readable name for this plugin.
     */
    val displayName: String

    /**
     * Register this plugin's panels and tab types with the host application.
     *
     * @param context The plugin context providing access to registries
     */
    fun register(context: PluginContext)

    /**
     * Called when the plugin is being disposed.
     * Override to clean up any resources.
     */
    fun dispose() {}
}
