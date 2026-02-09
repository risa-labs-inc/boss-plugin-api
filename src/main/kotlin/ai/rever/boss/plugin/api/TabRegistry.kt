package ai.rever.boss.plugin.api

import androidx.compose.runtime.mutableStateMapOf
import com.arkivanov.decompose.ComponentContext

open class TabRegistry {
    // Map of tab type handlers
    private val tabHandlers = mutableStateMapOf<TabTypeId, TabTypeInfo>()

    // Map of tab component factories by tab type
    private val tabFactories = mutableStateMapOf<TabTypeId, (TabInfo, ComponentContext) -> TabComponentWithUI>()

    // Callbacks to notify when tab types are registered/unregistered
    private val changeListeners = mutableListOf<() -> Unit>()

    // Callbacks to notify when a specific tab type is unregistered (for closing open tabs)
    private val unregisterListeners = mutableListOf<(TabTypeId) -> Unit>()

    // Register a tab type from a plugin
    open fun registerTabType(
        content: TabTypeInfo,
        factory: (TabInfo, ComponentContext) -> TabComponentWithUI
    ) {
        tabHandlers[content.typeId] = content
        tabFactories[content.typeId] = factory
        notifyChange()
    }

    /**
     * Unregister a tab type.
     *
     * This removes the tab type and its factory from the registry.
     * Used when dynamically unloading plugins.
     *
     * @param typeId The ID of the tab type to unregister
     */
    open fun unregisterTabType(typeId: TabTypeId) {
        tabHandlers.remove(typeId)
        tabFactories.remove(typeId)
        notifyUnregister(typeId)
        notifyChange()
    }

    /**
     * Add a listener that will be called when tab types are registered or unregistered.
     */
    open fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    /**
     * Remove a change listener.
     */
    open fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }

    /**
     * Add a listener that will be called when a tab type is unregistered.
     * The listener receives the typeId of the unregistered tab type.
     * Use this to close all open tabs of that type.
     */
    open fun addUnregisterListener(listener: (TabTypeId) -> Unit) {
        unregisterListeners.add(listener)
    }

    /**
     * Remove an unregister listener.
     */
    open fun removeUnregisterListener(listener: (TabTypeId) -> Unit) {
        unregisterListeners.remove(listener)
    }

    private fun notifyChange() {
        changeListeners.forEach { it() }
    }

    private fun notifyUnregister(typeId: TabTypeId) {
        unregisterListeners.forEach { it(typeId) }
    }

    // Create a component for a tab configuration
    open fun createTabComponent(config: TabInfo, componentContext: ComponentContext): TabComponentWithUI? {
        val factory = tabFactories[config.typeId]
        return factory?.invoke(config, componentContext)
    }

    /**
     * Get the tab type info for a specific tab type ID.
     */
    open fun getTabTypeInfo(typeId: TabTypeId): TabTypeInfo? {
        return tabHandlers[typeId]
    }

    /**
     * Get all registered tab types.
     */
    open fun getAllTabTypes(): List<TabTypeInfo> = tabHandlers.values.toList()

    /**
     * Check if a tab type is registered.
     */
    open fun isRegistered(typeId: TabTypeId): Boolean = tabHandlers.containsKey(typeId)
}
