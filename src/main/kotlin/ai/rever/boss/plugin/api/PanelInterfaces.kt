package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.arkivanov.decompose.ComponentContext

data class PanelId(
    val panelId: String,
    val defaultOrder: Int,
    val pluginId: String = "ai.rever.boss"
)

/**
 * Represents a single draggable item in the sidebar.
 */
data class SidebarItem(
    val pluginContentId: PanelId,
    val icon: ImageVector,
    val label: String,
    val onClick: (() -> Unit)? = null,
) {
    val id: String get() = pluginContentId.panelId
}

interface PanelInfo {
    val id: PanelId
    val displayName: String
    val icon: ImageVector
    val defaultSlotPosition: Panel

    val sidebarItem get() = SidebarItem(id, icon, displayName)
}

interface PanelComponentWithUI: ComponentContext, PanelLifecycle {
    val panelInfo: PanelInfo

    @Composable
    fun Content()

    /**
     * Default implementation of resetPanelId from PanelLifecycle.
     * Returns the panel's ID from panelInfo for component reset operations.
     */
    override val resetPanelId: PanelId
        get() = panelInfo.id
}
