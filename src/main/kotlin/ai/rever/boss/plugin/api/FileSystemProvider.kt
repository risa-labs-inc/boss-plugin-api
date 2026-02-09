package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

/**
 * Interface for file system data providers.
 *
 * This interface allows the CodeBase panel to be extracted to a separate module
 * while keeping the platform-specific file scanning infrastructure in composeApp.
 *
 * Usage:
 * - composeApp implements this interface with platform-specific file scanning
 * - plugin-panel-codebase depends only on this interface
 * - At registration time, composeApp provides the implementation
 */
interface FileSystemDataProvider {
    /**
     * Scan a directory and return its file tree.
     *
     * @param path Path to the directory to scan
     * @return The scanned file node, or null if the path doesn't exist
     */
    suspend fun scanDirectory(path: String): FileNodeData?

    /**
     * Scan a directory with depth control.
     *
     * @param path Path to the directory to scan
     * @param maxDepth Maximum depth to scan
     * @param startDepth Starting depth (for incremental loading)
     * @return The scanned file node, or null if the path doesn't exist
     */
    suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNodeData?

    /**
     * Check if a directory has any visible children without loading them all.
     * This is a quick O(1) check for the expand indicator.
     *
     * @param path Path to the directory
     * @return True if the directory has visible children
     */
    fun directoryHasChildren(path: String): Boolean

    /**
     * Open a file in the editor.
     *
     * @param path Full path to the file
     * @param windowId The window that initiated the open request
     */
    fun openFile(path: String, windowId: String)

    /**
     * Create a new file.
     *
     * @param parentPath Path to the parent directory
     * @param fileName Name of the new file
     * @return Result containing the full path of the created file, or failure with error
     */
    suspend fun createFile(parentPath: String, fileName: String): Result<String>

    /**
     * Create a new folder.
     *
     * @param parentPath Path to the parent directory
     * @param folderName Name of the new folder
     * @return Result containing the full path of the created folder, or failure with error
     */
    suspend fun createFolder(parentPath: String, folderName: String): Result<String>

    /**
     * Delete a file or folder.
     *
     * @param path Path to the file or folder to delete
     * @return Result indicating success or failure with error
     */
    suspend fun delete(path: String): Result<Unit>

    /**
     * Rename a file or folder.
     *
     * @param path Path to the file or folder to rename
     * @param newName The new name (not full path, just the name)
     * @return Result containing the new full path, or failure with error
     */
    suspend fun rename(path: String, newName: String): Result<String>

    /**
     * Reveal a file or folder in the system file manager.
     *
     * @param path Path to the file or folder to reveal
     * @return Result indicating success or failure with error
     */
    fun revealInFileManager(path: String): Result<Unit>

    /**
     * Copy text to the system clipboard.
     *
     * @param text Text to copy to clipboard
     * @return Result indicating success or failure with error
     */
    fun copyToClipboard(text: String): Result<Unit>

    /**
     * Write content to a file. Creates the file if it doesn't exist.
     *
     * @param path Full path to the file to write
     * @param content Content to write to the file
     * @return Result indicating success or failure with error
     */
    suspend fun writeFile(path: String, content: String): Result<Unit>

    /**
     * Read content from a file.
     *
     * @param path Full path to the file to read
     * @return Result containing the file content, or failure with error
     */
    suspend fun readFile(path: String): Result<String>

    /**
     * Get the user's Downloads directory path.
     *
     * @return Path to the Downloads directory
     */
    fun getDownloadsDirectory(): String

    /**
     * Get the user's home directory path.
     *
     * @return Path to the home directory
     */
    fun getHomeDirectory(): String
}

/**
 * Data class representing a file or directory node in the tree.
 */
@Serializable
data class FileNodeData(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNodeData> = emptyList(),
    val hasChildren: Boolean? = null,
    val loadingState: NodeLoadingStateData = NodeLoadingStateData.UNKNOWN,
    val loadDepth: Int = 0
)

/**
 * Loading state for lazy-loaded directory nodes.
 */
@Serializable
enum class NodeLoadingStateData {
    /**
     * Haven't checked if node has children yet
     */
    UNKNOWN,

    /**
     * Currently checking if node has children
     */
    CHECKING,

    /**
     * Children have been fully loaded
     */
    LOADED
}
