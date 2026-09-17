package ai.rever.boss.plugin.bookmark

import kotlin.test.Test
import kotlin.test.assertEquals
import java.lang.reflect.Modifier

/** Host and SDK copies must both provide fields emitted into consuming Compose plugins. */
class BookmarkLibraryLinkageTest {
    @Test fun `new bookmark models expose public static Compose stability fields`() {
        listOf(BookmarkLibraryState::class.java, BookmarkSaveRequest::class.java,
            BookmarkMutationResult::class.java, BookmarkOpenResult::class.java).forEach { type ->
            val field = type.getField("\$stable")
            assertEquals(Int::class.javaPrimitiveType, field.type, type.name)
            assertEquals(true, Modifier.isStatic(field.modifiers), type.name)
        }
    }
}
