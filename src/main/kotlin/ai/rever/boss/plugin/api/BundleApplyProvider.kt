package ai.rever.boss.plugin.api

/**
 * Host-facing SPI for **applying** a Magnetic Force / Plugin Bundle pack.
 *
 * A real BossConsole implementation should:
 * 1. Install / enable each [BundleApplyRequest.plugins] entry (respecting `optional`)
 * 2. Add [BundleApplyRequest.mcps] to the governed MCP allowlist (Builder = user;
 *    Organisation = shared / org-context allowlist when available)
 * 3. Honor RBAC (`bundle.apply`) and kill-switch / org policy
 *
 * **Why this lives on boss-plugin-api (not the bundle plugin JAR):**
 * `PluginContext.registerPluginAPI` / `getPluginAPI` key by [Class] identity.
 * A host-local or plugin-local copy of this interface would not match across
 * classloaders. Serving these types from the ApiClassLoader lets the host
 * register a typed impl that the Plugin Bundle plugin can resolve.
 *
 * **Registration:** `context.registerPluginAPI(impl)` then
 * `context.getPluginAPI(BundleApplyProvider::class.java)`. Option B
 * (`PluginContext.bundleApplyProvider`) is deferred — that member is
 * `@HostImplemented` and needs a BossConsole pin.
 *
 * **Gate:** new types only → `minApiVersion` of the release that ships this
 * file. Do not mark this interface `@HostImplemented`; the host compiles
 * against the fetched api jar rather than a parent-first host copy.
 *
 * Callers **must** enforce Boss literacy before invoke; implementations may
 * re-check and reject when [BundleApplyRequest.literacyAcknowledged] is false.
 *
 * Sync by design for now (UI + MCP share one path). Host suspend enable/install
 * may use a documented IO `runBlocking` until a suspend SPI lands.
 */
interface BundleApplyProvider {
    /** Stable id for diagnostics (e.g. `noop`, `logging+noop`, `bossconsole`). */
    val providerId: String

    /**
     * `true` when this provider can mutate host plugin/MCP state.
     * Stub / NoOp paths return `false`.
     */
    val isHostBacked: Boolean

    /** Apply [request] plugins + MCP allowlist entries. */
    fun applyBundle(request: BundleApplyRequest): BundleApplyOutcome
}

/** Who initiated apply (UI panel vs MCP tool). */
enum class BundleApplySource {
    Ui,
    Mcp,
}

/**
 * Pack mode for apply policy.
 *
 * Treat as an **open set**: new constants may appear in later api jars. Do not
 * write exhaustive `when` without an `else`.
 */
enum class BundleApplyMode {
    Builder,
    Organisation,
}

/**
 * One plugin row from a pack's `plugins[]` (stable apply subset — not the full
 * pack schema / literacy / website surface, which stays in the bundle plugin).
 */
data class BundleApplyPluginSpec(
    val pluginId: String,
    val version: String = "*",
    val optional: Boolean = false,
)

/**
 * One MCP row from a pack's `mcps[]`.
 *
 * [kind] is the wire string (`boss-native`, `official-registry`, `community`,
 * `workflow-bridge`, …) — a [String] on purpose so the open set does not force
 * enum evolution / host-shadow traps.
 */
data class BundleApplyMcpSpec(
    val name: String,
    val kind: String = "community",
    val toolPrefix: String? = null,
    val required: Boolean = true,
)

/**
 * Apply request. Literacy acknowledgement is required by product invariant
 * ("Boss literacy is not skipped").
 *
 * Stable subset only: [bundleId], [mode], [plugins], [mcps]. Full pack
 * metadata (displayName, literacy steps, website, …) stays plugin-local.
 */
data class BundleApplyRequest(
    val bundleId: String,
    val mode: BundleApplyMode,
    val plugins: List<BundleApplyPluginSpec> = emptyList(),
    val mcps: List<BundleApplyMcpSpec> = emptyList(),
    val literacyAcknowledged: Boolean,
    val source: BundleApplySource,
)

/**
 * Result of an apply attempt. Structured so MCP/UI can render the same outcome.
 *
 * Do not add constructor parameters later (binary break on copy/ctor); extend
 * with body-level vals or new methods if needed.
 */
data class BundleApplyOutcome(
    val ok: Boolean,
    val status: BundleApplyStatus,
    val message: String,
    val bundleId: String,
    val providerId: String,
    val pluginsRequested: List<String> = emptyList(),
    val pluginsEnabled: List<String> = emptyList(),
    val mcpsRequested: List<String> = emptyList(),
    val mcpsAllowlisted: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

/**
 * Apply status. Treat as an **open set**.
 */
enum class BundleApplyStatus {
    /** Host mutated plugin/MCP state successfully. */
    Applied,
    /** Provider present but host wiring still stubbed (NoOp path). */
    Stubbed,
    /** Literacy / validation / policy rejected the request. */
    Rejected,
    /** Some plugins/MCPs applied, some failed. */
    Partial,
}

