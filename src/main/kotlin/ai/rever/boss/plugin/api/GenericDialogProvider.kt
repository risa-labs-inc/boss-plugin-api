package ai.rever.boss.plugin.api

/**
 * Provider interface for generic dialog operations.
 *
 * This allows plugins to show common dialogs (text input, confirmation, choice)
 * without implementing their own dialog UI, ensuring consistent styling
 * with the host application.
 */
interface GenericDialogProvider {

    /**
     * Show a text input dialog.
     *
     * @param title Dialog title
     * @param message Optional message/description
     * @param initialValue Initial text value
     * @param placeholder Placeholder text when empty
     * @param validation Optional validation function (returns error message or null if valid)
     * @return The entered text, or null if cancelled
     */
    suspend fun showTextInputDialog(
        title: String,
        message: String? = null,
        initialValue: String = "",
        placeholder: String = "",
        validation: ((String) -> String?)? = null
    ): String?

    /**
     * Show a confirmation dialog with OK/Cancel buttons.
     *
     * @param title Dialog title
     * @param message The confirmation message
     * @param confirmText Text for confirm button (default "OK")
     * @param cancelText Text for cancel button (default "Cancel")
     * @param isDestructive If true, style confirm button as destructive action
     * @return True if confirmed, false if cancelled
     */
    suspend fun showConfirmationDialog(
        title: String,
        message: String,
        confirmText: String = "OK",
        cancelText: String = "Cancel",
        isDestructive: Boolean = false
    ): Boolean

    /**
     * Show a single-choice dialog.
     *
     * @param title Dialog title
     * @param message Optional message
     * @param choices List of choices to select from
     * @param selectedIndex Initially selected index (-1 for none)
     * @return The selected choice, or null if cancelled
     */
    suspend fun showChoiceDialog(
        title: String,
        message: String? = null,
        choices: List<DialogChoice>,
        selectedIndex: Int = -1
    ): DialogChoice?

    /**
     * Show a multi-choice dialog (checkboxes).
     *
     * @param title Dialog title
     * @param message Optional message
     * @param choices List of choices with initial selection state
     * @return List of selected choices, or null if cancelled
     */
    suspend fun showMultiChoiceDialog(
        title: String,
        message: String? = null,
        choices: List<DialogChoiceItem>
    ): List<DialogChoiceItem>?

    /**
     * Show an alert/message dialog with a single OK button.
     *
     * @param title Dialog title
     * @param message The message to display
     * @param buttonText Text for the button (default "OK")
     */
    suspend fun showAlertDialog(
        title: String,
        message: String,
        buttonText: String = "OK"
    )

    /**
     * Show a three-button dialog (e.g., Save/Don't Save/Cancel).
     *
     * @param title Dialog title
     * @param message The message to display
     * @param positiveText Text for positive button
     * @param negativeText Text for negative button
     * @param neutralText Text for neutral button
     * @return The button that was clicked
     */
    suspend fun showThreeButtonDialog(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String,
        neutralText: String
    ): DialogButton

    /**
     * Show a progress dialog for long-running operations.
     * Returns a handle to update or dismiss the dialog.
     *
     * @param title Dialog title
     * @param message Initial message
     * @param isIndeterminate If true, show indeterminate progress
     * @param cancellable If true, user can cancel
     * @return Handle to control the dialog
     */
    fun showProgressDialog(
        title: String,
        message: String,
        isIndeterminate: Boolean = true,
        cancellable: Boolean = false
    ): ProgressDialogHandle
}

/**
 * A choice item for single-choice dialogs.
 */
data class DialogChoice(
    val id: String,
    val label: String,
    val description: String? = null,
    val icon: String? = null
)

/**
 * A choice item for multi-choice dialogs with selection state.
 */
data class DialogChoiceItem(
    val id: String,
    val label: String,
    val description: String? = null,
    val isSelected: Boolean = false
)

/**
 * Button type for three-button dialogs.
 */
enum class DialogButton {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
    CANCELLED
}

/**
 * Handle for controlling a progress dialog.
 */
interface ProgressDialogHandle {
    /**
     * Update the progress value (0.0 to 1.0).
     *
     * @param progress Progress value
     */
    fun updateProgress(progress: Float)

    /**
     * Update the message.
     *
     * @param message New message
     */
    fun updateMessage(message: String)

    /**
     * Dismiss the dialog.
     */
    fun dismiss()

    /**
     * Check if the dialog was cancelled by the user.
     *
     * @return True if cancelled
     */
    fun isCancelled(): Boolean

    /**
     * Set a callback for when the user cancels.
     *
     * @param callback The callback
     */
    fun setOnCancelListener(callback: () -> Unit)
}
