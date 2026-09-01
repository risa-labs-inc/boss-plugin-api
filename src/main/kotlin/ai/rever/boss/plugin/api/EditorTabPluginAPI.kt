package ai.rever.boss.plugin.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Plugin API exposed by the editor-tab plugin for the host (and other plugins) to consume.
 *
 * Same pattern as [TerminalTabPluginAPI]: the editor-tab plugin owns the editor stack
 * (BossEditor is bundled privately inside the plugin JAR), registers this API via
 * registerPluginAPI(), and consumers access it via
 * getPluginAPI(EditorTabPluginAPI::class.java).
 *
 * The BossConsole host consumes it through EditorAPIAccess.
 *
 * Every method has a default implementation so plugin JARs built against older
 * API versions keep loading, and hosts degrade gracefully when the installed
 * plugin predates a method.
 */
@HostImplemented
interface EditorTabPluginAPI {

    // ============================================================
    // SETTINGS PANELS
    // ============================================================

    /**
     * Render the editor settings panel (font, theme, editing behavior, …).
     * Default no-op.
     */
    @Composable
    fun EditorSettingsPanel(modifier: Modifier) {}

    /**
     * Render the LSP / language-server settings panel.
     * Default no-op.
     */
    @Composable
    fun LspSettingsPanel(modifier: Modifier) {}

    // ============================================================
    // AUTO SAVE
    // ============================================================

    /**
     * Whether the editor saves modified files automatically, as an observable the host can
     * render a checked state from.
     *
     * The setting belongs to the editor rather than the host: the host has no editor state to
     * apply it to, and `editor-settings.json` is rewritten wholesale by the editor's own
     * settings manager, so a host-owned copy there would be erased on the next settings change.
     *
     * Null when the installed editor-tab plugin predates this method, which is how a host built
     * against a newer API tells that it should hide the control rather than show a dead one.
     */
    fun autoSaveEnabled(): StateFlow<Boolean>? = null

    /**
     * Turn auto save on or off. No-op on plugins that predate this method - pair it with
     * [autoSaveEnabled] returning non-null before offering the control.
     */
    fun setAutoSaveEnabled(enabled: Boolean) {}

    // ============================================================
    // LIVE BUFFER MODEL (1.0.87, IDE scope decision D3)
    //
    // GATING, for every 1.0.87 member below: [EditorTabPluginAPI] is
    // @HostImplemented - the host compiles this interface in (EditorAPIAccess),
    // so these members resolve from the host's pinned copy, parent-first. A
    // plugin that references any of them gates on minBossVersion, not
    // minApiVersion - below the floor the validator rejects the WHOLE plugin
    // at load; it does not hand the caller a null to check. The "null when the
    // installed plugin predates this method" notes below are the OTHER axis:
    // member EXISTENCE tracks the host floor, member BEHAVIOUR tracks the
    // installed editor-tab plugin's version. A null above the floor means the
    // plugin is old, never that the call is unsupported.
    //
    // Invariants every implementation must hold:
    // - ONE BUFFER PER PATH: split panes and multiple tabs on the same path are
    //   viewports over one shared editor state, never copies.
    // - MONOTONIC VERSION: every buffer has a `version: Long` bumped on each
    //   document change; [readBuffer] and [focusedDocument] report it, and
    //   [applyEdit] takes expectedVersion and fails with reason
    //   [EditResult.REASON_STALE] on mismatch rather than mis-applying over
    //   newer content.
    // - UNDOABLE WRITES: [applyEdit] routes through the editor's undo manager, so
    //   an applied edit is one undo step. It operates on OPEN BUFFERS ONLY: a path
    //   with no buffer fails with applied=false, and writing that file is the
    //   caller's job (git is the undo there). An applied edit always reports a
    //   non-null newVersion.
    //
    //   This used to claim applyEdit wrote closed files directly and reported
    //   newVersion = null. No implementation ever did that, and callers that
    //   believed it - treating a null version as a closed-file success - would
    //   have gone on to chain edits against a version that does not exist.
    // ============================================================

    /**
     * Snapshot of the live buffer for [path], or null when the file has no open
     * buffer (then the caller falls back to disk).
     */
    suspend fun readBuffer(path: String): BufferSnapshot? = null

