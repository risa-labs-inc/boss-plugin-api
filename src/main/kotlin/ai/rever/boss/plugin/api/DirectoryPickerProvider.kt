package ai.rever.boss.plugin.api

/**
 * Provider interface for directory picker functionality.
 *
 * This interface abstracts platform-specific directory picker dialogs
 * to allow the CodeBase panel to be extracted to a separate module.
 */
interface DirectoryPickerProvider {
    /**
     * Pick a directory using a platform-native file picker dialog.
     *
     * @param onResult Callback with the selected directory path, or null if cancelled
     */
    fun pickDirectory(onResult: (String?) -> Unit)
}
