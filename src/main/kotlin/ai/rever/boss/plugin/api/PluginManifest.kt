package ai.rever.boss.plugin.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Manifest for a dynamically loaded plugin.
 *
 * This describes the plugin's metadata, capabilities, and configuration.
 * The manifest is read from `META-INF/boss-plugin/plugin.json` in the plugin JAR.
 *
 * Example plugin.json:
 * ```json
 * {
 *   "manifestVersion": 1,
 *   "pluginId": "com.example.my-plugin",
 *   "displayName": "My Plugin",
 *   "version": "1.0.0",
 *   "apiVersion": "1.0",
 *   "mainClass": "com.example.plugin.MyPlugin",
 *   "type": "panel",
 *   "description": "Example plugin",
 *   "dependencies": [],
 *   "sandbox": {
 *     "maxThreads": 2
 *   }
 * }
 * ```
 */
@Serializable
data class PluginManifest(
    /**
     * Version of the manifest format (for future compatibility).
     */
    @SerialName("manifestVersion")
    val manifestVersion: Int = 1,

    /**
     * Unique identifier for this plugin.
     * Should follow reverse domain notation (e.g., "com.example.my-plugin").
     */
    @SerialName("pluginId")
    val pluginId: String,

    /**
     * Human-readable display name for the plugin.
     */
    @SerialName("displayName")
    val displayName: String,

    /**
     * Plugin version following semantic versioning (e.g., "1.0.0").
     */
    @SerialName("version")
    val version: String,

    /**
     * Minimum BOSS Plugin API version required (e.g., "1.0").
     * The plugin will not load if the host API version is lower.
     */
    @SerialName("apiVersion")
    val apiVersion: String,

    /**
     * Fully qualified class name of the Plugin implementation.
     * This class must implement the [Plugin] interface.
     */
    @SerialName("mainClass")
    val mainClass: String,

    /**
     * Type of plugin for categorization and validation.
     */
    @SerialName("type")
    val type: PluginType = PluginType.PANEL,

    /**
     * Optional description of the plugin.
     */
    @SerialName("description")
    val description: String = "",

    /**
     * Optional author information.
     */
    @SerialName("author")
    val author: String = "",

    /**
     * Optional URL for plugin homepage or documentation.
     */
    @SerialName("url")
    val url: String = "",

    /**
     * List of plugin dependencies (other plugin IDs that must be loaded first).
     */
    @SerialName("dependencies")
    val dependencies: List<PluginDependency> = emptyList(),

    /**
     * Sandbox configuration for the plugin.
     */
    @SerialName("sandbox")
    val sandbox: PluginSandboxConfig = PluginSandboxConfig(),

    /**
     * List of packages that should be shared with the host classloader.
     * These packages will use parent-first loading.
     */
    @SerialName("sharedPackages")
    val sharedPackages: List<String> = emptyList(),

    /**
     * Whether the plugin supports dynamic loading/unloading at runtime.
     * If false, the plugin can only be loaded at startup.
     */
    @SerialName("isDynamic")
    val isDynamic: Boolean = true,

    /**
     * Actions to perform when the plugin is unloaded.
     */
    @SerialName("unloadActions")
    val unloadActions: PluginUnloadActions = PluginUnloadActions(),

    /**
     * Panel configuration for panel-type plugins.
     * Specifies icon, location, and display settings.
     */
    @SerialName("panel")
    val panel: PluginPanelConfig? = null,

    /**
     * Whether this plugin requires admin privileges.
     * If true, the plugin will only be visible and active for admin users.
     *
     * Legacy gate, superseded by [requiredPermissions]. Still honored: a plugin
     * with `requiresAdmin = true` additionally requires the user to be an admin.
     */
    @SerialName("requiresAdmin")
    val requiresAdmin: Boolean = false,

    /**
     * Effective permissions the user must hold for this plugin to be visible and
     * active (granular RBAC). The host shows/registers the plugin only if the
     * user's effective permissions (from the JWT `user_permissions` claim, which
     * includes permissions inherited via the role hierarchy) contain ALL of these.
     *
     * Empty (the default, and the case for legacy plugins that predate this field)
     * means no special permission is required — the plugin is available to every
     * authenticated user (baseline `user` level).
     */
    @SerialName("requiredPermissions")
    val requiredPermissions: List<String> = emptyList(),

    /**
     * NEW permissions this plugin *introduces* to the RBAC system — distinct from
     * [requiredPermissions], which lists what the user must already hold (and may
     * reference existing system permissions like `role.read`).
     *
     * When the plugin is published to the store, each of these is auto-registered
     * into the permission catalog as a non-system, **ungranted** entry; an admin
     * then grants them to roles. Names must be namespaced `domain.action` and must
     * NOT use a reserved system domain (role, user, api_key, rpa, secret, plugins).
     * Any [requiredPermissions] entry that is not already a catalog permission
     * should be declared here too, or publishing rejects it as a dangling requirement.
     */
    @SerialName("definedPermissions")
    val definedPermissions: List<DefinedPermission> = emptyList(),

    /**
     * Minimum BOSS version required to run this plugin (e.g., "8.16.27").
     * The plugin will not load if the host BOSS version is lower.
     *
     * Use for HOST-implemented capabilities: new providers, member additions
     * to host-compiled API types, Compose bumps.
     */
    @SerialName("minBossVersion")
    val minBossVersion: String = "",

    /**
     * Minimum boss-plugin-api version required to run this plugin
     * (e.g., "1.0.62"). The plugin will not load if the installed api layer
     * (the newest boss-plugin-api jar resolved by the ApiClassLoader) is
     * older.
     *
     * Use for SDK-only additions — brand-new interfaces/types shipped via
     * the boss-plugin-api jar alone, which need no host release.
     */
    @SerialName("minApiVersion")
    val minApiVersion: String = "",

    /**
     * Minimum IPC contract version this plugin's runtime requires from the
     * host (semver, e.g. "1.0.0"). Used for out-of-process plugins and the
     * microkernel runtime JAR itself so a runtime built against a newer IPC
     * surface cannot silently attach to an older host — the spawner rejects
     * the launch with a clear "update BossConsole" error.
     *
     * Blank means "unknown / not declared" (legacy JARs from before this
     * field existed); treated as likely-compatible but logged at WARN.
     */
    @SerialName("minIpcVersion")
    val minIpcVersion: String = "",

    // ============================================================
    // MICROKERNEL / PROCESS ISOLATION
    // These fields support the OS-like multi-process architecture.
    // ============================================================

    /**
     * Process isolation mode for this plugin.
     * - "in-process": Plugin runs in the kernel JVM (default, backward compatible)
     * - "out-of-process": Plugin runs in its own JVM or GraalVM native image
     */
    @SerialName("isolationMode")
    val isolationMode: String = "in-process",

    /**
     * Capabilities this plugin exposes for use in Mastery DAG workflows.
     * Each capability describes an action with typed input/output.
     */
    @SerialName("capabilities")
    val capabilities: List<PluginCapability> = emptyList(),

    /**
     * Health monitoring contract for out-of-process plugins.
     */
    @SerialName("healthContract")
    val healthContract: PluginHealthContract? = null,

    /**
     * Known failure modes and repair hints for the self-healing orchestrator.
     */
    @SerialName("repairHints")
    val repairHints: List<PluginRepairHint> = emptyList(),

    /**
     * Paths to this plugin's source code files (quine self-description).
     * Used by the orchestrator to read and understand the plugin for AI-powered repair.
     */
    @SerialName("sourceFiles")
    val sourceFiles: List<String> = emptyList(),

    /**
     * Paths to configuration files this plugin reads.
     */
    @SerialName("configFiles")
    val configFiles: List<String> = emptyList(),

    /**
     * Natural language description of what this plugin does.
     * Used by the orchestrator for AI diagnosis and by Mastery for workflow generation.
     */
    @SerialName("behaviorSpec")
    val behaviorSpec: String = "",

    /**
     * JSON Schema describing this plugin's configuration format.
     * Used by the orchestrator for automated config repair.
     */
    @SerialName("configSchema")
    val configSchema: String? = null,

    /**
     * Whether this plugin supports state snapshots for rollback on failure.
     */
    @SerialName("stateSnapshotEnabled")
    val stateSnapshotEnabled: Boolean = false,

    /**
     * Path to GraalVM native image binary for this plugin (out-of-process mode only).
     * If null, the plugin runs as a JVM subprocess.
     */
    @SerialName("nativeImagePath")
    val nativeImagePath: String? = null,

    // ============================================================
    // BUNDLED PLUGIN SUPPORT
    // These fields support system/bundled plugins that ship with
    // BossConsole and can be upgraded from the plugin store.
    // ============================================================

    /**
     * Whether this is a system/bundled plugin.
     *
     * System plugins:
     * - Ship with BossConsole (bundled in the distribution)
     * - Load before dynamic plugins
     * - May have restricted unload behavior (see [canUnload])
     * - Can be upgraded from the plugin store
     *
     * Default is false for regular dynamic plugins.
     */
    @SerialName("systemPlugin")
    val systemPlugin: Boolean = false,

    /**
     * Plugin load priority (lower values load first).
     *
     * Priority ranges:
     * - 0-10: System/bundled plugins (boss-plugin-api uses 0)
     * - 11-50: Core infrastructure plugins
     * - 51-99: High-priority user plugins
     * - 100+: Regular user plugins (default)
     *
     * Plugins with the same priority load in undefined order.
     */
    @SerialName("loadPriority")
    val loadPriority: Int = 100,

    /**
     * Whether this plugin can be unloaded at runtime.
     *
     * System plugins may set this to false to prevent unloading,
     * as they provide core APIs that other plugins depend on.
     *
     * When false:
     * - Unload requests will be rejected gracefully
     * - Plugin can still be upgraded (unload + reload sequence)
     *
     * Default is true for regular plugins.
     */
    @SerialName("canUnload")
    val canUnload: Boolean = true
)

