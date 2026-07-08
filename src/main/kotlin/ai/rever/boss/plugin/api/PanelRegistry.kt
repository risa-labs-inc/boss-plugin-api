package ai.rever.boss.plugin.api

import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.Panel.Companion.right
import ai.rever.boss.plugin.api.Panel.Companion.top
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import androidx.compose.runtime.mutableStateMapOf
import com.arkivanov.decompose.ComponentContext

private val panelRegistryLogger = BossLogger.forComponent("PanelRegistry")

open class PanelRegistry {
    private val contentProviders = mutableStateMapOf<PanelId, (ComponentContext, PanelInfo) -> PanelComponentWithUI>()
    private val availablePanelInfo = mutableStateMapOf<PanelId, PanelInfo>()

    // Callbacks to notify when panels are registered/unregistered
    private val changeListeners = mutableListOf<() -> Unit>()

    open fun registerPanel(
        content: PanelInfo,
        factory: (ComponentContext, PanelInfo) -> PanelComponentWithUI
    ) {
        // `__` is reserved for internal sentinel ids (e.g. the
        // SidebarCustomizeMenu's synthetic drag-overlay item). Refuse to
        // register such a panel here at the boundary instead of trusting
        // plugin authors not to collide. We log + skip rather than throw:
        // a single misbehaving plugin shouldn't take down the entire
        // panel-registration pipeline, but the offending panel must not
        // shadow the internal sentinel.
        if (content.id.panelId.startsWith(RESERVED_PANEL_ID_PREFIX)) {
            panelRegistryLogger.warn(LogCategory.SYSTEM, "Refusing to register panel with reserved-prefix id", mapOf(
                "panelId" to content.id.panelId,
                "pluginId" to content.id.pluginId,
                "reservedPrefix" to RESERVED_PANEL_ID_PREFIX
            ))
            return
        }
        contentProviders[content.id] = factory
        availablePanelInfo[content.id] = content
        notifyChange()
    }

    companion object {
        /** Internal-only prefix for synthetic [PanelId] / [SidebarItem.id] values. */
        const val RESERVED_PANEL_ID_PREFIX = "__"
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
