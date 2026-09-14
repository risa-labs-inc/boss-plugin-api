package ai.rever.boss.plugin.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecretDataProviderDefaultsTest {
    @Test
    fun `legacy secret pages remain readable but management fails closed`() = runBlocking {
        val provider = LegacySecretDataProvider()

        val page = provider.getUserSecretsWithAccess(limit = 7, offset = 3).getOrThrow()

        assertEquals(listOf("legacy"), page.data.map { it.secret.id })
        assertFalse(page.data.single().canManage)
        assertFalse(page.data.single().isOrgOwned)
        assertNull(page.data.single().orgId)
        assertNull(page.data.single().orgSlug)
        assertFalse(page.hasMore)
        assertEquals(7 to 3, provider.lastPage)
    }

    @Test
    fun `legacy search keeps paging arguments and fails management closed`() = runBlocking {
        val provider = LegacySecretDataProvider()

        val page = provider.searchSecretsWithAccess("needle", limit = 9, offset = 4).getOrThrow()

        assertEquals("legacy", page.data.single().secret.id)
        assertFalse(page.data.single().canManage)
        assertEquals(Triple("needle", 9, 4), provider.lastSearch)
    }

    @Test
    fun `legacy share rows remain readable with no invented organisation target`() = runBlocking {
        val share = LegacySecretDataProvider().getSecretSharesWithTargets("s1").getOrThrow().single()

        assertEquals("share-1", share.share.shareId)
        assertNull(share.sharedWithOrgId)
        assertNull(share.sharedWithOrgSlug)
    }

    @Test
    fun `legacy sharing pages remain readable but organisation access fails closed`() = runBlocking {
        val page = LegacySecretDataProvider().getUserSecretsWithSharingAccess(5, 2).getOrThrow()
        val row = page.data.single()

        assertEquals("legacy", row.secret.id)
        assertNull(row.orgId)
        assertNull(row.orgSlug)
        assertNull(row.sharedWithOrgSlug)
        assertFalse(row.isOrgOwned)
        assertFalse(row.canManage)
    }

    @Test
    fun `sharing access envelope retains organisation share attribution`() {
        val row =
            SecretEntryWithSharingAccessData(
                secret = sharingSecret(),
                sharedWithOrgSlug = "partner-org",
            )

        assertEquals("partner-org", row.sharedWithOrgSlug)
        assertNull(row.orgId)
        assertNull(row.orgSlug)
    }

    @Test
    fun `all access defaults preserve backend failures`() = runBlocking {
        val failure = IllegalStateException("backend unavailable")
        val provider = object : LegacySecretDataProvider() {
            override suspend fun getUserSecrets(limit: Int, offset: Int): Result<PaginatedSecretsData> =
                Result.failure(failure)
            override suspend fun searchSecrets(query: String, limit: Int, offset: Int): Result<PaginatedSecretsData> =
                Result.failure(failure)
            override suspend fun getUserSecretsWithSharingInfo(limit: Int, offset: Int): Result<PaginatedSecretsWithSharingData> =
                Result.failure(failure)
            override suspend fun getSecretShares(secretId: String): Result<List<SecretShareData>> =
                Result.failure(failure)
        }
        assertSame(failure, provider.getUserSecretsWithAccess().exceptionOrNull())
        assertSame(failure, provider.searchSecretsWithAccess("query").exceptionOrNull())
        assertSame(failure, provider.getUserSecretsWithSharingAccess().exceptionOrNull())
        assertSame(failure, provider.getSecretSharesWithTargets("secret").exceptionOrNull())
    }

    @Test
    fun `access defaults do not swallow coroutine cancellation`() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val provider = object : LegacySecretDataProvider() {
            override suspend fun getUserSecrets(limit: Int, offset: Int): Result<PaginatedSecretsData> = throw cancellation
            override suspend fun searchSecrets(query: String, limit: Int, offset: Int): Result<PaginatedSecretsData> = throw cancellation
            override suspend fun getUserSecretsWithSharingInfo(limit: Int, offset: Int): Result<PaginatedSecretsWithSharingData> = throw cancellation
            override suspend fun getSecretShares(secretId: String): Result<List<SecretShareData>> = throw cancellation
        }
        assertSame(cancellation, assertFailsWith<CancellationException> { provider.getUserSecretsWithAccess() })
        assertSame(cancellation, assertFailsWith<CancellationException> { provider.searchSecretsWithAccess("query") })
        assertSame(cancellation, assertFailsWith<CancellationException> { provider.getUserSecretsWithSharingAccess() })
        assertSame(cancellation, assertFailsWith<CancellationException> { provider.getSecretSharesWithTargets("secret") })
    }

    @Test
    fun `legacy owner rows cannot grant management and sharing pagination survives`() = runBlocking {
        val owner = sharingSecret().copy(isOwner = true, accessLevel = "owner")
        val provider = object : LegacySecretDataProvider() {
            override suspend fun getUserSecretsWithSharingInfo(limit: Int, offset: Int): Result<PaginatedSecretsWithSharingData> {
                assertEquals(3 to 8, limit to offset)
                return Result.success(PaginatedSecretsWithSharingData(listOf(owner), hasMore = true))
            }
        }
        val page = provider.getUserSecretsWithSharingAccess(3, 8).getOrThrow()
        assertTrue(page.hasMore)
        assertSame(owner, page.data.single().secret)
        assertFalse(page.data.single().canManage)
    }

    @Test
    fun `empty pages with continuation retain pagination`() = runBlocking {
        val provider = object : LegacySecretDataProvider() {
            override suspend fun getUserSecrets(limit: Int, offset: Int): Result<PaginatedSecretsData> =
                Result.success(PaginatedSecretsData(emptyList(), hasMore = true))
            override suspend fun searchSecrets(query: String, limit: Int, offset: Int): Result<PaginatedSecretsData> =
                Result.success(PaginatedSecretsData(emptyList(), hasMore = true))
        }
        for (page in listOf(provider.getUserSecretsWithAccess().getOrThrow(), provider.searchSecretsWithAccess("query").getOrThrow())) {
            assertTrue(page.hasMore)
            assertTrue(page.data.isEmpty())
        }
    }

    @Test
    fun `access members have real JVM defaults for legacy implementations`() {
        for (name in listOf("getUserSecretsWithAccess", "searchSecretsWithAccess", "getUserSecretsWithSharingAccess", "getSecretSharesWithTargets")) {
            val method = SecretDataProvider::class.java.declaredMethods.single { it.name.startsWith("$name-") && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            assertTrue(method.isDefault, "$name must remain a JVM default method")
        }
    }

    private open class LegacySecretDataProvider : SecretDataProvider {
        var lastPage: Pair<Int, Int>? = null
        var lastSearch: Triple<String, Int, Int>? = null

        override suspend fun getUserSecrets(limit: Int, offset: Int): Result<PaginatedSecretsData> {
            lastPage = limit to offset
            return Result.success(PaginatedSecretsData(listOf(secret()), hasMore = false))
        }

        override suspend fun searchSecrets(
            query: String,
            limit: Int,
            offset: Int
        ): Result<PaginatedSecretsData> {
            lastSearch = Triple(query, limit, offset)
            return Result.success(PaginatedSecretsData(listOf(secret()), hasMore = false))
        }

        override suspend fun getSecretShares(secretId: String): Result<List<SecretShareData>> =
            Result.success(
                listOf(
                    SecretShareData(
                        shareId = "share-1",
                        accessLevel = "read",
                        sharedByEmail = "owner@example.com",
                        createdAt = "now"
                    )
                )
            )

        override suspend fun getUserSecretsWithSharingInfo(
            limit: Int,
            offset: Int
        ): Result<PaginatedSecretsWithSharingData> =
            Result.success(
                PaginatedSecretsWithSharingData(
                    listOf(
                        SecretEntryWithSharingData(
                            id = "legacy",
                            website = "example.com",
                            username = "user",
                            password = "secret",
                            createdAt = "then",
                            updatedAt = "now",
                            isOwner = false,
                            accessLevel = "read"
                        )
                    ),
                    hasMore = false
                )
            )

        override suspend fun createSecret(request: CreateSecretRequestData): Result<Unit> = error("unused")
        override suspend fun updateSecret(request: UpdateSecretRequestData): Result<Unit> = error("unused")
        override suspend fun deleteSecret(id: String): Result<Unit> = error("unused")
        override suspend fun shareSecret(request: ShareSecretRequestData): Result<Unit> = error("unused")
        override suspend fun unshareSecret(request: UnshareSecretRequestData): Result<Unit> = error("unused")
    }

    private companion object {
        fun secret() =
            SecretEntryData(
                id = "legacy",
                website = "example.com",
                username = "user",
                password = "secret",
                createdAt = "then",
                updatedAt = "now"
            )

        fun sharingSecret() =
            SecretEntryWithSharingData(
                id = "legacy",
                website = "example.com",
                username = "user",
                password = "secret",
                createdAt = "then",
                updatedAt = "now",
                isOwner = false,
                accessLevel = "read"
            )
    }
}
