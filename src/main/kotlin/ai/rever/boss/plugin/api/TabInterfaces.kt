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
}

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
