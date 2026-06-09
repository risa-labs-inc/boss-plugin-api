package ai.rever.boss.plugin.api

/**
 * Vendor-neutral analytics contract.
 *
 * This is the *producer-facing* surface of the BOSS analytics pipeline. Any plugin
 * (or the host) emits product events through [PluginContext.track]; a dedicated
 * analytics plugin consumes them and forwards to whatever analytics backend is
 * configured (PostHog today, others later). Producers never depend on a concrete
 * backend — they only depend on this contract.
 *
 * ## How it flows
 * [PluginContext.track] publishes a [CustomPluginEvent] on the [ApplicationEventBus]
 * with an event name prefixed by [ANALYTICS_TRACK_PREFIX]. The analytics plugin
 * subscribes to the bus, recognises that prefix, maps the payload into a canonical
 * analytics event, scrubs PII, and dispatches it to the active sink(s).
 *
 * Riding the event bus (rather than a direct API call) means the typed `track`
 * helper behaves identically whether the analytics plugin runs in-process or
 * out-of-process — the bus is the single transport in both cases.
 */

/** Prefix applied to [CustomPluginEvent.eventName] for events emitted via [PluginContext.track]. */
const val ANALYTICS_TRACK_PREFIX: String = "analytics.track:"

/**
 * Canonical analytics event as seen by producers.
 *
 * @property name dot-namespaced event name, e.g. `"workflow.completed"`.
 * @property properties structured, non-PII properties. Values should be primitives,
 *   strings, or collections thereof. The analytics pipeline runs a mandatory PII
 *   sanitizer over these before anything leaves the device, but producers should
 *   still avoid putting raw PII (emails, file paths, free text) in here.
 * @property timestamp epoch milliseconds when the event occurred.
 */
data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, Any?> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * Well-known event names. Producers are free to use their own dot-namespaced names;
 * these exist to keep common product events consistent across plugins.
 */
object AnalyticsEvents {
    const val WORKFLOW_COMPLETED: String = "workflow.completed"
    const val WORKFLOW_STARTED: String = "workflow.started"
    const val FEATURE_USED: String = "feature.used"
    const val PLUGIN_OPENED: String = "plugin.opened"
}

/**
 * Emit a product analytics event.
 *
 * Safe to call from any plugin: if no [ApplicationEventBus] is available (e.g. a
 * minimal host context) the call is a no-op. The event is delivered to the analytics
 * plugin via the application event bus — the caller does not need a reference to it
 * and does not depend on any analytics backend.
 *
 * ```kotlin
 * context.track(AnalyticsEvents.WORKFLOW_COMPLETED, mapOf(
 *     "workflowType" to "prior-auth",
 *     "durationMs" to elapsed,
 *     "stepCount" to steps,
 * ))
 * ```
 *
 * @param name dot-namespaced event name. It **must be a static, low-cardinality identifier**
 *   (e.g. `"workflow.completed"`) — never interpolate user data, ids, paths, or free text into
 *   it. The analytics pipeline defensively redacts PII-looking substrings from the name, but
 *   high-cardinality names also degrade analytics and should be avoided.
 * @param properties structured, non-PII properties (see [AnalyticsEvent.properties]).
 */
fun PluginContext.track(name: String, properties: Map<String, Any?> = emptyMap()) {
    val bus = applicationEventBus ?: return
    val sourcePluginId = manifest?.pluginId ?: "unknown"
    bus.publish(
        CustomPluginEvent(
            sourcePluginId = sourcePluginId,
            eventName = ANALYTICS_TRACK_PREFIX + name,
            payload = properties,
        )
    )
}
