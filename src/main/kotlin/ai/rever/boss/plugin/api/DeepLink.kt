package ai.rever.boss.plugin.api

/**
 * Handler for plugin-addressed deep-link actions.
 *
 * The host routes `boss://plugin?id=<handlerId>&action=<action>&<args…>` to
 * the handler registered under `<handlerId>`; remaining query parameters are
 * passed URL-decoded in [handle]'s params map. Links WITHOUT an `action`
 * parameter keep the existing behavior (open the panel with that id).
 *
 * Deep links are external input — handlers must validate every parameter.
 *
 * Register via [PluginContext.registerDeepLinkActionHandler]; the host
 * unregisters automatically on disable/unload.
 */
interface DeepLinkActionHandler {
    /** The id matched against the link's `id` parameter; by convention the pluginId. */
    val handlerId: String

    /**
     * Handle [action] with [params] (query parameters minus `id`/`action`,
     * URL-decoded). Return true if handled; false logs a warning.
     */
    fun handle(action: String, params: Map<String, String>): Boolean
}
