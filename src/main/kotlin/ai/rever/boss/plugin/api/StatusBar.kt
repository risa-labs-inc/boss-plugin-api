package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable

/**
 * Alignment of a plugin status-bar item within the host bottom bar.
 */
enum class StatusBarAlignment { LEFT, RIGHT }

/**
 * A widget contributed to the host status (bottom) bar.
 *
 * Content is wrapped in the host's plugin extension boundary — a crash
 * attributed to the owning plugin collapses the widget to a compact error
 * marker instead of taking down the status bar. Keep it a small, single-row
 * composable (text, icon + text, tiny indicator).
 *
 * Register via [PluginContext.registerStatusBarItem]; the host unregisters
 * automatically on disable/unload.
 */
interface StatusBarItemProvider {
    /** Unique id, by convention `"<pluginId>:<name>"`. Re-registration replaces. */
    val itemId: String

    val alignment: StatusBarAlignment
        get() = StatusBarAlignment.RIGHT

    /** Items are sorted ascending within their alignment group. */
    val order: Int
        get() = 100

    /** RBAC: the user must hold ALL of these permissions to see the item. */
    val requiredPermissions: Set<String>
        get() = emptySet()

    @Composable
    fun Content()
}
