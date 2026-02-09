package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Provider interface for code editor content.
 * This allows the code editor tab to be loaded as a dynamic plugin.
 */
interface EditorContentProvider {
    /**
     * Display code editor content with syntax highlighting and editing capabilities.
     *
     * @param content The file content to display
     * @param onContentChange Callback when content changes
     * @param language The programming language for syntax highlighting
     * @param filePath The path to the file being edited
     * @param projectPath The project root path
     * @param modifier Modifier for the editor
     * @param onModifiedStateChange Callback when modification state changes
     * @param onSaveRequested Callback when save is requested (returns success)
     * @param onCursorPositionChange Optional callback when cursor position changes (line, column)
     * @param onRunFunction Optional callback when run gutter icon is clicked
     * @param onNavigate Optional callback for PSI navigation (Cmd+Click)
     * @param showRunGutter Whether to show run gutter icons (default true)
     */
    @Composable
    fun CodeEditorContent(
        content: String,
        onContentChange: (String) -> Unit,
        language: String,
        filePath: String,
        projectPath: String,
        modifier: Modifier,
        onModifiedStateChange: (Boolean) -> Unit,
        onSaveRequested: suspend () -> Boolean,
        onCursorPositionChange: ((line: Int, column: Int) -> Unit)? = null,
        onRunFunction: ((MainFunctionInfo) -> Unit)? = null,
        onNavigate: ((filePath: String, line: Int, column: Int) -> Unit)? = null,
        showRunGutter: Boolean = true
    )

    /**
     * Read file content with size validation.
     *
     * @param filePath Path to the file
     * @param maxSize Maximum allowed file size in bytes
     * @return FileReadResult indicating success, size limit exceeded, or error
     */
    fun readFileContent(filePath: String, maxSize: Long = 100_000_000): FileReadResult

    /**
     * Write content to a file.
     *
     * @param filePath Path to the file
     * @param content Content to write
     * @return true if successful, false otherwise
     */
    fun writeFileContent(filePath: String, content: String): Boolean

    /**
     * Detect the programming language based on file path.
     *
     * @param filePath Path to the file
     * @return Language identifier string (e.g., "kotlin", "java", "python")
     */
    fun detectLanguage(filePath: String): String

    // ============ Phase 1: Find/Replace and Navigation APIs ============

    /**
     * Show the find dialog for the currently focused editor.
     * If no editor is focused, this is a no-op.
     */
    fun showFindDialog() {}

    /**
     * Show the find & replace dialog for the currently focused editor.
     * If no editor is focused, this is a no-op.
     */
    fun showReplaceDialog() {}

    /**
     * Navigate to a specific line in the currently focused editor.
     * If no editor is focused, this is a no-op.
     *
     * @param line The line number to navigate to (1-based)
     */
    fun goToLine(line: Int) {}

    /**
     * Find the next occurrence of the current search term.
     * If no editor is focused or no search term is active, this is a no-op.
     */
    fun findNext() {}

    /**
     * Find the previous occurrence of the current search term.
     * If no editor is focused or no search term is active, this is a no-op.
     */
    fun findPrevious() {}

    // ============ Phase 1: Editor Feature Toggles ============

    /**
     * Check if code folding is enabled.
     * @return true if code folding is enabled, false otherwise
     */
    fun isCodeFoldingEnabled(): Boolean = true

    /**
     * Enable or disable code folding for editors.
     *
     * @param enabled true to enable code folding, false to disable
     */
    fun setCodeFoldingEnabled(enabled: Boolean) {}

    /**
     * Check if bracket matching is enabled.
     * @return true if bracket matching is enabled, false otherwise
     */
    fun isBracketMatchingEnabled(): Boolean = true

    /**
     * Enable or disable bracket matching for editors.
     *
     * @param enabled true to enable bracket matching, false to disable
     */
    fun setBracketMatchingEnabled(enabled: Boolean) {}

    // ============ Phase 1: Advanced Editor Toggles ============

    /**
     * Check if mark occurrences is enabled (highlights other occurrences of selected word).
     * @return true if mark occurrences is enabled, false otherwise
     */
    fun isMarkOccurrencesEnabled(): Boolean = true

    /**
     * Enable or disable mark occurrences for editors.
     *
     * @param enabled true to enable mark occurrences, false to disable
     */
    fun setMarkOccurrencesEnabled(enabled: Boolean) {}

    /**
     * Check if current line highlight is enabled.
     * @return true if current line highlight is enabled, false otherwise
     */
    fun isCurrentLineHighlightEnabled(): Boolean = true

