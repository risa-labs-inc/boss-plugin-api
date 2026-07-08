# CLAUDE.md

## Project Overview

**BOSS Plugin API** (`ai.rever.boss.plugin.api`) is a dynamic plugin for the BOSS desktop application.

Core provider APIs for BOSS plugins. This plugin provides the foundational APIs that other plugins depend on.

- **Plugin ID**: `ai.rever.boss.plugin.api`
- **Main Class**: `ai.rever.boss.plugin.bundled.api.BossPluginAPIPlugin`
- **API Version**: see `build.gradle.kts` (single source of truth; the changelog comment above `version` documents each release)

## Essential Commands

```bash
./gradlew buildPluginJar    # Build plugin JAR (output: build/libs/)
./gradlew build              # Full build
./gradlew processResources   # Process resources (syncs version)
```

## Workflow Rules

- Do NOT run the BOSS application to test. The user will test manually.
- After building, copy JAR to `~/.boss/plugins/` for local testing.

## Architecture

### Plugin Structure
```
src/main/kotlin/   → Plugin source code (package: ai.rever.boss.plugin.dynamic.*)
src/main/resources/META-INF/boss-plugin/plugin.json → Plugin manifest
build.gradle.kts   → Build config + version (single source of truth)
```

### Key Patterns
- Entry point: `DynamicPlugin` interface with `register(context)` and `dispose()`
- UI: `PanelComponentWithUI` with `@Composable Content()`
- State: ViewModel pattern with `StateFlow`
- Providers from `PluginContext`: `workspaceDataProvider`, `splitViewOperations`, `contextMenuProvider`, `activeTabsProvider`
- Null-safe provider access: providers may be null, UI must handle gracefully

### Dependencies
- **boss-plugin-api**: compileOnly (provided by host app at runtime)
- **Compose Desktop**: UI framework
- **Decompose**: Navigation and component lifecycle
- **Coroutines**: Async operations

## Version Management

**`build.gradle.kts` is the single source of truth for version.**

The `processResources` task automatically syncs the version into `plugin.json` at build time. Never manually edit the version in `plugin.json` — only change it in `build.gradle.kts`. The release workflow bump-pushes the version before building, so the version in `main` is the one already released; the next merge releases version+1.

### Evolution rules (runtime-updatable API layer)

The host resolves the newest installed api jar into a shared **ApiClassLoader** at startup (parent of every plugin classloader) and publishes its version as the `boss.api.version` property (`BossApiRuntime`). Consequences:

- **New types** (interfaces/objects/data classes) ship via this jar alone — no BossConsole release. Consumers gate with manifest `minApiVersion`.
- **Member changes to existing types** the host compiles in are shadowed by the host's copy — they require a BossConsole release and `minBossVersion` gating. Mark such types `@HostImplemented`.
- Only additive changes; new interface methods always get default bodies. Never evolve sealed hierarchies or data classes across the boundary.
- CI enforces additive-only evolution via the kotlinx binary-compatibility-validator (`./gradlew apiCheck`; regenerate the dump with `./gradlew apiDump` and commit `api/boss-plugin-api.api`).

### Maven publishing

Each release also publishes `ai.rever.boss.plugin:boss-plugin-api` to GitHub Packages (`.github/workflows/publish-maven.yml`) so BossConsole pins the API as a normal dependency instead of hand-mirroring sources.

## Code Quality

- Use Compose Multiplatform APIs (not Android-specific)
- All Kotlin files must end with a newline
- Handle null providers gracefully — show fallback UI, never crash

## CI/CD

Pushes to `main` trigger the release workflow which:
1. Builds the plugin JAR
2. Creates a GitHub release
3. Publishes to the BOSS Plugin Store

The workflow is defined in `.github/workflows/build.yml` and delegates to the shared workflow in `risa-labs-inc/BossConsole-Releases`.
