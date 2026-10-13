package ai.rever.boss.plugin.api

/**
 * Lets a plugin declare that BOSS is currently being shown to a remote viewer.
 *
 * The host counts open handles. While at least one is open it avoids UI that only the local
 * machine can see or operate, such as native OS menus, which a viewer painting BOSS's own
 * windows never receives and cannot click. Hold a handle for as long as the remote view is
 * live and close it when the last viewer goes away:
 * ```kotlin
 * val handle = context.remoteViewProvider?.beginRemoteView("fluck-boss-view")
 * // ...view is live...
 * handle?.close()
 * ```
 * Null means the host has this surface but wired no provider. It does NOT cover an older host:
 * see [PluginContext.remoteViewProvider] for the `minBossVersion` floor.
 *
 * Contract: [beginRemoteView] and `close()` may be called from any thread, never throw, and
 * closing one handle more than once never affects another handle's count. The host releases a
 * plugin's open handles when that plugin is disposed (including an api-jar hot swap); still close
 * them in `dispose()`.
 */
@HostImplemented
interface RemoteViewProvider {
    /**
     * Marks one remote view as live and returns its handle.
     *
     * [reason] is a short label for diagnostics only. Closing the handle ends that view; each
     * call here is counted separately.
     */
    fun beginRemoteView(reason: String): AutoCloseable
}
