package ai.rever.boss.plugin.api

/**
 * Provider interface for file picker dialogs.
 *
 * Extends the existing DirectoryPickerProvider with file open/save capabilities.
 * Plugins cannot access AWT/Swing file dialogs directly due to classloader isolation.
 */
interface FilePickerProvider {

    /**
     * Show a file open dialog.
     *
     * @param title Optional dialog title
     * @param filters Optional list of file extension filters (e.g., ["json", "txt"])
     * @param onResult Callback with the selected file path, or null if cancelled
     */
    fun pickFile(
        title: String? = null,
        filters: List<String>? = null,
        onResult: (String?) -> Unit
    )

    /**
     * Show a file save dialog.
     *
     * @param suggestedFileName Optional default file name
     * @param filters Optional list of file extension filters
     * @param onResult Callback with the selected save path, or null if cancelled
     */
    fun pickSaveFile(
        suggestedFileName: String? = null,
        filters: List<String>? = null,
        onResult: (String?) -> Unit
    )
}
