package ai.rever.boss.plugin.api

/**
 * Provider interface for displaying notifications/toasts to users.
 *
 * This allows plugins to show user feedback without depending on
 * the host application's UI implementation.
 */
interface NotificationProvider {

    /**
     * Show a toast notification.
     *
     * @param message The message to display
     * @param type The notification type (determines visual style)
     * @param duration How long to show the notification
     * @param title Optional title for the notification
     * @param actionLabel Optional action button label
     * @param onAction Optional callback when action button is clicked
     * @return Notification ID that can be used to dismiss it
     */
    fun showToast(
        message: String,
        type: NotificationType = NotificationType.INFO,
        duration: NotificationDuration = NotificationDuration.SHORT,
        title: String? = null,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ): String

    /**
     * Show an info notification (convenience method).
     *
     * @param message The message to display
     * @param title Optional title
     * @return Notification ID
     */
    fun showInfo(message: String, title: String? = null): String =
        showToast(message, NotificationType.INFO, title = title)

    /**
     * Show a success notification (convenience method).
     *
     * @param message The message to display
     * @param title Optional title
     * @return Notification ID
     */
    fun showSuccess(message: String, title: String? = null): String =
        showToast(message, NotificationType.SUCCESS, title = title)

    /**
     * Show a warning notification (convenience method).
     *
     * @param message The message to display
     * @param title Optional title
     * @return Notification ID
     */
    fun showWarning(message: String, title: String? = null): String =
        showToast(message, NotificationType.WARNING, title = title)

    /**
     * Show an error notification (convenience method).
     *
     * @param message The message to display
     * @param title Optional title
     * @return Notification ID
     */
    fun showError(message: String, title: String? = null): String =
        showToast(message, NotificationType.ERROR, title = title)

    /**
     * Dismiss a specific notification.
     *
     * @param notificationId The ID returned from showToast
     */
    fun dismiss(notificationId: String)

    /**
     * Dismiss all notifications.
     */
    fun dismissAll()
}

/**
 * Type/severity of a notification that determines visual styling.
 */
enum class NotificationType {
    /** Neutral informational message */
    INFO,

    /** Positive success message (green) */
    SUCCESS,

    /** Cautionary warning message (yellow/orange) */
    WARNING,

    /** Negative error message (red) */
    ERROR
}

/**
 * Duration for how long a notification remains visible.
 */
enum class NotificationDuration {
    /** Short duration (~3 seconds) */
    SHORT,

    /** Longer duration (~6 seconds) */
    LONG,

    /** Stays until manually dismissed */
    INDEFINITE
}
