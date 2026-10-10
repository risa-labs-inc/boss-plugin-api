# Plugin background services

A supporting BOSS host exposes `PluginContext.daemonServiceProvider`. The provider is
nullable and bound to the registering plugin's identity and verified JAR. API 1.0.99
and a host containing the provider are required; this change targets BOSS 9.5.44.
Consumers must declare both `minApiVersion: 1.0.99` and the actual supporting
`minBossVersion`. A nullable getter does not bypass binary validation on older hosts:
referencing this new host-compiled member can reject the entire plugin before registration.
Verify the target release numbers before publishing a consumer.

Implement a public no-argument `DaemonService` in the plugin JAR:

```kotlin
class BackgroundWorker : DaemonService {
    override suspend fun start(
        context: DaemonServiceContext,
        configuration: Map<String, String>,
    ): Map<String, String> {
        // Restore durable jobs from context.dataDirectory.
        // Launch owned jobs in context.scope; return protocol metadata.
        return mapOf("protocol" to "1")
    }

    override suspend fun request(method: String, payload: String): String {
        // Validate and dispatch the plugin's own versioned protocol.
        return ""
    }

    override suspend fun stop() {
        // Stop external processes, servers and executors, and await their drain.
    }
}

context.pluginScope.launch {
    val provider = context.daemonServiceProvider ?: return@launch // show unavailable/fallback UI
    try {
        val connection = provider.connect("background-worker", BackgroundWorker::class.java.name)
        if (connection.endpoints["protocol"] != "1") return@launch // show incompatible-worker UI
        connection.request("status")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        // Show connection failure/retry UI. Do not log payloads, tokens or exception messages.
    }
}
```

The host serializes each service's start/request/stop operations. Requests must return
promptly: start long tasks in the service scope and expose job IDs, events and cancellation
through the plugin protocol. The connection is a control handle, not ownership of the worker.
Drop UI handles freely; call `connection.stop()` only for an explicit stop/reset action.

This control API intentionally supplies no push stream: workers expose their own authenticated
HTTP/WebSocket transports through the versioned protocol, as the terminal adapter does.
Use short request/response calls for control and event replay cursors; never block a request
waiting indefinitely for an event. A reconnect to a running ID keeps its original entry point,
configuration and code. Changed connection arguments take effect only after explicit stop/start.

The host adds `boss.service.instanceId` to endpoint metadata. It stays stable on reconnect and
changes after worker restart. Store durable conversation/job IDs separately. Treat a new worker
instance as a recovery/replay boundary; it does not mean a task executed exactly once.
Every control handle is bound to that instance: requests through an old handle reject after
stop/restart. `stop()` is idempotent and never stops or unregisters a replacement. Reconnect
explicitly to obtain the new handle. Connect is a creating operation, not a read-only lookup;
this version has no lookup-only API. Negotiate effective plugin configuration/version in the
worker's own status protocol rather than assuming new connect arguments changed a live worker.

Connection calls can throw `Exception`: cancellation propagates, local transport errors use
I/O exceptions, and rejected remote operations use `IllegalStateException` containing only an
error type. Plugin protocols should return their own structured error codes for actionable
service failures. Do not catch `Throwable` in UI connection code. The current host limits an
encoded control message to 1 MiB, socket connection to 1.5 seconds, response reads to 30 seconds,
and on-demand daemon readiness to 15 seconds. A client timeout does not cancel server-side work.

The host never logs control payloads or transport auth tokens. Configuration and restart descriptors
are persisted; request payloads are not. Return short-lived HTTP/WebSocket auth tokens through
`request()` (as the terminal adapter does), never through `start()` endpoint metadata. They cross
the authenticated loopback control channel, but are not automatically refreshed or available after
process restart. Endpoint metadata contains only non-secret protocol/endpoint information.

Service IDs are opaque, nonempty strings up to 512 characters. Storage names use hashes of the
plugin/service pair, never service IDs as paths. Entry points must resolve from the plugin's
own immutable worker JAR and implement `DaemonService`, before any constructor runs.

Stop is serialized behind admitted requests and begins with the service scope still active.
After `stop()` returns or throws, the host cancels and joins the scope. There is no forced
worker-drain deadline: a stuck request/stop can delay disable or daemon quit indefinitely.
The host retains executing loaders rather than unloading code underneath them. Long tasks
must run in the service scope, expose cooperative cancellation and keep control requests short.

| Event | Worker behavior |
| --- | --- |
| Hide/close BOSS window, UI plugin reload | Worker continues in the daemon |
| Reconnect with the same plugin/service ID | Same worker and original code |
| Plugin update | Running worker retains an immutable JAR snapshot; negotiate its protocol |
| Explicit service stop | Drain work, close its loader, remove login registration |
| Disable/remove plugin | Revoke UI providers and stop/remove that plugin's workers |
| Daemon quit | Drain workers; retain restart registrations |
| Login / daemon restart | Start registered services; plugin restores its own durable jobs |
| OS reboot / sleep / offline | No promise of uninterrupted execution or network access |

The context scope uses a `SupervisorJob` and `Dispatchers.IO`: a failed child does not cancel its
siblings. The data directory exists before `start()`, is private to the OS user, and remains on disk
after explicit stop or plugin removal; plugins own their data retention policy. Different plugins
share one JVM and OS identity, so these directories are not a security boundary between plugins.

Use the service scope for every coroutine. External child processes, threads and servers
must also be stopped and joined before `stop()` returns. A failed drain retains the loader;
UI unload must never close a loader that is still executing background code. UI plugins can
throw `PluginUnloadDeferredException` from `dispose()` when their bounded cleanup cannot finish;
the supporting host reports unload failure and retains the active plugin loader for retry.
Ordinary disposal exceptions keep their existing best-effort behavior, so use this explicit
signal for incomplete drains and declare the supporting host version. The daemon
uses BOSS's packaged runtime, a profile-specific login registration, owner-private state,
and an authenticated loopback control channel. It is a trusted plugin process, not a sandbox.

`configuration` is persisted and endpoint metadata is exposed through control handles: **never put
credentials in either**. Keep secrets out of logs and error messages. A worker receives no
window, Compose state, `PluginContext`, or UI-owned credential provider. Bundle its headless
dependencies. UI-dependent approvals must pause until an authorized client can answer.

## Fluck web access

The intended Fluck behavior is that its agent remains reachable from fluck.ai after BOSS's
windows close, provided the daemon and machine are running, awake and online. This API is
its foundation; it does not migrate Fluck or deploy/configure that website.

Move these owners into a future Fluck worker together:

- Conversations, task execution, durable job state, cancellation, and event replay.
- The web server, authenticated browser sessions, tunnel, portal presence and reconnect flow.
- Required AI Gateway/CLI execution and headless provider capabilities.
- A host-owned background credential broker with one refresh owner, sign-out/revocation,
  account switching and owner-checked access. Do not copy rotating refresh tokens into two
  independently refreshing UI/daemon sessions.
- Approvals and task controls that work from an authenticated browser while no desktop UI exists.

Reuse Fluck's existing web chat, portal tickets and tunnel code where appropriate. Both
BOSS and the browser become clients of the same worker. Validate window close/reopen,
website reconnect, output replay, cancellation, account changes, token expiry, tunnel restart,
plugin upgrade protocol compatibility, and daemon restart recovery before claiming web parity.
Desktop window viewing/automation still requires a live desktop UI.
