package ai.rever.boss.plugin.api

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the surrounding main-window panel is currently the one the
 * user is interacting with (the "active" panel in the split layout).
 *
 * Plugins rendered inside a [BossMainWindowPanel] receive a value
 * that flips between `true` and `false` as the user moves focus
 * between panels (clicking another split, sidebar plugin, etc.).
 *
 * The default is `true` so that plugins rendered outside a managed
 * panel — sidebar slots, test harnesses, dialogs — behave exactly as
 * before. Only plugins that actively read this value see any change.
 *
 * **Why this exists**: some embedded widgets (notably the BossTerm
 * terminal) only re-issue their internal focus requester when an
 * "active" flag toggles. Without a signal flowing in from the host,
 * the embedded widget can end up visually present but unable to
 * receive keystrokes after external focus round-trips. Reading
 * `LocalIsPanelActive.current` and forwarding it into such widgets
 * gives them the trigger they need.
 */
val LocalIsPanelActive: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }
