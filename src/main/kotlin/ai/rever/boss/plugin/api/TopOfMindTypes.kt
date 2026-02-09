package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

/**
 * Hierarchical structure for workspace tab sections.
 * Used by TopOfMind panel to organize tabs within workspaces.
 */
@Serializable
sealed class WorkspaceTabStructure {
    @Serializable
    data class TabItem(
        val activeTab: ActiveTabData
    ) : WorkspaceTabStructure()

    @Serializable
    data class SplitSection(
        val sectionName: String,  // "Left", "Right", "Top", "Bottom"
        val children: List<WorkspaceTabStructure>,
        val level: Int = 0
    ) : WorkspaceTabStructure()
}

/**
 * Simplified tree structure for organizing tabs (workspace level only).
 * Used by TopOfMind panel for displaying the tab hierarchy.
 */
@Serializable
sealed class TabTreeNode {
    abstract val id: String
    abstract val name: String
    abstract val level: Int

    @Serializable
    data class WorkspaceNode(
        override val id: String,
        override val name: String,
        override val level: Int = 0,
        val workspaceId: String,
        var isExpanded: Boolean = true,
        val tabStructure: List<WorkspaceTabStructure> = emptyList()
    ) : TabTreeNode()

    @Serializable
    data class TabNode(
        override val id: String,
        override val name: String,
        override val level: Int,
        val activeTab: ActiveTabData
    ) : TabTreeNode()
}

/**
 * Breadcrumb item types for navigation display.
 */
@Serializable
enum class BreadcrumbType {
    WORKSPACE,
    PANEL,
    TAB,
    SEPARATOR
}

/**
 * Data class for breadcrumb navigation items.
 */
data class BreadcrumbItem(
    val text: String,
    val type: BreadcrumbType,
    val clickable: Boolean = true,
    val onClick: (() -> Unit)? = null
)