    /**
     * Apply a text edit to the live buffer at [path], replacing the range
     * ([startLine], [startCol])..([endLine], [endCol]) with [newText]. Line and
     * column are 1-based. Fails (applied=false) when the buffer is gone or
     * [expectedVersion] no longer matches; [EditResult.reason] names why
     * ([EditResult.REASON_STALE] is the version-mismatch case - the one a
     * caller retries after re-reading, as opposed to every other value,
     * which means give up).
     */
    suspend fun applyEdit(
        path: String,
        startLine: Int,
        startCol: Int,
        endLine: Int,
        endCol: Int,
        newText: String,
        expectedVersion: Long
    ): EditResult = EditResult(applied = false, reason = EditResult.REASON_UNSUPPORTED)

    /**
     * Observe buffer changes for [path] (one emission per document change), or
     * null when the installed editor-tab plugin predates this method - the
     * behaviour axis from the block above; the call itself is minBossVersion
     * gated, so a null here is never "this host is too old".
     *
     * A hot stream that does NOT complete on its own - collect it within a scope you
     * cancel when done, rather than waiting for completion. Emissions may be dropped
     * under load (the buffer keeps a bounded queue), so treat a change as "re-read via
     * [readBuffer]", not as a delta.
     *
     * Re-reading is a WHOLE-BUFFER round trip ([BufferSnapshot.content] is the full
     * text), and this flow fires on every keystroke - an out-of-process consumer
     * must debounce (conflate to the latest version, read once per quiet period)
     * rather than read per emission. A cheaper `readBufferRange`/content-hash skip
     * is deliberately left as a future member; [BufferChange] stays minimal so it
     * can arrive without reshaping this contract.
     */
    fun observeChanges(path: String): Flow<BufferChange>? = null

    /**
     * The document of the focused editor tab, with the current selection, or
     * null when no editor tab is focused.
     */
    fun focusedDocument(): FocusedDocument? = null

    /**
     * Open (or focus) an editor tab for [path], optionally at [line] (1-based).
     * Returns false when no editor tab can be opened in this context.
     */
    fun openEditor(path: String, line: Int? = null): Boolean = false

    /**
     * Open [path] in a split pane of the current editor tab (P3 implements the
     * rendering; the surface ships in 1.0.87 so one release carries it).
     * Same path = a second viewport over the shared buffer; different path = a
     * side-by-side comparison pair.
     */
    fun openSplit(path: String): Boolean = false
}

/**
 * A live-buffer snapshot (1.0.87). [version] is the buffer's monotonic change
 * version at read time - pass it back to [EditorTabPluginAPI.applyEdit] as
 * expectedVersion.
 */
@Serializable
data class BufferSnapshot(
    val path: String,
    val content: String,
    val version: Long,
    val isModified: Boolean
)

/**
 * The focused editor's document plus its selection (1.0.87). Selection ends
 * are null when the caret is not in a selection. Lines/columns are 1-based.
 */
@Serializable
data class FocusedDocument(
    val path: String,
    val content: String,
    val version: Long,
    val selectionStartLine: Int?,
    val selectionStartCol: Int?,
    val selectionEndLine: Int?,
    val selectionEndCol: Int?,
    val language: String
)

/**
 * Outcome of an [EditorTabPluginAPI.applyEdit] call (1.0.87).
 *
 * [newVersion] is the buffer's version after an APPLIED edit, so it is non-null
 * whenever [applied] is true - applyEdit operates on open buffers only (see the LIVE
 * BUFFER MODEL block above); there is no closed-file disk-write path that reports a
 * null version on success. [reason] is [REASON_STALE] for a version mismatch (the
 * retry-after-re-read case), otherwise a short human-readable failure.
 *
 * [reason] values cross this boundary as free strings, so the ONE that carries
 * control flow - [REASON_STALE] - is pinned as a const on the companion and a
 * test pins the literal, for the same reason
 * [AiRequest.EXTRAS_KEY_MODEL_OVERRIDE] is: a later rewording would silently
 * kill every `reason == REASON_STALE` retry loop. Callers must not
 * pattern-match on any other value.
 */
@Serializable
data class EditResult(
    val applied: Boolean,
    val newVersion: Long? = null,
    val reason: String? = null
) {
    companion object {
        /** [reason] for a version mismatch: re-read the buffer and retry. */
        const val REASON_STALE = "stale"

        /** [reason] for the default body: the host predates this member. */
        const val REASON_UNSUPPORTED = "unsupported"
    }
}

/**
 * One buffer change emission (1.0.87): the buffer's new [version]. The change
 * content itself is never carried - consumers re-read through readBuffer,
 * which keeps the flow small and the buffer the single source of truth.
 */
@Serializable
data class BufferChange(
    val path: String,
    val version: Long
)
