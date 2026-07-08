import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.17.0"
}

group = "ai.rever.boss.plugin.bundled"
// 1.0.48: adds the MCP tool-provider API (McpTool.kt) + PluginContext
// registerMcpToolProvider/unregisterMcpToolProvider/mcpToolRegistry so any
// plugin can contribute `mcp__boss__*` tools that appear/disappear with it.
// 1.0.49: McpToolRegistry gains allTools + disabledToolNames + setToolEnabled
// so the Plugin Manager can list and enable/disable individual MCP tools.
// 1.0.50: McpToolDefinition gains requiredPermissions + requiresAdmin (body
// props, binary-compatible) so MCP tools can be RBAC-gated; the host filters
// exposed tools by the current user's permissions/admin status.
// 1.0.51: adds McpServerController (+McpServerState/McpAttachTargetInfo/
// McpAttachOutcome) — terminal-tab exposes MCP server on/off + CLI attach via
// registerPluginAPI so the Plugin Manager MCP tab can control it.
// 1.0.52: adds McpToolDefinition.withRbac(...) factory (safer alternative to
// mutating requiredPermissions/requiresAdmin via .apply{}); hardens KDoc on
// McpToolHandler (cancellation-cooperative requirement), McpToolProvider.tools()
// (snapshot-at-registration semantics), and McpToolRegistry.tools/allTools (RBAC
// filtering + the deliberate metadata-only disclosure posture of allTools).
// McpToolArgs.int() now returns null for values outside Int range instead of
// silently wrapping (9999999999 -> null, not 1410065407); raw KDoc documents
// that it may hold malformed JSON when the client sent malformed arguments.
// No binary-breaking change.
// 1.0.59: adds ConsoleLogsAPI (cross-plugin per-plugin log access, implemented
// by the Console plugin via registerPluginAPI: logsForPlugin flow + the panel's
// pluginFilter selection) and PluginLogMatcher (the shared keyword heuristic for
// attributing host stdout/stderr lines to a plugin — LogEntryData carries no
// plugin field). Purely additive.
// 1.0.60: adds SplitViewOperations.openTabInSplit(tabInfo, TabSplitMode) and
// openUrlInSplit(url, title, TabSplitMode) — the split half of the "new tab vs
// split" chooser (existing/vertical/horizontal) for registered tab types and for
// URLs, backed host-side by SplitViewState.splitPanel. Default no-ops; additive.
// 1.0.62: the runtime-updatable API layer. Adds BossApiRuntime (feature
// detection against the installed api jar via the host-set boss.api.version
// property), PluginManifest.minApiVersion (gate for SDK-only additions, vs
// minBossVersion for host-implemented ones), @HostImplemented (documentation
// marker for types whose member changes require a host release), and the UI
// extension registry contracts rendered by host >= the platform release:
// PanelMenuContribution/PanelMenuItem (panel top-bar menu items, cross-plugin
// targeting), TabTypeInfo.newTabSpec/createTabInfo + NewTabSpec/NewTabContext
// (New Tab dialog entries), SettingsPageProvider, DeepLinkActionHandler
// (boss://plugin?id&action=…), ShortcutActionProvider/PluginShortcutSpec/
// KeyChordSpec (global shortcuts), StatusBarItemProvider. All additive with
// default no-op PluginContext hooks.
version = "1.0.63"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // Compose dependencies
    implementation(compose.desktop.currentOs)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.materialIconsExtended)

    // Decompose for ComponentContext
    implementation("com.arkivanov.decompose:decompose:3.3.0")
    implementation("com.arkivanov.essenty:lifecycle:2.5.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
}

// Task to build plugin JAR with compiled classes only
tasks.register<Jar>("buildPluginJar") {
    archiveFileName.set("boss-plugin-api-${version}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Implementation-Title" to "BOSS Plugin API",
            "Implementation-Version" to version,
            "Main-Class" to "ai.rever.boss.plugin.bundled.api.BossPluginAPIPlugin"
        )
    }

    // Include compiled classes
    from(sourceSets.main.get().output)

    // Include plugin manifest
    from("src/main/resources")
}

// Sync version from build.gradle.kts into plugin.json (single source of truth)
tasks.processResources {
    inputs.property("pluginVersion", version)
    filesMatching("**/plugin.json") {
        filter { line ->
            line.replace(Regex(""""version"\s*:\s*"[^"]*""""), """"version": "\$version"""")
        }
    }
}

tasks.build {
    dependsOn("buildPluginJar")
}

// NOTE: distribution is store/GitHub-releases ONLY — no Maven publication.
// BossConsole compiles against the api contract by downloading this repo's
// pinned release jar and filtering the `ai.rever.boss.plugin.api` package
// locally (see BossConsole plugins/plugin-api-core/build.gradle.kts,
// fetchApiPluginJar). At runtime the same released jar is the store-updated
// system plugin, resolved by the host's ApiClassLoader and hot-swappable
// without an app restart.
