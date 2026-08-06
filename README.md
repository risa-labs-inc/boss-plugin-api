# BOSS Plugin API

The SDK every BOSS plugin compiles against, and the bundled system plugin that serves it at
runtime.

A plugin depends on this as `compileOnly` and never bundles it. The host owns the copy that is
actually loaded, which is what keeps a plugin built against an older SDK working on a newer
BOSS.

## What it provides

- **`PluginContext`** - roughly fifty host providers covering git, filesystem, Supabase, auth,
  browser, LLM, clipboard, secrets, projects, tabs, notifications and cache. **Every one of
  them is nullable.** Null-check and degrade; never crash.
- **The plugin-to-plugin API registry** - `registerPluginAPI(api)` and
  `getPluginAPI(apiClass)`. Both default to a no-op and null respectively, so a plugin calling
  them still loads on a host that predates them.
- **The MCP extension point** - `McpToolProvider`, `McpToolDefinition`, `McpToolArgs`,
  `McpToolResult`, and `registerMcpToolProvider` / `unregisterMcpToolProvider`. This module
  defines the contract; it contributes no tools itself.
- **Panel and tab registration** - `PanelInfo`, `PanelId`, `Panel.left/right.top.bottom`,
  `PanelRegistry`, `TabRegistry`.
- **Shared UI** - `BossTheme`, `BossColors`, `BossDialog`, `BossOverlayHost`, panel scrollbars
  and the `Boss*` component set, so a plugin's panel matches the host and re-skins with it.

## Feature detection

`BossApiRuntime.version` and `BossApiRuntime.isAtLeast(...)` read the `boss.api.version` system
property the host publishes at startup, so a plugin can check the SDK level at runtime instead
of discovering it as a `NoSuchMethodError`.

It is deliberately a standalone object. New *members* on host-compiled types get shadowed by
the host's older copy, while new *types* are served from this jar - so version detection has to
live on a type the host does not also define.

## Using it from a plugin

```kotlin
compileOnly(files("../boss-plugin-api/build/libs/boss-plugin-api-<version>.jar"))
```

The manifest declares `sharedPackages: ["ai.rever.boss.plugin.api"]`, so the host classloader
serves that package to every plugin and the host's copy always wins at runtime. Bundling this
jar into a plugin will break it.

## Build

```bash
./gradlew buildPluginJar    # build/libs/boss-plugin-api-*.jar
```

This plugin ships bundled with BossConsole and needs no manual installation. For local
development:

```bash
cp build/libs/boss-plugin-api-*.jar \
   ~/Development/Boss/BossConsole/composeApp/build/bundled-plugins/
```

## Notes

- System plugin: `loadPriority: 0`, so it loads before everything else, and `canUnload: false`.
- Updating it triggers a process-wide API-layer swap rather than an ordinary hot reload.
- The plugin class itself is intentionally a no-op. Providers are still supplied by the host's
  `DefaultPlugin`; this module's value is the API surface, not its `register()`.

See [AGENTS.md](AGENTS.md) for architecture and conventions.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

Copyright 2025-2026 Risa Labs Inc.
