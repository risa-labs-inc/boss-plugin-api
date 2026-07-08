package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.arkivanov.decompose.ComponentContext

data class TabTypeId(
    val typeId: String,
    val pluginId: String = "ai.rever.boss"
)

/**
 * Wrapper for tab icons that can be either vector or bitmap.
 */
sealed class TabIcon {
    data class Vector(
        val imageVector: ImageVector,
        val tint: Color? = null
    ) : TabIcon()
    data class Image(val painter: Painter) : TabIcon()

    @Composable
    fun asPainter(): Painter = when (this) {
        is Vector -> rememberVectorPainter(imageVector)
        is Image -> painter
    }
}

interface TabInfo {
    val id: String
    val typeId: TabTypeId
    val title: String
    val icon: ImageVector
    val tabIcon: TabIcon?
        get() = null
}

interface TabTypeInfo {
    val typeId: TabTypeId
    val displayName: String
    val icon: ImageVector

    /**
     * Non-null to make this tab type appear as an option in the host's
     * New Tab dialog. Defaults to null (hidden) so existing tab types are
     * unaffected.
     */
    val newTabSpec: NewTabSpec?
        get() = null

    /**
     * Build the [TabInfo] to open for the New Tab dialog's input. Return
     * null to reject the input (the dialog stays open). Only called when
     * [newTabSpec] is non-null.
     */
    fun createTabInfo(input: String, context: NewTabContext): TabInfo? = null
}

/**
 * Display metadata for a tab type's entry in the host New Tab dialog.
 */
data class NewTabSpec(
    /** Options are sorted ascending; built-ins come first. */
    val order: Int = 100,
    val inputLabel: String = "Input",
    val inputPlaceholder: String = "",
    /** When true, confirming with empty input is allowed (terminal-style). */
    val inputOptional: Boolean = false,
    val confirmLabel: String = "Open",
)

/**
 * Context passed to [TabTypeInfo.createTabInfo] from the New Tab dialog.
 */
data class NewTabContext(
    val projectPath: String? = null,
    val windowId: String? = null,
)

/**
 * Extended TabInfo interface for terminal tabs.
 * Both bundled TerminalTabInfo and dynamic plugin's TerminalTabData should implement this.
 * This allows the dynamic plugin to access terminal-specific properties without reflection.
 */
interface TerminalTabInfoInterface : TabInfo {
    val initialCommand: String?
    val workingDirectory: String?
}

interface TabComponentWithUI: ComponentContext {
    val tabTypeInfo: TabTypeInfo
    val config: TabInfo

    @Composable
    fun Content()
}
