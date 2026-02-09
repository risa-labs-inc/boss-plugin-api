package ai.rever.boss.plugin.api

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import androidx.compose.runtime.mutableStateMapOf
import com.arkivanov.decompose.ComponentContext

open class PanelRegistry {
    private val contentProviders = mutableStateMapOf<PanelId, (ComponentContext, PanelInfo) -> PanelComponentWithUI>()
    private val availablePanelInfo = mutableStateMapOf<PanelId, PanelInfo>()

    // Callbacks to notify when panels are registered/unregistered
    private val changeListeners = mutableListOf<() -> Unit>()

    open fun registerPanel(
        content: PanelInfo,
        factory: (ComponentContext, PanelInfo) -> PanelComponentWithUI
    ) {
        contentProviders[content.id] = factory
        availablePanelInfo[content.id] = content
        notifyChange()
    }

    open fun unregisterPanel(id: PanelId) {
        contentProviders.remove(id)
        availablePanelInfo.remove(id)
        notifyChange()
    }

    open fun addChangeListener(listener: () -> Unit) {
        changeListeners.add(listener)
    }

    open fun removeChangeListener(listener: () -> Unit) {
        changeListeners.remove(listener)
    }

    private fun notifyChange() {
        changeListeners.forEach { it() }
    }

    open fun createComponent(id: PanelId, componentContext: ComponentContext): PanelComponentWithUI? {
        return getPanelContent(id)?.let { contentProviders[id]?.invoke(componentContext, it) }
    }

    open fun getPanelContent(id: PanelId): PanelInfo? {
        return availablePanelInfo[id]
    }

    open fun getAllPanels(): List<PanelInfo> = availablePanelInfo.values.sortedBy { it.id.defaultOrder }

    open fun getDefaultSidebarMap(): Map<Panel, List<SidebarItem>> =
        mapOf<Panel, MutableList<SidebarItem>>(
            left.top.top to mutableListOf<SidebarItem>(),
            left.top.bottom to mutableListOf<SidebarItem>(),
            left.bottom to mutableListOf<SidebarItem>(),
            right.top.top to mutableListOf<SidebarItem>(),
            right.top.bottom to mutableListOf<SidebarItem>(),
        ).apply {
            getAllPanels().forEach {
                get(it.defaultSlotPosition)?.add(it.sidebarItem)
            }
        }
}
