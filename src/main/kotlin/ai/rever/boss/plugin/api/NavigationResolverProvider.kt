package ai.rever.boss.plugin.api

/**
 * Kind of navigation target.
 */
enum class NavigationTargetKind {
    CLASS,
    INTERFACE,
    OBJECT,
    FUNCTION,
    PROPERTY,
    PARAMETER,
    VARIABLE,
    TYPE_PARAMETER,
    ENUM,
    ENUM_ENTRY,
    ANNOTATION,
    UNKNOWN
}

/**
 * Information about a definition (class, function, property, etc.).
 */
data class DefinitionInfoData(
    val name: String,
    val kind: NavigationTargetKind,
    val filePath: String,
    val offset: Int,
    val line: Int,
    val column: Int
)

/**
 * Location of a reference to a symbol.
 */
data class ReferenceLocationData(
    val filePath: String,
    val line: Int,
    val column: Int,
    val offset: Int,
    val context: String,
    val symbolName: String
)

/**
 * Result of a navigation resolution.
 */
sealed class NavigationResolveResult {
    /**
     * Navigation target found - go to this location.
     */
    data class Found(
        val filePath: String,
        val line: Int,
        val column: Int
    ) : NavigationResolveResult()

    /**
     * Clicked on a definition - show usages popup instead of navigating.
     */
    data class ShowUsages(
        val references: List<ReferenceLocationData>,
        val definition: DefinitionInfoData
    ) : NavigationResolveResult()

    /**
     * No navigation target found at the given position.
     */
    data object NotFound : NavigationResolveResult()

    /**
     * Navigation is not available (PSI not initialized, unsupported language, etc.).
     */
    data object Unavailable : NavigationResolveResult()
}

/**
 * Provider for resolving code navigation targets using the host's PSI infrastructure.
 *
 * This allows dynamic plugins to use the same navigation capabilities as the bundled editor.
 * The host application provides PSI-based navigation (go-to-definition, find usages) through
 * this interface, so plugins don't need to initialize their own PSI infrastructure.
 *
 * When clicking on a definition (class, function, property), this returns ShowUsages
 * with all references to that symbol. When clicking on a reference, it returns Found
 * with the definition location.
 *
 * Usage in a plugin:
 * ```kotlin
 * val resolver = context.navigationResolverProvider
 *
 * // In BossEditor's onNavigationRequested:
 * val result = resolver?.resolveNavigation(content, filePath, offset)
 * when (result) {
 *     is NavigationResolveResult.Found -> {
 *         // Navigate to result.filePath at result.line:result.column
 *     }
 *     is NavigationResolveResult.ShowUsages -> {
 *         // Show usages popup with result.references
 *     }
 *     else -> {
 *         // No navigation available
 *     }
 * }
 * ```
 */
interface NavigationResolverProvider {
    /**
     * Check if PSI navigation is initialized and available.
     */
    val isInitialized: Boolean

    /**
     * Resolve navigation target for a position in a file.
     *
     * This uses the host's PSI infrastructure to find the definition of the symbol
     * at the given offset. Supports Kotlin files (.kt, .kts).
     *
     * If the clicked position is a definition, returns ShowUsages with all references.
     * If the clicked position is a reference, returns Found with the definition location.
     *
     * @param content The full content of the file
     * @param filePath The absolute path to the file
     * @param offset The character offset in the file where navigation was requested
     * @return NavigationResolveResult indicating the target or why navigation failed
     */
    suspend fun resolveNavigation(content: String, filePath: String, offset: Int): NavigationResolveResult

    /**
     * Ensure the file's project is indexed for cross-file navigation.
     *
     * This should be called when opening a file to ensure navigation works
     * for symbols defined in other files in the same project.
     *
     * @param filePath The absolute path to the file
     */
    suspend fun ensureProjectIndexed(filePath: String)
}
