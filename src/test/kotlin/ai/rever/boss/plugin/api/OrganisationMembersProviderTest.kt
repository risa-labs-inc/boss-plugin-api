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
 * It is what makes risa-labs-inc/boss-plugin-api#42 additive: a host compiled
 * against an earlier API does not override it, so the property has to answer
 * null rather than reaching an abstract member and throwing AbstractMethodError
 * at the first plugin that asks.
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
    fun `a host that does not implement the provider answers null rather than throwing`() {
        // The whole additive claim rests on this. A PluginContext compiled before
        // #42 overrides nothing here, so the property has to resolve to a default
        // that answers null instead of reaching an abstract member.
        //
        // Asserted through PluginContext$DefaultImpls rather than a stub context
        // deliberately. That static IS the compatibility guarantee: it is what an
        // already-compiled host links against, and it is the line apiCheck records.
        // Building a stub would instead prove that a class compiled TODAY works,
        // which was never in doubt, and would need seven unrelated members
        // (panelRegistry, tabRegistry, pluginScope and the health callbacks) whose
        // churn would then break this test for reasons unrelated to #42.
        // The receiver is a Proxy rather than null because Kotlin emits an
        // Intrinsics null check on the synthetic `$this` parameter. The proxy
        // never has a method called on it: a `get() = null` default ignores its
        // receiver entirely, which is the point.
        val olderHost =
            java.lang.reflect.Proxy.newProxyInstance(
                PluginContext::class.java.classLoader,
                arrayOf(PluginContext::class.java),
            ) { _, method, _ -> error("the default must not call back into the host, but called ${method.name}") }

        val defaults = Class.forName("ai.rever.boss.plugin.api.PluginContext\$DefaultImpls")
        val getter = defaults.getMethod("getOrganisationMembersProvider", PluginContext::class.java)

        assertNull(
            getter.invoke(null, olderHost),
            "the default getter must answer null for a host that does not override it",
        )
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
