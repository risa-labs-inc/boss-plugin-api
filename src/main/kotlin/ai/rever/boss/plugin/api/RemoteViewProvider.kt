package ai.rever.boss.plugin.api

/**
 * Lets a plugin declare that BOSS is currently being shown to a remote viewer.
 *
 * The host counts open handles. While at least one is open it avoids UI that only the local
 * machine can see or operate, such as native OS menus, which a viewer painting BOSS's own
 * windows never receives and cannot click. Hold a handle for as long as the remote view is
 * live and close it when the last viewer goes away.
 *
 * Gate on a null provider, which is what a host without this surface returns:
 * ```kotlin
 * val handle = context.remoteViewProvider?.beginRemoteView("fluck-boss-view")
 * // ...view is live...
 * handle?.close()
 * ```
 * A call on an older host still compiles against the new api jar but gets null from the
 * [PluginContext.remoteViewProvider] default, so there is nothing to undo.
 */
interface RemoteViewProvider {
    /**
     * Marks one remote view as live and returns its handle.
     *
     * [reason] is a short label for diagnostics. Closing the handle ends that view; close is
     * idempotent, and each call here is counted separately.
     */
    fun beginRemoteView(reason: String): AutoCloseable
}
