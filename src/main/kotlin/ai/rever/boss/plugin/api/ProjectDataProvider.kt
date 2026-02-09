package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Data class representing a project.
 */
@Serializable
data class ProjectData(
    val name: String,
    val path: String,
    val lastOpened: Long = 0L
)

/**
 * Provider interface for project management operations.
 *
 * This interface abstracts project data management to allow
 * the CodeBase panel to be extracted to a separate module.
 */
interface ProjectDataProvider {
    /**
     * Recent projects list.
     */
    val recentProjects: StateFlow<List<ProjectData>>

    /**
     * Update recent projects list with a new project.
     *
     * @param project The project to add/update
     */
    fun updateRecentProjects(project: ProjectData)

    /**
     * Remove a project from the recent projects list.
     *
     * @param projectPath The project path to remove
     */
    fun removeRecentProject(projectPath: String)

    /**
     * Select a project in the current window.
     * This sets the window's current working directory.
     *
     * @param project The project to select
     */
    fun selectProject(project: ProjectData)
}
