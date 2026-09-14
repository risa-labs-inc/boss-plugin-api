package ai.rever.boss.plugin.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `listCoMembers`' default arguments are part of the contract: an implementor
 * sees them verbatim and `apiCheck` cannot see a changed default, so a tidy-up
 * that moves the limit or makes the query non-null would silently change what
 * every existing caller gets. Both are pinned here.
 *
 * The `organisationMembersProvider` default is pinned for a different reason.
 * A host exposing this API can omit the override and inherit a JVM default.
 * This does not test older hosts that load their own older PluginContext;
 * consumers still need the minBossVersion gate documented on the property.
 */
class OrganisationMembersProviderTest {

    private val member =
        CoMember(
            userId = "u1",
            email = "someone@example.com",
            displayName = "Someone",
            avatarUrl = null,
            organisationIds = listOf("org-a"),
        )

    @Test
    fun `listCoMembers defaults are no filter and a limit of 50`() {
        var seenQuery: String? = "unset"
        var seenLimit = -1
        val provider =
            object : OrganisationMembersProvider {
                override suspend fun listCoMembers(
                    query: String?,
                    limit: Int,
                ): Result<List<CoMember>> {
                    seenQuery = query
                    seenLimit = limit
                    return Result.success(listOf(member))
                }
            }

        runBlocking { provider.listCoMembers() }

        assertNull(seenQuery, "a defaulted query must mean no filter, not an empty-string filter")
        assertEquals(
            OrganisationMembersProvider.DEFAULT_LIMIT,
            seenLimit,
            "the defaulted limit must be the published constant, not a second copy of 50",
        )
        assertEquals(50, OrganisationMembersProvider.DEFAULT_LIMIT)
    }

    @Test
    fun `the provider getter is a JVM default returning null`() {
        val getter = PluginContext::class.java.getMethod("getOrganisationMembersProvider")
        assertTrue(getter.isDefault, "old implementations need a JVM default, not only DefaultImpls")

        // Invoke the actual interface default through normal proxy dispatch.
        // Calling DefaultImpls directly would still pass if JVM defaults were disabled.
        val context =
            java.lang.reflect.Proxy.newProxyInstance(
                PluginContext::class.java.classLoader,
                arrayOf(PluginContext::class.java),
            ) { proxy, method, _ ->
                check(method == getter) { "unexpected callback: ${method.name}" }
                java.lang.reflect.InvocationHandler.invokeDefault(proxy, method)
            } as PluginContext

        assertNull(context.organisationMembersProvider)

        // Keep coverage for Kotlin's compatibility bridge as well.
        val defaults = Class.forName("ai.rever.boss.plugin.api.PluginContext\$DefaultImpls")
        val bridge = defaults.getMethod("getOrganisationMembersProvider", PluginContext::class.java)
        assertNull(bridge.invoke(null, context))
    }

    @Test
    fun `a co-member carries identity without carrying authority`() {
        // The point of the type. UserData exists for the current user and carries
        // roles; this one is handed to a plugin about OTHER people, so it must not
        // grow a permissions or credential field. If this test has to change, that
        // is the decision being made.
        // Java reflection, not `::class.members`: kotlin-reflect is not on the
        // test runtime classpath here.
        val fields = CoMember::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fields.containsAll(setOf("userId", "email", "displayName", "avatarUrl", "organisationIds")))
        for (forbidden in listOf("roles", "permissions", "apiKey", "token", "accessToken", "secret")) {
            assertTrue(forbidden !in fields, "CoMember must not carry $forbidden")
        }
    }

    @Test
    fun `no co-members is a success, not a failure`() {
        // Pins the KDoc's distinction: an authenticated user who simply shares no
        // organisation with anyone is an empty list. Folding that into a failure
        // would make every picker show an error state on a legitimate account.
        val provider =
            object : OrganisationMembersProvider {
                override suspend fun listCoMembers(
                    query: String?,
                    limit: Int,
                ): Result<List<CoMember>> = Result.success(emptyList())
            }

        val result = runBlocking { provider.listCoMembers(query = "nobody") }

        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrNull())
    }
}
