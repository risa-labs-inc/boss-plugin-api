package ai.rever.boss.plugin.api

/**
 * Interface for dynamically loaded plugins.
 *
 * Dynamic plugins are loaded from JAR files at runtime and must include
 * a manifest file at `META-INF/boss-plugin/plugin.json`.
 *
 * Required manifest fields:
 * - pluginId: Unique identifier (e.g., "ai.rever.boss.plugin.example")
 * - displayName: Human-readable name
 * - version: Semantic version (e.g., "1.0.0")
 * - apiVersion: BOSS Plugin API version (e.g., "1.0")
 * - mainClass: Fully qualified class name of this DynamicPlugin implementation
 * - type: Plugin type ("panel", "tab", "mixed", or "service")
 *
 * Optional manifest fields:
 * - description: Brief description of the plugin
 * - author: Author name or organization
 * - url: Homepage or documentation URL
 * - dependencies: List of required plugin dependencies
 * - sandbox: Sandbox configuration
 * - panel: Panel configuration (for panel-type plugins)
 *
 * Example plugin.json:
 * ```json
 * {
 *   "manifestVersion": 1,
 *   "pluginId": "ai.rever.boss.plugin.example",
 *   "displayName": "Example Plugin",
 *   "version": "1.0.0",
 *   "apiVersion": "1.0",
 *   "mainClass": "com.example.ExamplePlugin",
 *   "type": "panel",
 *   "description": "An example plugin",
 *   "author": "Your Name"
 * }
 * ```
 *
 * @see Plugin for the base plugin interface
 * @see PluginManifest for the manifest data class
 */
interface DynamicPlugin : Plugin {
    /**
     * Plugin version following semantic versioning (e.g., "1.0.0").
     * Must match the version in the plugin manifest.
     */
    val version: String

    /**
     * Brief description of what this plugin does.
     * Should match the description in the plugin manifest.
     */
    val description: String
        get() = ""

    /**
     * Author name or organization.
     * Should match the author in the plugin manifest.
     */
    val author: String
        get() = ""

    /**
     * URL for plugin homepage or source repository (e.g., GitHub URL).
     * Required for publishing to the plugin store.
     * Should match the url in the plugin manifest.
     */
    val url: String
        get() = ""

    /**
     * Validate that this plugin's properties match the manifest.
     *
     * @param manifest The manifest to validate against
     * @return ValidationResult indicating success or listing mismatches
     */
    fun validateAgainstManifest(manifest: PluginManifest): ManifestValidationResult {
        val errors = mutableListOf<String>()

        if (pluginId != manifest.pluginId) {
            errors.add("pluginId mismatch: code='$pluginId', manifest='${manifest.pluginId}'")
        }
        if (displayName != manifest.displayName) {
            errors.add("displayName mismatch: code='$displayName', manifest='${manifest.displayName}'")
        }
        if (version != manifest.version) {
            errors.add("version mismatch: code='$version', manifest='${manifest.version}'")
        }

        return if (errors.isEmpty()) {
            ManifestValidationResult.Valid
        } else {
            ManifestValidationResult.Invalid(errors)
        }
    }

    companion object {
        /**
         * Validate that a manifest has all required fields for publishing.
         *
         * @param manifest The manifest to validate
         * @return ValidationResult indicating success or listing missing fields
         */
        fun validateManifestForPublishing(manifest: PluginManifest): ManifestValidationResult {
            val errors = mutableListOf<String>()

            if (manifest.pluginId.isBlank()) {
                errors.add("pluginId is required")
            } else if (!manifest.pluginId.matches(Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+$"))) {
                errors.add("pluginId must be in reverse domain notation (e.g., 'ai.rever.boss.plugin.example')")
            }

            if (manifest.displayName.isBlank()) {
                errors.add("displayName is required")
            }

            if (manifest.version.isBlank()) {
                errors.add("version is required")
            } else if (!manifest.version.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) {
                errors.add("version must be in semver format (e.g., '1.0.0')")
            }

            if (manifest.mainClass.isBlank()) {
                errors.add("mainClass is required")
            }

            if (manifest.apiVersion.isBlank()) {
                errors.add("apiVersion is required")
            }

            if (manifest.url.isBlank()) {
                errors.add("url is required (GitHub repository URL)")
            } else if (!manifest.url.startsWith("https://")) {
                errors.add("url must be a valid HTTPS URL (e.g., 'https://github.com/user/repo')")
            }

            return if (errors.isEmpty()) {
                ManifestValidationResult.Valid
            } else {
                ManifestValidationResult.Invalid(errors)
            }
        }
    }
}

/**
 * Result of manifest validation.
 */
sealed class ManifestValidationResult {
    /**
     * Manifest is valid.
     */
    object Valid : ManifestValidationResult() {
        override fun toString() = "Valid"
    }

    /**
     * Manifest has validation errors.
     *
     * @property errors List of error messages
     */
    data class Invalid(val errors: List<String>) : ManifestValidationResult() {
        override fun toString() = "Invalid: ${errors.joinToString(", ")}"
    }

    /**
     * Check if validation passed.
     */
    val isValid: Boolean
        get() = this is Valid

    /**
     * Get error messages if validation failed.
     */
    val errorMessages: List<String>
        get() = when (this) {
            is Valid -> emptyList()
            is Invalid -> errors
        }
}
