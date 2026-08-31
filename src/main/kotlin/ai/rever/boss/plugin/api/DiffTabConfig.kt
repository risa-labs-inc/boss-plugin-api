package ai.rever.boss.plugin.api

/**
 * What a diff tab is showing, readable by the plugin that renders it (1.0.87).
 *
 * The diff tab's config type lives in the host's `plugin-api-core`, which no
 * plugin compiles against - so a plugin-side renderer received a config whose
 * scope fields it could not read. That is the whole reason the diff tab was
 * built in the host, where it has no access to the editor's lexer, language
 * servers or overview ruler.
 *
 * The host's `DiffTabInfo` implements this, so the renderer can move to the
 * editor-tab plugin and become what it always should have been: a variation
 * of the editor tab.
 *
 * An interface rather than a data class on purpose: the host owns the tab
 * config (it has to persist and restore it), and this is only the read side
 * of that contract.
 */
interface DiffTabConfig {
    /** Project-relative path, or empty for a whole-commit / ref-range diff. */
    val filePath: String

    /** Compare the index against HEAD rather than the working tree. */
    val staged: Boolean

    /** Left-hand ref; null for a working-tree or index diff. */
    val fromRef: String?

    /** Right-hand ref; null unless this is a ref-range diff. */
    val toRef: String?
}
