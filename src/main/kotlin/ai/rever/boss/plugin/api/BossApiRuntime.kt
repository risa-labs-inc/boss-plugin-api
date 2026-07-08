package ai.rever.boss.plugin.api

/**
 * Runtime information about the installed boss-plugin-api layer.
 *
 * The host resolves the newest installed boss-plugin-api jar at startup and
 * publishes its version through the `boss.api.version` system property before
 * any plugin loads. Plugins can feature-detect SDK-level capabilities with
 * [isAtLeast] instead of failing at class-load time.
 *
 * This is deliberately a standalone type rather than a member of
 * [PluginContext]: new members on host-compiled types are shadowed by the
 * host's older copy, while new types are served from the api jar itself.
 */
object BossApiRuntime {

    /**
     * The version of the boss-plugin-api layer the host resolved at startup
     * (e.g. "1.0.62"), or "0.0.0" when the host predates the runtime API layer.
     */
    val version: String
        get() = System.getProperty("boss.api.version")?.takeIf { it.isNotBlank() } ?: "0.0.0"

    /**
     * Whether the runtime API level is at least [required].
     *
     * Fails open (returns true) when either version is unparseable, matching
     * the loader's minBossVersion behavior — gates should block only on a
     * definite mismatch.
     */
    fun isAtLeast(required: String): Boolean {
        val current = Version.parse(version) ?: return true
        val needed = Version.parse(required) ?: return true
        return current >= needed
    }
}
