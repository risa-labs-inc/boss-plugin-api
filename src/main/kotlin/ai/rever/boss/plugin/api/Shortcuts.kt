package ai.rever.boss.plugin.api

/**
 * A key chord, expressed in the host keymap's vocabulary.
 *
 * Plugin chords MUST include at least one of Cmd/Ctrl/Alt: the host's
 * keyboard interceptor ignores modifier-less and Shift-only events (they
 * belong to text entry), so such a chord can never fire. The registry
 * rejects a defaultBinding without one of those modifiers (warned, treated
 * as unbound — the user can still rebind to a reachable chord in Settings).
 *
 * @param key The main key, e.g. "K", "F5", "Enter".
 * @param modifiers Modifier names, e.g. ["Cmd", "Shift"]. Platform-mapped by
 *   the host (Cmd = Ctrl on Windows/Linux presets).
 */
data class KeyChordSpec(
    val key: String,
    val modifiers: Set<String> = emptySet(),
)

/**
 * A global keyboard action contributed by a plugin.
 *
 * v1 scope: GLOBAL context only — plugin shortcuts fire regardless of which
 * panel/tab has focus, and never participate in editor/browser-scoped
 * contexts.
 *
 * Binding resolution: a user override stored under [actionId] in the keymap
 * settings wins; otherwise [defaultBinding] applies. Host bindings always win
 * conflicts — a colliding default is registered unbound (and logged) until
 * the user rebinds it in Settings → Shortcuts.
 */
data class PluginShortcutSpec(
    /**
     * MUST be `"plugin.<pluginId>.<name>"` — the host rejects non-conforming
     * ids to guard against collisions with built-in keymap actions.
     */
    val actionId: String,
    val displayName: String,
    val description: String = "",
    /** Null = no default; the user must bind it manually. */
    val defaultBinding: KeyChordSpec? = null,
)

/**
 * Provider of global keyboard actions.
 *
 * [shortcuts] is snapshotted at registration (it is not re-queried), so the
 * set of actions is fixed per registration; re-register to change it.
 *
 * Register via [PluginContext.registerShortcutActionProvider]; the host
 * unregisters automatically on disable/unload, after which the chords are
 * inert. User rebinds persist in the user's keymap file across plugin
 * reloads.
 */
interface ShortcutActionProvider {
    /** Unique id, by convention the pluginId. */
    val providerId: String

    fun shortcuts(): List<PluginShortcutSpec>

    /** Called on the UI thread when a bound chord fires. */
    fun onAction(actionId: String, windowId: String?)
}