/**
 * Type of plugin for categorization.
 */
@Serializable
enum class PluginType {
    /**
     * Plugin that provides a panel in the sidebar.
     */
    @SerialName("panel")
    PANEL,

    /**
     * Plugin that provides a new tab type.
     */
    @SerialName("tab")
    TAB,

    /**
     * Plugin that provides both panels and tab types.
     */
    @SerialName("mixed")
    MIXED,

    /**
     * Plugin that provides both panels and tab types (alias for MIXED).
     */
    @SerialName("hybrid")
    HYBRID,

    /**
     * Plugin that provides services or utilities without UI.
     */
    @SerialName("service")
    SERVICE
}

/**
 * Dependency on another plugin.
 */
@Serializable
data class PluginDependency(
    /**
     * ID of the required plugin.
     */
    @SerialName("pluginId")
    val pluginId: String,

    /**
     * Version range requirement (e.g., ">=1.0.0", ">=1.0.0 <2.0.0", "1.0.0").
     * Uses semantic versioning comparison.
     */
    @SerialName("version")
    val version: String = "*",

    /**
     * Whether this dependency is optional.
     * Optional dependencies are loaded if available but don't prevent plugin loading if missing.
     */
    @SerialName("optional")
    val optional: Boolean = false
)

/**
 * A permission a plugin introduces to the RBAC system. Registered into the
 * permission catalog (non-system, ungranted) when the plugin is published, then
 * granted to roles by an admin. See [PluginManifest.definedPermissions].
 */
