package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

@Serializable
open class Panel {
    @Serializable
    data class TOP(val child: Panel? = null): Panel()

    @Serializable
    data class LEFT(val child: Panel? = null): Panel()

    @Serializable
    data class RIGHT(val child: Panel? = null): Panel()

    @Serializable
    data class BOTTOM(val child: Panel? = null): Panel()

    companion object {
        val top = TOP()
        val left = LEFT()
        val right = RIGHT()
        val bottom = BOTTOM()

        val Panel.root: Panel get() = when (this) {
            is TOP -> Panel.top
            is LEFT -> Panel.left
            is RIGHT -> Panel.right
            is BOTTOM -> Panel.bottom
            else -> this
        }

        val Panel.opposite get() = when (this) {
            is TOP -> Panel.bottom
            is LEFT -> Panel.right
            is RIGHT -> Panel.left
            is BOTTOM -> Panel.top
            else -> this
        }

        val Panel.isHorizontal get() = this is LEFT || this is RIGHT

        val Panel.isVertical get() = this is TOP || this is BOTTOM

        val Panel.isFirst get() = this is LEFT || this is TOP

        val Panel.isLast get() = this is RIGHT || this is BOTTOM

        val Panel.top get() = when (this) {
            is TOP -> this.top
            is LEFT -> this.top
            is RIGHT -> this.top
            is BOTTOM -> this.top
            else -> this
        }

        val Panel.left get() = when (this) {
            is TOP -> this.left
            is LEFT -> this.left
            is RIGHT -> this.left
            is BOTTOM -> this.left
            else -> this
        }

        val Panel.right get() = when (this) {
            is TOP -> this.right
            is LEFT -> this.right
            is RIGHT -> this.right
            is BOTTOM -> this.right
            else -> this
        }

        val Panel.bottom get() = when (this) {
            is TOP -> this.bottom
            is LEFT -> this.bottom
            is RIGHT -> this.bottom
            is BOTTOM -> this.bottom
            else -> this
        }

        val TOP.top: TOP get() = this.copy(child = this.child?.top ?: Panel.top)
        val TOP.left: TOP get() = this.copy(child = this.child?.left ?: Panel.left)
        val TOP.right: TOP get() = this.copy(child = this.child?.right ?: Panel.right)
        val TOP.bottom: TOP get() = this.copy(child = this.child?.bottom ?: Panel.bottom)

        val LEFT.top: LEFT get() = this.copy(child = this.child?.top ?: Panel.top)
        val LEFT.left: LEFT get() = this.copy(child = this.child?.left ?: Panel.left)
        val LEFT.right: LEFT get() = this.copy(child = this.child?.right ?: Panel.right)
        val LEFT.bottom: LEFT get() = this.copy(child = this.child?.bottom ?: Panel.bottom)

        val RIGHT.top: RIGHT get() = this.copy(child = this.child?.top ?: Panel.top)
        val RIGHT.left: RIGHT get() = this.copy(child = this.child?.left ?: Panel.left)
        val RIGHT.right: RIGHT get() = this.copy(child = this.child?.right ?: Panel.right)
        val RIGHT.bottom: RIGHT get() = this.copy(child = this.child?.bottom ?: Panel.bottom)

        val BOTTOM.top: BOTTOM get() = this.copy(child = this.child?.top ?: Panel.top)
        val BOTTOM.left: BOTTOM get() = this.copy(child = this.child?.left ?: Panel.left)
        val BOTTOM.right: BOTTOM get() = this.copy(child = this.child?.right ?: Panel.right)
        val BOTTOM.bottom: BOTTOM get() = this.copy(child = this.child?.bottom ?: Panel.bottom)
    }
}
