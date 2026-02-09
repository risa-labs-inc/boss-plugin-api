package ai.rever.boss.plugin.api

/**
 * Lifecycle interface for panel components.
 *
 * This interface defines the lifecycle hooks that panel components can implement
 * to manage their initialization, reset, and disposal phases.
 *
 * Lifecycle Phases:
 * 1. CREATED - Component instantiated (handled by Decompose ComponentContext)
 * 2. INITIALIZED - Custom initialization after component creation (onInitialized)
 * 3. ACTIVE - Component is running and rendering UI
 * 4. RESET - Component cleanup and recreation (onBeforeReset)
 * 5. DISPOSED - Component destroyed (handled by Decompose lifecycle.doOnDestroy)
 *
 * Reset Behavior:
 * By default, reset triggers complete component recreation via PanelComponentStore.
 * This ensures a clean slate with all state reset to initial values.
 * Components can override onBeforeReset() to perform custom cleanup before recreation.
 *
 * Example Implementation:
 * ```kotlin
 * class MyPanelComponent(
 *     ctx: ComponentContext,
 *     override val panelInfo: PanelInfo
 * ) : PanelComponentWithUI, ComponentContext by ctx {
 *
 *     private val viewModel = MyViewModel()
 *
 *     init {
 *         // Standard Decompose disposal
 *         lifecycle.doOnDestroy {
 *             viewModel.dispose()
 *         }
 *     }
 *
 *     override fun onInitialized() {
 *         logger.info("Panel initialized")
 *         viewModel.loadData()
 *     }
 *
 *     override fun onBeforeReset() {
 *         logger.info("Preparing to reset")
 *         viewModel.clearData()
 *     }
 *
 *     @Composable
 *     override fun Content() {
 *         MyPanelContent(viewModel)
 *     }
 * }
 * ```
 */
interface PanelLifecycle {
    /**
     * Called once when the panel component is first initialized.
     *
     * Use this lifecycle hook for setup operations that should happen once per
     * component creation, such as:
     * - Loading initial data from database
     * - Initializing ViewModels
     * - Setting up subscriptions or observers
     * - Starting background tasks
     *
     * This method is called AFTER the component's constructor and init blocks
     * have executed, but BEFORE the first composition of Content().
     *
     * Default implementation is empty - override to add custom initialization logic.
     */
    fun onInitialized() {
        // Default empty implementation
        // Panels can override to add custom initialization logic
    }

    /**
     * Called immediately before the panel component is reset.
     *
     * Use this lifecycle hook for cleanup operations that should happen before
     * component recreation, such as:
     * - Clearing sensitive data from memory
     * - Cancelling pending operations
     * - Saving temporary state if needed
     * - Logging reset events for analytics
     *
     * After this method completes, the component will be removed from
     * PanelComponentStore and a new instance will be created.
     *
     * Note: This is NOT a replacement for proper disposal via Decompose's
     * lifecycle.doOnDestroy(). Use this only for reset-specific cleanup.
     *
     * Default implementation is empty - override to add custom cleanup logic.
     */
    fun onBeforeReset() {
        // Default empty implementation
        // Panels can override to add custom cleanup before reset
    }

    /**
     * Get the panel ID for this component.
     *
     * This is used internally by PanelComponentStore to identify and reset
     * the correct component instance.
     *
     * Default implementation returns panelInfo.id, which works for all
     * standard panel components. Only override if you have a custom panel
     * ID management strategy.
     */
    val resetPanelId: PanelId
        get() = throw NotImplementedError(
            "resetPanelId must be implemented. " +
            "For PanelComponentWithUI, this is automatically implemented as panelInfo.id"
        )
}