@Serializable
data class DefinedPermission(
    /**
     * Permission name in `domain.action` form (e.g. "invoices.read").
     * Must be namespaced and must not use a reserved system domain.
     */
    @SerialName("name")
    val name: String,

    /**
     * Human-readable description shown in the role-management UI.
     */
    @SerialName("description")
    val description: String = ""
)

/**
 * Sandbox configuration for a plugin.
 */
@Serializable
data class PluginSandboxConfig(
    /**
     * Maximum number of threads for the plugin's sandbox.
     */
    @SerialName("maxThreads")
    val maxThreads: Int = 2,

    /**
     * Maximum heap memory for the plugin in MB (0 = no limit).
     */
    @SerialName("maxMemoryMb")
    val maxMemoryMb: Int = 0,

    /**
     * Whether to enable crash isolation via the sandbox.
     */
    @SerialName("enableSandbox")
    val enableSandbox: Boolean = true,

    /**
     * Interval in milliseconds between heartbeat checks.
     */
    @SerialName("heartbeatIntervalMs")
    val heartbeatIntervalMs: Long = 5000,

    /**
     * Maximum number of restart attempts before disabling.
     */
    @SerialName("maxRestartAttempts")
    val maxRestartAttempts: Int = 3
)

/**
 * Panel configuration for panel-type plugins.
 * Allows plugins to specify their icon, location, and display settings in the manifest.
 */
