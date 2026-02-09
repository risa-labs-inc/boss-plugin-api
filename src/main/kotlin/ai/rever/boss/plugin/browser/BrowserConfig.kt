package ai.rever.boss.plugin.browser

/**
 * Configuration for creating a browser instance.
 *
 * @property url Initial URL to load in the browser
 * @property enableDevTools Whether to enable developer tools (F12)
 * @property enableDownloads Whether to enable file downloads
 * @property enableFullscreen Whether to allow fullscreen video
 * @property userAgent Optional custom user agent string
 */
data class BrowserConfig(
    val url: String = "",
    val enableDevTools: Boolean = false,
    val enableDownloads: Boolean = true,
    val enableFullscreen: Boolean = true,
    val userAgent: String? = null
)
