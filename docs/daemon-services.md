# Plugin background services

A supporting BOSS host exposes `PluginContext.daemonServiceProvider`. The provider is
nullable and bound to the registering plugin's identity and verified JAR. API 1.0.99
and a host containing the provider are required; this change targets BOSS 9.5.44.
Verify those release numbers before publishing a consumer.

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

val connection = context.daemonServiceProvider?.connect(
    serviceId = "background-worker",
    entryPoint = BackgroundWorker::class.java.name,
)
check(connection?.endpoints?.get("protocol") == "1")
connection.request("status")
```

The host serializes each service's start/request/stop operations. Requests must return
promptly: start long tasks in the service scope and expose job IDs, events and cancellation
through the plugin protocol. The connection is a control handle, not ownership of the worker.
Drop UI handles freely; call `connection.stop()` only for an explicit stop/reset action.

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

Use the service scope for every coroutine. External child processes, threads and servers
must also be stopped and joined before `stop()` returns. A failed drain retains the loader;
UI unload must never close a loader that is still executing background code. The daemon
uses BOSS's packaged runtime, a profile-specific login registration, owner-private state,
and an authenticated loopback control channel. It is a trusted plugin process, not a sandbox.

`configuration` and endpoint metadata are persisted/public control data: **never put
credentials in them**. Keep secrets out of logs and error messages. A worker receives no
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