@Serializable
data class PluginPanelConfig(
    /**
     * Material icon name for the panel (e.g., "Language", "Chat", "Settings").
     * Should match a name from Material Icons Outlined.
     */
    @SerialName("icon")
    val icon: String = "Extension",

    /**
     * Panel location in the sidebar.
     * Format: "side.slot.position" where:
     * - side: "left" or "right"
     * - slot: "top" or "bottom"
     * - position: "top", "middle", or "bottom" (position within the slot)
     *
     * Examples: "left.top.top", "right.bottom.middle", "left.bottom.bottom"
     */
    @SerialName("location")
    val location: String = "left.bottom.bottom",

    /**
     * Default order within the panel slot (lower numbers appear first).
     */
    @SerialName("order")
    val order: Int = 100,

    /**
     * Optional custom panel ID. If not specified, defaults to "{pluginId}-panel".
     */
    @SerialName("panelId")
    val panelId: String? = null,

    /**
     * Display name for the panel. If not specified, uses the plugin's displayName.
     */
    @SerialName("displayName")
    val displayName: String? = null
) {
    /**
     * Parse the location string into a Panel object.
     *
     * @return Panel object representing the location, or default Panel.left.bottom if parsing fails
     */
    fun parseLocation(): Panel {
        return parseLocationString(location)
    }

    companion object {
        /**
         * Parse a location string like "right.top.top" into a Panel object.
         *
         * @param locationString The location string (e.g., "left.top.top", "right.bottom.bottom")
         * @return Panel object, or Panel.left.bottom as default
         */
        fun parseLocationString(locationString: String): Panel {
            val parts = locationString.lowercase().split(".")
            if (parts.isEmpty()) return Panel.Companion.run { Panel.left.bottom }

            // Start with the first part (left, right, top, bottom)
            var panel: Panel = when (parts[0]) {
                "left" -> Panel.left
                "right" -> Panel.right
                "top" -> Panel.top
                "bottom" -> Panel.bottom
                else -> return Panel.Companion.run { Panel.left.bottom }
            }

            // Apply subsequent parts
            for (i in 1 until parts.size) {
                panel = when (parts[i]) {
                    "top" -> Panel.Companion.run { panel.top }
                    "bottom" -> Panel.Companion.run { panel.bottom }
                    "left" -> Panel.Companion.run { panel.left }
                    "right" -> Panel.Companion.run { panel.right }
                    else -> panel
                }
            }

            return panel
        }
    }
}

/**
 * Actions to perform when the plugin is unloaded.
 */
@Serializable
data class PluginUnloadActions(
    /**
     * Whether to clear caches associated with this plugin.
     */
    @SerialName("clearCaches")
    val clearCaches: Boolean = true,

    /**
     * Whether to dispose all registered services.
     */
    @SerialName("disposeServices")
    val disposeServices: Boolean = true,

    /**
     * Custom actions defined by the plugin.
     */
    @SerialName("customActions")
    val customActions: List<String> = emptyList()
)

/**
 * Represents a loaded plugin instance with its manifest and state.
 */
data class LoadedPlugin(
    /**
     * The plugin manifest.
     */
    val manifest: PluginManifest,

    /**
     * The plugin instance (implements [Plugin]).
     */
    val instance: Plugin,

    /**
     * ClassLoader used to load this plugin.
     */
    val classLoader: java.net.URLClassLoader,

    /**
     * Path to the plugin JAR file.
     */
    val jarPath: String,

    /**
     * Timestamp when the plugin was loaded.
     */
    val loadedAt: Long = System.currentTimeMillis(),

    /**
     * Current state of the plugin.
     */
    val state: PluginState = PluginState.LOADED
)

/**
 * State of a dynamically loaded plugin.
 */
