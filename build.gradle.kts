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
// 1.0.75: adds AiAvailability + AiReadiness - the dialog a plugin shows when an AI action does
// nothing. Before it, each consumer ended at a dead-end toast naming BOTH possible causes ("install
// the gateway and configure a provider"), which is accurate and useless: two different problems,
// a route to neither. check() tells them apart (getPluginAPI null => GATEWAY_MISSING; present but
// activeModel() null => NO_PROVIDER) and promptToFix() opens the Toolbox or Settings, AI Providers
// accordingly.
//
// It lives HERE rather than in the AI Gateway plugin for a structural reason: the case it handles is
// the gateway being absent, so a helper shipped inside the gateway could never run then. The api jar
// is served by ApiClassLoader and is always present.
//
// Nothing in it throws - it runs on the path where a user just pressed a button, so failing to
// explain a failure must not become a second failure. A broken or absent dialog host degrades to
// "the caller shows its own message", and a broken one is never read as consent. Additive.
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
//
// 1.0.76: adds browser telemetry — BrowserEvent/BrowserEventType (navigation and
// engagement: page viewed/left, dwell + active ms, tab open/close/activate) and
// BrowserInteractionEvent/BrowserInteractionType (in-page interaction: clicks, rage
// clicks, scroll depth, field focus, form submit, copy/paste). Both carry only a
// registrable domain and structural element attributes — never a URL, path, query,
// page title, element text, input value, or label. The host reduces and sanitizes
// before constructing either event, so a consumer cannot recover page-level detail
// even by accident.
//
// Jar-only: every type here is new, so no host copy exists to shadow them
// parent-first. Consumers gate with minApiVersion. Note this is a one-time property
// — once a BossConsole release pins 1.0.76, plugin-api-core filters these into the
// host and ADDING A CONSTANT to any of these enums becomes a minBossVersion change,
// same trap as 1.0.70's LlmApiFormat.GOOGLE_GENERATIVE. Each enum documents that
// callers must treat it as open. Additive.
// 1.0.77: adds SplitViewOperations.openPanelAsTab(panelId) + supportsOpenPanelAsTab —
// the programmatic "Open as Tab" for a sidebar panel. The host already did this for
// itself (panel header action, header drag-out onto the centre) but the whole path was
// internal: PanelHostTabType/PanelHostTabInfo live in the host's components package and
// the trigger is BossDraggableComponent.requestPromoteToTab. openTab was not a way in —
// PanelHostTabComponent builds from the concrete PanelHostTabInfo, so a plugin-side
// TabInfo carrying TabTypeId("panel-host") lands in a cast, and createTabInfo is not
// overridden there either. The Toolbox reached it reflectively to give each tool an Open
// button (boss-plugin-plugin-manager#35) and had to accept losing the panel's state,
// because the only close a plugin can reach (PanelEventProvider.closePanel) drops the
// cached component while the host's own promote path merely hides the sidebar copy.
// Routing to requestPromoteToTab inherits all three of the things that made the internal
// path right: the cached component (so state moves with the panel), the hosted-as-tab
// bookkeeping (so the sidebar icon afterwards FOCUSES the tab rather than opening a
// second copy) and the non-destructive collapse.
//
// NOT jar-only. SplitViewOperations is inside the api package that plugin-api-core filters
// into the host and serves parent-first, so the host's pinned copy is what every plugin
// resolves — an older host's copy has neither member and a call is a NoSuchMethodError,
// not the defaulted no-op. Gate on the minBossVersion of the release that pins 1.0.77, the
// same shape as 1.0.65 supportsHiddenEntries and 1.0.69 addBookmarks; apiCheck reports it
// as cleanly additive and cannot see the shadowing. SplitViewOperations is now marked
// @HostImplemented to say so at the declaration site. A plugin that must also run below
// that floor probes supportsOpenPanelAsTab reflectively and keeps its old fallback.
//
// Also narrows the openTab KDoc, which promised more indirection than it has: the typeId
// lookup reaches any registered factory, but a HOST-registered type may build from a
// concrete config class of its own and reject a foreign TabInfo. That applied to every
// such type, not only panel-host, and was the doc the Toolbox read before finding the
// cast. Doc-only. Additive.
// 1.0.78: adds AiCliSessionAPI and its types (AiCliEngine, AiCliHealth, AiCliSessionSpec,
// AiCliPricing, AiCliUsage, AiCliHostedTool, AiCliApprovalAsk/Answer, AiCliDeniedCall,
// AiCliEvent) — driving a locally
// installed coding-agent CLI, Claude Code or Codex, headlessly, authenticated by THAT
// CLI's own terminal login rather than by a key any plugin holds. That auth path is the
// most valuable one in the product (no API key, no organisation spend, nothing stored)
// and until now it existed only inside Atlas, which spawns `claude -p` and `codex exec`
// itself. Moving it behind an interface lets the AI Gateway own the subprocess and serve
// it to every plugin, and lets Atlas stop being a process supervisor.
//
// NOT an extension of AiGatewayAPI, deliberately. That is a stateless completion
// interface: AiRequest has no working directory, no session to resume, no subagent and no
// permission mode, and AiChunk has no element for a tool call. A CLI agent session has all
// five. Stretching the completion types would make every consumer of the simple case pay
// for the complicated one, so this is a second interface and the gateway registers both.
//
// JAR-ONLY. Every type here is brand new, so the ApiClassLoader serves it with a single
// shared Class identity and cross-plugin getPluginAPI works with NO BossConsole release —
// gate on minApiVersion alone. This is why CLI engines are not modelled as an LlmConfig:
// that type is host-compiled, requires a non-blank apiKey and an endpoint URL, and a CLI
// session has neither. Adding an LlmApiFormat constant for them would have forced a host
// release for the same reason 1.0.70 and 1.0.74 did.
//
// Review rounds also settled several contracts while the shapes were still free: hosted tools
// (AiCliHostedTool) so a caller serves its own tools on the implementation's bridge rather
// than standing up a second server; AiCliUsage on Completed, because the implementation
// cannot price a turn without the numbers and discarding them blanks every token display;
// qualifiedToolName(engineId, tool), since mcp__server__tool is Claude Code's convention
// rather than a cross-CLI standard; selectEngine(null) answering true, which its own KDoc
// promised and a flat false contradicted; and idleTimeoutMs defaulting to ten minutes, since
// a working tool call emits nothing while it runs and three minutes cannot fit a build.
//
// Also corrects the AiRequest.temperature KDoc, which promised "the value the user chose
// for the active provider". Nothing ever chose one: there is no temperature control in AI
// Providers and LlmConfig.temperature is a non-null field defaulting to 0.7 that no
// settings surface writes. Reading that doc is what led the gateway to send a temperature
// on every request, which 400s on models that reject the parameter
// (boss-plugin-ai-gateway#3). And AiRequest.maxTokens, which made the identical claim about
// a field that is also a non-null default nobody writes - 2000 - so an implementation
// trusting it capped every null-maxTokens request at 2000 output tokens. Same provenance,
// quieter symptom: an answer that stops mid-sentence reports no error anywhere. Doc-only.
// Additive.
//
// 1.0.82 - documents the fillCredentials tombstone properly. 1.0.81 turned that method into a
// deprecated no-op and its KDoc then contradicted itself: it said this jar's body is shadowed at
// runtime AND that removing the method would throw NoSuchMethodError at the call site. Both are
// true of DIFFERENT copies - the host compiles in its own BrowserHandle and serves it parent-first
// - and side by side they read as nonsense. The runtime statements are now scoped to the host's
// copy, and the crash window is attributed correctly: it is a user on a NEW host with a
// not-yet-updated fluck-browser (1.2.18 and earlier call it bare in a launch), not the older host
// the previous text blamed.
//
// The migration advice also said only "fill via executeJavaScript", which is public guidance that
// leads somewhere bad if followed literally: executeJavaScript takes SOURCE, so a caller
// interpolates the credential into a script, and concatenating one containing a quote, backslash,
// newline or U+2028 closes the string literal and executes the remainder - an injection whose
// payload is the user's own password. The KDoc now requires JSON encoding, shows the targeted
// fill, and says to return a sentinel because executeJavaScript answers null for unsupported,
// no-frame, threw and evaluated-to-null alike. Doc-only. Additive.
//
// 1.0.83 - BrowserHandle.setPageEventScript(script, onEvent) + the PAGE_EVENT_BRIDGE constant. A
// document-start injection point with a push back to the plugin, for fluck-browser's "save this
// password?" prompt. The prompt needs the credential the user typed, and the only moment it exists
// is the submit that is immediately followed by a navigation destroying the JS context - so a
// latch-and-poll design races its own teardown and loses on fast logins. A push cannot.
//
// Deliberately carries NO field rules, no event vocabulary and no credential type. The host
// injects the script it is given and forwards whatever that script hands the bridge; the plugin
// owns every heuristic. That is 1.0.81's lesson (fillCredentials could not say WHICH field, so the
// host guessed, and guessed wrong on accounts.google.com) applied to reading rather than writing.
// A typed CredentialSubmission parameter would have re-made exactly that mistake.
//
// Grants no new access: executeJavaScript already returns arbitrary page content to a plugin. What
// is new is timing - before the page's own scripts, and delivered while the document still exists.
//
// HOST-IMPLEMENTED, so the default body is a no-op that silently delivers nothing and consumers
// gate on the minBossVersion of the BossConsole release carrying the implementation, not on
// minApiVersion. Additive.
//
// THE BRIDGE IS A SCOPED PARAMETER, NOT A WINDOW PROPERTY, and that is the one decision an
// implementer must not get wrong. The host wraps the script and passes the object in as a parameter
// named PAGE_EVENT_BRIDGE. Nothing is left on window.
//
// Three earlier drafts of this contract said otherwise and each was a security bug, which is why
// this paragraph is long. A documented global is reachable by every script on the page, so for a
// channel whose first consumer posts a password: a page could REPLACE the property and receive the
// payload itself; FORGE events into the plugin's sink; and DETECT BOSS by probing for the name. A
// binding in the script's own scope has none of those. The host does need a window slot to hand the
// object across (executeJavaScript takes source, not arguments), so it uses a random per-injection
// name and, in the SAME evaluation and BEFORE invoking the script body, reads it into a local and
// deletes it. That ordering is part of the contract: a delete after the script body is skipped
// whenever the script throws, leaving a live bridge on window for the rest of the document.
//
// What the random name does not buy: enumeration. Object.keys(window) in the gap between the host
// writing the slot and the script deleting it finds the key whatever it is called - a gap that only
// exists for the one-off injection into a document already running page script. The name therefore
// carries no recognisable prefix, so what enumeration finds is anonymous rather than a "this is
// BOSS" bit. Closing it fully needs a non-enumerable property, which JsObject.putProperty cannot
// express.
//
// Also settled while the shape was free:
//
// - onEvent takes (url, json), not (json). The host reads the posting document's URL at the moment
//   of the call. The reason is NOT that the channel is forgeable - under the parameter design it
//   largely is not - it is that a URL inside the JSON is only as trustworthy as whatever wrote it,
//   and reading the handle's URL AFTER the fact can be overtaken by the navigation the event itself
//   started. That would attribute a credential to the page a login LANDED on rather than the one it
//   was typed into, which for a cross-domain sign-in means storing it against the wrong site.
// - A script may be evaluated MORE than once in one document and must tolerate it. A guard inside
//   the script cannot fix that (fresh function scope per evaluation), and the only shared slot is
//   window - the detectability this design removes. A draft of this contract promised the host would
//   dedupe instead; it could not, because the host's navigation event fires after the new document's
//   script context is created, so any counter keyed on it advances between the two injections that
//   reach one document. Duplicates are cheap for an event-driven consumer; the guarantee was not.
// - The host bounds payload size and rate and DROPS the excess rather than queueing it, so a chatty
//   or hostile script cannot allocate its way through the host heap one call at a time.
// - Arm and disarm are two methods, setPageEventScript(script, onEvent) with NON-NULL parameters
//   plus clearPageEventScript(), matching startCoBrowseCapture/stopCoBrowseCapture on the same
//   interface. The first draft took a nullable pair, which admitted (script, null) and (null,
//   callback) - both only ever caller bugs, both silently uninstalling. Worth settling before
//   release rather than after: BrowserHandle is @HostImplemented, so reshaping it later costs a
//   BossConsole release.
// - supportsPageEventScript, defaulting to false, so a consumer can tell an older host from a host
//   that delivered nothing. Silence otherwise has three causes - no implementation, a host-side
//   size/rate drop, and the user doing nothing - and minBossVersion is only the coarse answer. Same
//   shape as supportsHiddenEntries (1.0.65) and supportsBulkAdd (1.0.69).
// - PAGE_EVENT_EMIT pins the METHOD name too. The bridge property was a constant from the start
//   while `.emit` lived only in prose, which is the same silent-rename hazard one level down: the
//   name only appears inside a JavaScript string, so renaming it is a compile error at no consumer
//   and every built plugin posts into a method that is gone.
// - Callers must uninstall in dispose() or pin their own classloader across an api hot swap; single
//   owner per handle, so a second caller silently replaces the first.
// - NO origin scoping, deliberately, and worth stating rather than leaving to be discovered: one
//   call means the script runs on every main-frame document for the handle's lifetime. That is not
//   quite the same standing as executeJavaScript, which is per-call and per-document. An origin
//   allowlist is additive later (an overload), and is the obvious next parameter if a second
//   consumer wants less than "everywhere".
//
// 1.0.85 - adds DownloadCenterProvider + PluginContext.downloadCenterProvider: the one place every
// in-flight transfer is reported, so the host's bottom bar can show all of them. Before it, the only
// visible download progress was a status-bar widget the Toolbox plugin owned, which is why every
// download the HOST started (a plugin update from a panel badge, a missing dependency, the
// application's own update) ran with no progress at all - nothing could report into a plugin's widget.
// Decisions worth keeping, because all five types are @HostImplemented and reshaping them later costs
// a BossConsole release:
// - TransferPhase.INSTALLING is the ONLY phase that withdraws Cancel, and TransferPhase.allowsCancel
//   is the single expression of that. Abandoning a jar swap leaves the plugin unloaded; a download is
//   bytes, and a downloaded-but-uninstalled app update is a file to delete - which is what lets one
//   rule serve both a plugin's rows and the app update's Install/Cancel pair.
// - NO failure phase. A failed transfer ends and its row goes; saying why belongs to whatever the
//   caller already reports with. A row that lingered to show an error would need its own dismissal,
//   and a progress bar is the wrong surface for a message someone has to read. The cost is stated in
//   the KDoc: a vanished row does not distinguish success from failure by itself.
// - READY_TO_INSTALL is host-only. begin() takes a cancel action and nothing else, so a plugin
//   setting that phase would render an Install button with nothing behind it.
// - The host namespaces a plugin-supplied id with the calling plugin's id. Without that, ids are one
//   shared namespace: two plugins collide on "update" and hit the nested-begin rule by accident, and
//   any plugin could address the host's app-update row - withdrawing its Cancel or faking progress.
// - begin() does NOT coalesce progress. Stated in TransferHandle.progress rather than left to be
//   measured: a per-8KiB loop on a 40 MB jar is ~5,000 emissions, each waking the bar, the dialog and
//   every plugin watching its own id.
// - A plugin that must also run on an OLDER host cannot rely on a null check, and the reason is
//   worth stating precisely because it looked like a contradiction of the evolution rules. New TYPES
//   do resolve on an old host, from the installed api jar via the ApiClassLoader - that is what
//   minApiVersion gates. The PROPERTY does not: PluginContext is host-compiled and parent-first, so
//   below minBossVersion the read is a NoSuchMethodError. Either miss makes the host's
//   BinaryCompatibilityValidator reject the WHOLE plugin, since it member-checks every
//   ai.rever.boss.plugin.* class in the jar - so undercutting a floor means moving those references
//   out of that package entirely, not catching something. AGENTS.md now carries this as a general
//   rule; the Toolbox met both halves adopting this.
// - TransferPhase.allowsCancel is the PHASE half of the cancel rule. Renderers must use
//   TransferInfo.cancellable, which is that AND a cancel action existing; the extension exists so
//   the phase half has one definition, and its own KDoc says so - an api property that looked like
//   the whole answer would reintroduce "Cancel offered mid-jar-swap" from inside the api.
// - transfers is readable by every installed plugin, deliberately, like applicationEventBus. Id
//   qualification protects addressing, not reading.
version = "1.0.85"

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
