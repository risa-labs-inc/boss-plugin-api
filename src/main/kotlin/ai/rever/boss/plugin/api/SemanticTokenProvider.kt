package ai.rever.boss.plugin.api

/**
 * Provider interface for semantic token data from the host's PSI infrastructure.
 *
 * This allows dynamic plugins to access semantic highlighting information
 * (function calls, property accesses, type references, etc.) that is computed
 * by the host's PSI analysis.
 *
 * The host populates this data when files are indexed/analyzed, and plugins
 * can query it to merge semantic tokens with lexer-based syntax highlighting.
 */
interface SemanticTokenProvider {

    /**
     * Gets semantic elements for a file.
     *
     * @param filePath Absolute path to the file
     * @return List of semantic elements, or null if not available
     */
    fun getSemanticElements(filePath: String): List<SemanticElement>?

    /**
     * Triggers semantic analysis for a file.
     * This should be called after content changes to update the semantic cache.
     *
     * @param filePath Absolute path to the file
     * @param content The file content to analyze
     */
    suspend fun analyzeFile(filePath: String, content: String)
}

/**
 * Represents a semantic element in the code.
 */
data class SemanticElement(
    val startOffset: Int,
    val endOffset: Int,
    val type: SemanticElementType,
    val name: String
)

/**
 * Types of semantic elements that can be highlighted.
 */
enum class SemanticElementType {
    FUNCTION_CALL,      // Function/method calls
    PROPERTY_ACCESS,    // Property/field access
    CLASS_REFERENCE,    // Class/type references
    OBJECT_REFERENCE,   // Object/companion references
    PARAMETER,          // Function parameters
    LOCAL_VARIABLE,     // Local variables
    ANNOTATION,         // Annotations
    LABEL,              // Labels (@label)
    TYPE_PARAMETER,     // Generic type parameters
}
