package ai.rever.boss.plugin.api

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A single menu item contributed to the shared top bar of sidebar panels.
 *
 * Rendered by the host between the built-in items (Restart Panel, Reload
 * Plugin, …) and Minimize, in both the kebab ("…") menu and the right-click
 * menu of the panel top bar.
 *
 * All fields are serializable primitives (plus an optional icon) so a future
 * out-of-process proxy stays mechanical.
 */
data class PanelMenuItem(
    /** Unique within the owning [PanelMenuContribution]. */
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    /** Plugin items are sorted ascending by order. Default 100. */
    val order: Int = 100,
    val enabled: Boolean = true,
    /** RBAC: the user must hold ALL of these permissions to see the item. */
    val requiredPermissions: Set<String> = emptySet(),
    /** RBAC: only admins see this item. */
    val requiresAdmin: Boolean = false,
    /**
     * Forward-evolution escape hatch: extra key/value data interpreted by
     * SDK-side readers only — the host never inspects it, so new keys need
     * no host release.
     */
    val extras: Map<String, String> = emptyMap(),
)

/**
 * A plugin's contribution of menu items to sidebar panel top bars.
 *
 * Cross-plugin story: plugin A can contribute items to plugin B's panel by
 * naming B's panel id in [targetPanels], or to every panel with null.
 *
 * Register via [PluginContext.registerPanelMenuContribution]; the host
 * unregisters automatically when the plugin is disabled or unloaded.
 */
interface PanelMenuContribution {
    /** Unique id, by convention `"<pluginId>:<name>"`. Re-registration replaces. */
    val contributionId: String

    /**
     * Panel id strings this contribution targets ([PanelId.panelId] values).
     * Null means ALL panels.
     */
    val targetPanels: Set<String>?
        get() = null

    /**
     * Items to show for [panelId]. May be queried whenever a panel's top bar
     * (re)composes — not only when a menu opens — so it MUST be cheap,
     * non-blocking, and side-effect free. Registrations are re-queried on
     * plugin lifecycle and role changes; re-register the contribution to
     * change its item set outside those events.
     */
    fun items(panelId: PanelId): List<PanelMenuItem>

    /**
     * Called when the user clicks a contributed item. Arguments are the
     * primitives needed to route the action; exceptions are caught and logged
     * by the host, never crashing the panel chrome.
     */
    fun onItemClick(panelId: PanelId, itemId: String, windowId: String?)
}