enum class PluginState {
    /**
     * Plugin JAR loaded but not yet registered.
     */
    LOADED,

    /**
     * Plugin registered with the application.
     */
    REGISTERED,

    /**
     * Plugin is being initialized.
     */
    INITIALIZING,

    /**
     * Plugin is being unloaded.
     */
    UNLOADING,

    /**
     * Plugin has been unloaded.
     */
    UNLOADED,

    /**
     * Plugin encountered an error.
     */
    ERROR,

    /**
     * Plugin is disabled.
     */
    DISABLED
}

// ============================================================
// MICROKERNEL DATA CLASSES
// ============================================================

/**
 * A capability that a plugin exposes for use in Mastery DAG workflows.
 */
@Serializable
data class PluginCapability(
    /** Action name (e.g., "run_command", "open_file", "navigate") */
    @SerialName("action")
    val action: String,
    /** JSON Schema for this action's input */
    @SerialName("inputSchemaJson")
    val inputSchemaJson: String = "{}",
    /** JSON Schema for this action's output */
    @SerialName("outputSchemaJson")
    val outputSchemaJson: String = "{}",
    /** Human-readable description */
    @SerialName("description")
    val description: String = "",
)

/**
 * Health monitoring contract for out-of-process plugins.
 */
@Serializable
data class PluginHealthContract(
    /** How often heartbeats are expected (milliseconds) */
    @SerialName("heartbeatIntervalMs")
    val heartbeatIntervalMs: Long = 5000,
    /** Maximum time to wait for process startup (milliseconds) */
    @SerialName("startupTimeoutMs")
    val startupTimeoutMs: Long = 30000,
)

/**
 * Known failure mode and repair hint for the self-healing orchestrator.
 */
@Serializable
data class PluginRepairHint(
    /** Regex pattern matching stack trace or error message */
    @SerialName("failurePattern")
    val failurePattern: String,
    /** Severity of this failure type */
    @SerialName("severity")
    val severity: RepairSeverity,
    /** Recommended repair strategy */
    @SerialName("strategy")
    val strategy: RepairStrategy,
    /** Human-readable description of the failure */
    @SerialName("description")
    val description: String,
    /** Suggested fix for user escalation */
    @SerialName("suggestedFix")
    val suggestedFix: String? = null,
)

/**
 * Severity of a plugin failure.
 */
@Serializable
enum class RepairSeverity {
    /** Temporary, likely resolves on restart */
    @SerialName("transient")
    TRANSIENT,
    /** Degraded functionality, may need config/state fix */
    @SerialName("degraded")
    DEGRADED,
    /** Fatal, requires code fix or rollback */
    @SerialName("fatal")
    FATAL,
}

/**
 * Strategy for repairing a failed plugin process.
 */
@Serializable
enum class RepairStrategy {
    /** Simple restart */
    @SerialName("restart")
    RESTART,
    /** Clear persisted state, restart fresh */
    @SerialName("reset_state")
    RESET_STATE,
    /** AI-assisted config file patch */
    @SerialName("patch_config")
    PATCH_CONFIG,
    /** AI-assisted source code patch (requires user approval) */
    @SerialName("patch_source")
    PATCH_SOURCE,
    /** Revert to previous plugin version */
    @SerialName("rollback")
    ROLLBACK,
    /** Show diagnostic report to user */
    @SerialName("escalate")
    ESCALATE,
}

/**
 * Constants for plugin manifest handling.
 */
object PluginManifestConstants {
    /**
     * Location of the plugin manifest within a JAR file.
     */
    const val MANIFEST_PATH = "META-INF/boss-plugin/plugin.json"

    /**
     * Alternate manifest path for backward compatibility.
     */
    const val LEGACY_MANIFEST_PATH = "META-INF/plugin.json"

    /**
     * Directory name for plugins.
     */
    const val PLUGINS_DIR = "plugins"

    /**
     * Directory name for bundled plugins.
     */
    const val BUNDLED_PLUGINS_DIR = "bundled-plugins"

    /**
     * Current BOSS Plugin API version.
     * Plugins must declare a compatible apiVersion in their manifest.
     */
    const val CURRENT_API_VERSION = "1.0.18"
}
