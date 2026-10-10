package ai.rever.boss.plugin.api

/**
 * Thrown by Plugin.dispose() when owned work has not drained safely. Supporting hosts retain the
 * plugin and its active classloader, report unload failure, and permit a later retry. Use this
 * rather than returning while plugin code is still executing. Requires the supporting host;
 * ordinary disposal failures retain their existing best-effort behavior.
 */
@HostImplemented
class PluginUnloadDeferredException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
