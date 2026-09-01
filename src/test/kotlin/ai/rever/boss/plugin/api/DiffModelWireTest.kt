package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wire story of [DiffLineKind.UNKNOWN]: an older consumer must decode a
 * FUTURE kind into UNKNOWN instead of throwing and taking the whole diff down.
 * That rests on a non-obvious kotlinx interaction - `coerceInputValues` only
 * coerces an unknown enum name when the property is OPTIONAL (has a default),
 * and [DiffLine.kind] is the property that has one. Pinning the combination,
 * because a tidy-up that drops the default silently re-arms the crash.
 */
class DiffModelWireTest {

    private val wireJson = Json { coerceInputValues = true }

    @Test
    fun `an unknown kind decodes to UNKNOWN instead of throwing`() {
        val decoded = wireJson.decodeFromString(DiffLine.serializer(), """{"kind":"MOVED","text":"x"}""")

        assertEquals(DiffLineKind.UNKNOWN, decoded.kind)
        assertEquals("x", decoded.text)
        // The known kinds still decode to themselves, so the coercion is
        // unknown-only, not a blanket remap.
        assertEquals(DiffLineKind.ADDED, wireJson.decodeFromString(DiffLine.serializer(), """{"kind":"ADDED","text":"x"}""").kind)
    }

    @Test
    fun `without the property default the same payload is a hard failure - that is the bug UNKNOWN prevents`() {
        // The control case: the coercion has nowhere to land, so decoding
        // throws. This is exactly the failure an older consumer would hit on
        // the first added kind if DiffLine.kind had no default.
        assertTrue(
            runCatching {
                wireJson.decodeFromString(RequiredKind.serializer(), """{"kind":"MOVED"}""")
            }.isFailure
        )
    }

    /** A kind property with NO default: the shape the coercion cannot save. */
    @Serializable
    private data class RequiredKind(val kind: DiffLineKind)
}
