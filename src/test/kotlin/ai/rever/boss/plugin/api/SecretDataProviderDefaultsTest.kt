package ai.rever.boss.plugin.api

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
        assertFalse(row.isOrgOwned)
        assertFalse(row.canManage)
    }

    private class LegacySecretDataProvider : SecretDataProvider {
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
    }
}