    /**
     * Enable or disable current line highlight for editors.
     *
     * @param enabled true to enable current line highlight, false to disable
     */
    fun setCurrentLineHighlightEnabled(enabled: Boolean) {}

    /**
     * Check if auto-indent is enabled.
     * @return true if auto-indent is enabled, false otherwise
     */
    fun isAutoIndentEnabled(): Boolean = true

    /**
     * Enable or disable auto-indent for editors.
     *
     * @param enabled true to enable auto-indent, false to disable
     */
    fun setAutoIndentEnabled(enabled: Boolean) {}

    // ============ Phase 1: PSI Navigation APIs ============

    /**
     * Check if PSI navigation (Cmd+Click go-to-definition) is enabled.
     * @return true if navigation is enabled, false otherwise
     */
    fun isNavigationEnabled(): Boolean = true

    /**
     * Enable or disable PSI navigation for editors.
     *
     * @param enabled true to enable PSI navigation, false to disable
     */
    fun setNavigationEnabled(enabled: Boolean) {}

    /**
     * Navigate to a definition at the specified location.
     * Opens the file and scrolls to the line/column position.
     *
     * @param filePath The file path to navigate to
     * @param line The line number (1-based)
     * @param column The column number (1-based)
     */
    fun navigateToDefinition(filePath: String, line: Int, column: Int) {}

    // ============ Phase 2: Main Function Detection ============

    /**
     * Detect main/runnable functions in the given file content.
     *
     * @param filePath Path to the file (used for language detection)
     * @param content The file content to analyze
     * @return List of detected main functions with their line numbers and metadata
     */
    fun detectMainFunctions(filePath: String, content: String): List<MainFunctionInfo> = emptyList()

    /**
     * Execute a detected main function.
     *
     * @param mainFunction The main function to execute
     * @param projectPath The project path for determining working directory
     * @param windowId The window ID for multi-window support
     */
    fun executeMainFunction(mainFunction: MainFunctionInfo, projectPath: String, windowId: String?) {}

    // ============ Phase 2: Theme Integration ============

    /**
     * Get the list of available editor themes.
     * @return List of theme names
     */
    fun getAvailableThemes(): List<String> = listOf("Dark", "Light")

    /**
     * Get the current editor theme.
     * @return The current theme name
     */
    fun getCurrentTheme(): String = "Dark"

    /**
     * Set the editor theme.
     *
     * @param theme The theme name to apply
     */
    fun setTheme(theme: String) {}

    // ============ Phase 3: Font Customization ============

    /**
     * Get the current editor font size.
     * @return Font size in points
     */
    fun getFontSize(): Int = 14

    /**
     * Set the editor font size.
     *
     * @param size Font size in points
     */
    fun setFontSize(size: Int) {}

    /**
     * Get the current editor font family.
     * @return Font family name
     */
    fun getFontFamily(): String = "JetBrains Mono"

    /**
     * Set the editor font family.
     *
     * @param family Font family name
     */
    fun setFontFamily(family: String) {}

    /**
     * Get the list of available editor fonts.
     * @return List of font family names
     */
    fun getAvailableFonts(): List<String> = listOf("JetBrains Mono", "Fira Code", "Source Code Pro", "Menlo", "Monaco")
}

/**
 * Result of attempting to read a file with size validation.
 */
sealed class FileReadResult {
    /**
     * File was read successfully.
     */
    data class Success(val content: String) : FileReadResult()

    /**
     * File exceeds the maximum allowed size.
     */
    data class FileTooLarge(val sizeBytes: Long, val maxSizeBytes: Long) : FileReadResult()

    /**
     * An error occurred reading the file.
     */
    data class Error(val message: String) : FileReadResult()

    /**
     * The file does not exist.
     */
    data object FileNotFound : FileReadResult()
}


/**
 * Information about a detected main/runnable function.
 */
data class MainFunctionInfo(
    /**
     * The file path containing this function.
     */
    val filePath: String,

    /**
     * The line number where the function is defined (0-based).
     */
    val lineNumber: Int,

    /**
     * The name of the function (e.g., "main", "run").
     */
    val functionName: String,

    /**
     * The programming language.
     */
    val language: String,

    /**
     * The full qualified class name if applicable (for Java/Kotlin).
     */
    val className: String? = null,

    /**
     * Additional metadata about the function.
     */
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Generate a short display name for this function.
     */
    fun toShortName(): String {
        return if (className != null) {
            "$className.$functionName"
        } else {
            functionName
        }
    }
}
