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
// 1.0.65: adds FileSystemDataProvider.supportsHiddenEntries (default false)
// plus showHidden overloads of scanDirectory/scanDirectoryWithDepth/
// directoryHasChildren whose default implementations delegate to the legacy
// dot-filtering methods — additive, binary-compatible. Hosts opt in by
// overriding and flipping the flag (BossConsole implements it); callers must
// check supportsHiddenEntries before relying on showHidden. Also applies
// @HostImplemented to FileSystemDataProvider — first use of the marker; the
// interface is host-implemented, so member changes like this one ship only
// with a BossConsole release.
// 1.0.68: adds the LLM provider access layer — LlmProvider/LlmConfig/LlmApiFormat
// (new SDK types; consumers gate with minApiVersion) + PluginContext.llmProvider
// (host-implemented, default null; needs a BossConsole release + minBossVersion to
// return a real value). Lets AI plugins (e.g. the Jupyter notebook) reuse the
// user's configured provider keys/model instead of managing their own. Additive.
// 1.0.69: adds BookmarkDataProvider.addBookmarks — bulk insert that creates the
// collection if absent and persists once for the whole batch — plus
// supportsBulkAdd so callers can tell a real implementation from the
// compatibility shim. Declared with a default body so implementations built
// against <=1.0.68 stay binary compatible; the bookmarks plugin overrides it to
// do a single save. Motivated by password/bookmark import, where looping the
// single-item addBookmark fired one full collections.json rewrite per bookmark.
//
// NOT jar-only: BookmarkDataProvider is inside the api package that
// plugin-api-core filters into the host and serves parent-first, so the host's
// pinned copy is what every plugin resolves. Consumers must gate on
// minBossVersion, not just minApiVersion — same shape as the 1.0.65
// FileSystemDataProvider change, and BookmarkDataProvider is now marked
// @HostImplemented to say so at the declaration site. Additive.
// 1.0.70: adds LlmProviderSettingsAPI — the seam that lets the plugin owning AI
// provider configuration serve its settings panel to the host and back
// PluginContext.llmProvider. Extends LlmProvider rather than redeclaring
// activeConfig()/configuredProviders(), so the host can relay the registered instance
// straight to other plugins instead of keeping a parallel copy of that state.
// supportsSettingsPanel accompanies the defaulted panel member so the host can tell
// "no settings UI" from "drew a blank page" (same shape as 1.0.65
// supportsHiddenEntries / 1.0.69 supportsBulkAdd). The interface itself is jar-only:
// gate with minApiVersion.
//
// Also adds LlmApiFormat.GOOGLE_GENERATIVE, needed now that Google Gemini is a
// supported provider (it speaks neither the Anthropic nor the OpenAI-compatible
// format, and takes its credential as a query parameter).
//
// NOT jar-only, for that constant: LlmApiFormat is inside the api package that
// plugin-api-core filters into the host and serves parent-first, so the host's pinned
// copy is what every plugin resolves. A plugin using GOOGLE_GENERATIVE must gate on
// minBossVersion for the release that pins 1.0.70, not minApiVersion alone, or it
// fails with NoSuchFieldError. LlmApiFormat/LlmConfig/LlmProvider are now marked
// @HostImplemented to say so at the declaration site, and the enum documents that
// callers must treat it as open (always an else branch) so the next constant is
// cheaper — apiCheck reports both hazards as cleanly additive and cannot see the
// parent-first shadowing. Additive.
// 1.0.72: adds BossDialog/BossAlertDialog + BossOverlayHost/LocalHeavyweightOverlays
// (ai.rever.boss.plugin.ui.BossDialog.kt) so a plugin's dialogs can escape above the
// browser surface. Under JxBrowser HARDWARE_ACCELERATED - the default on every
// platform since BossConsole 9.4.1 - Chromium composites its own native window over
// the Compose scene, so a plain Compose Dialog in a plugin panel is drawn BEHIND the
// page. Swap Dialog( for BossDialog( and AlertDialog( for BossAlertDialog(.
//
// Which gate to declare, and this is the one place people will look. minApiVersion: 1.0.72
// is what makes the symbols RESOLVE, and it is the minimum needed to install and run:
// ApiClassLoader serves brand-new types out of this jar on a host that lacks them. It does
// NOT promise the dialog clears the browser surface - on such a host the fallback is the
// pre-fix, occluded dialog, silently. So: minApiVersion alone if you only need to compile
// and behave no worse than before; ALSO gate on the minBossVersion of the release carrying
// the host's copy if your feature depends on the dialog actually being in front. The host
// logs one warning when the renderer is missing. Additive.
// 1.0.74: adds AiGatewayAPI (ai/rever/boss/plugin/api/AiGateway.kt) - one AI interface for
// plugins, so none of them speaks a provider's wire format. LlmProvider hands out a credential
// and an LlmApiFormat tag, which left jupyter-notebook, llmrpa and flow-tab each carrying their
// own HTTP client and their own `when` over that enum. Because LlmApiFormat is an open set, those
// `when`s throw NoWhenBranchMatchedException the first time a newer constant reaches them - so
// adding a provider silently broke consumers compiled before it, and jupyter does this today for
// GOOGLE_GENERATIVE. AiRequest carries no provider and no format; the implementing plugin resolves
// the active provider and owns the only dispatch. A consumer needs minApiVersion: 1.0.74 alone,
// never minBossVersion, because it never names a constant. Additive (new types only).
//
// Also adds BrokeredCredentialProvider + PluginContext.brokeredCredentialProvider, for a provider
// whose credential nobody types in: the user is signed in and an organisation gateway mints a
// short-lived scoped key for that identity. The exchange stays host-side because nothing on
// PluginContext exposes the Supabase access token, and a broker is named by ID rather than URL -
// exchange(url) would hand every installed plugin a way to post the user's session to a host of
// its choosing. The new PluginContext member needs the host relay, so gate on the minBossVersion
// of the release that implements it. Additive (defaulted member).
//
// Two constraints this jar's data classes impose, written down because apiCheck reports the
// breaking version as additive. (1) Adding a constructor PARAMETER to any of the Ai* data classes
// is a hard break: the synthetic constructor descriptor and copy$default both move, so a plugin
// compiled earlier gets NoSuchMethodError on a call it never touched. Extend by adding a body-level
// `val` (one new getter, additive, at the cost of being outside copy()/equals()), or by adding a
// method. AiRequest.extras exists as the escape hatch for exactly this. (2) A suspend fun returning
// Result has a MANGLED JVM name derived from its signature - complete-gIAlu-s, step-0E7RQCE - so
// adding even a defaulted parameter renames the method and apiCheck shows it as one method
// disappearing and an unrelated one appearing. Those signatures are frozen; extend by adding
// methods.
//
// AiGatewayAPI.step is the primitive under runAgent, for a caller whose loop is already part of
// something else (a node in a graph) and whose stopping rules are its own - it hands the model's
// tool calls back rather than running them. flow-tab surfaced the need: it has its own DAG-shaped
// loop and could not use runAgent without giving that up. Defaulted so a plugin built against a
// later api keeps loading on an older gateway, degrading to a tool-less reply. Additive.
//
// Also adds LlmApiFormat.OPENAI_RESPONSES, the format Codex and the gateways in front of it speak.
// Not interchangeable with OPENAI_CHAT: a Chat Completions body posted to /v1/responses is
// rejected. Same gate as GOOGLE_GENERATIVE and for the same reason - this enum is host-compiled
// and served parent-first, so minApiVersion alone resolves the host's older copy and fails with
// NoSuchFieldError. apiCheck reports it as cleanly additive and cannot see the shadowing.
// Additive.
version = "1.0.74"

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
    // The api is mostly declarations, but this jar does carry hand-written logic now -
    // AiImage.equals/hashCode, AiUsage.plus, BrokeredCredential.toString - and apiCheck
    // only checks shape, not behaviour. These are the first tests in the repo.
    testImplementation(kotlin("test"))

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
