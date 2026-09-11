package ai.rever.boss.plugin.api

/**
 * Provider interface for secret management operations.
 *
 * This interface abstracts secret management functionality to allow
 * the SecretManager panel to be extracted to a separate module.
 */
interface SecretDataProvider {
    /**
     * Get all secrets for the current user with pagination.
     */
    suspend fun getUserSecrets(limit: Int = 50, offset: Int = 0): Result<PaginatedSecretsData>

    /**
     * Get secrets together with the server-authoritative organisation ownership and
     * management decision for each row.
     *
     * The default is deliberately fail-closed. An older host still returns the rows so
     * a consumer can render and copy them, but it must not infer an edit permission the
     * server did not publish. Hosts that support organisation secrets override this.
     */
    suspend fun getUserSecretsWithAccess(
        limit: Int = 50,
        offset: Int = 0
    ): Result<PaginatedSecretsWithAccessData> =
        getUserSecrets(limit, offset).map { page ->
            PaginatedSecretsWithAccessData(
                data = page.data.map(::SecretEntryWithAccessData),
                hasMore = page.hasMore
            )
        }

    /**
     * Get secrets with sharing information (for read-only panels like UserSecretList).
     */
    suspend fun getUserSecretsWithSharingInfo(limit: Int = 50, offset: Int = 0): Result<PaginatedSecretsWithSharingData>

    /**
     * Sharing rows plus organisation ownership. Kept additive so the published
     * [SecretEntryWithSharingData] constructor remains binary compatible.
     */
    suspend fun getUserSecretsWithSharingAccess(
        limit: Int = 50,
        offset: Int = 0
    ): Result<PaginatedSecretsWithSharingAccessData> =
        getUserSecretsWithSharingInfo(limit, offset).map { page ->
            PaginatedSecretsWithSharingAccessData(
                data = page.data.map(::SecretEntryWithSharingAccessData),
                hasMore = page.hasMore
            )
        }

    /**
     * Search secrets by query.
     */
    suspend fun searchSecrets(query: String, limit: Int = 50, offset: Int = 0): Result<PaginatedSecretsData>

    /** Search equivalent of [getUserSecretsWithAccess], with the same fail-closed fallback. */
    suspend fun searchSecretsWithAccess(
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): Result<PaginatedSecretsWithAccessData> =
        searchSecrets(query, limit, offset).map { page ->
            PaginatedSecretsWithAccessData(
                data = page.data.map(::SecretEntryWithAccessData),
                hasMore = page.hasMore
            )
        }

    /**
     * Create a new secret.
     */
    suspend fun createSecret(request: CreateSecretRequestData): Result<Unit>

    /**
     * Update an existing secret.
     */
    suspend fun updateSecret(request: UpdateSecretRequestData): Result<Unit>

    /**
     * Delete a secret by ID.
     */
    suspend fun deleteSecret(id: String): Result<Unit>

    /**
     * Get shares for a specific secret.
     */
    suspend fun getSecretShares(secretId: String): Result<List<SecretShareData>>

    /**
     * Get share rows with their complete target identity. Organisation targets were added
     * after [SecretShareData] shipped, so this additive envelope preserves that binary
     * contract while allowing new consumers to label an organisation share correctly.
     */
    suspend fun getSecretSharesWithTargets(secretId: String): Result<List<SecretShareWithTargetData>> =
        getSecretShares(secretId).map { shares -> shares.map(::SecretShareWithTargetData) }

    /**
     * Share a secret with a user or role.
     */
    suspend fun shareSecret(request: ShareSecretRequestData): Result<Unit>

    /**
     * Remove sharing for a secret.
     */
    suspend fun unshareSecret(request: UnshareSecretRequestData): Result<Unit>
}

/**
 * Paginated result for secret queries.
 */
data class PaginatedSecretsData(
    val data: List<SecretEntryData>,
    val hasMore: Boolean
)

/**
 * Data class representing a secret entry.
 */
data class SecretEntryData(
    val id: String,
    val website: String,
    val username: String,
    val password: String,
    val notes: String? = null,
    val expirationDate: String? = null,
    val tags: List<String> = emptyList(),
    val metadata: SecretMetadataData? = null,
    val createdAt: String,
    val updatedAt: String
)

/**
 * A secret plus the server-authoritative decision about who owns and may manage it.
 *
 * This is a new envelope rather than new constructor fields on [SecretEntryData]: plugin
 * API data classes are binary contracts and must never gain constructor components.
 */
data class SecretEntryWithAccessData(
    val secret: SecretEntryData,
    val orgId: String? = null,
    val orgSlug: String? = null,
    val isOrgOwned: Boolean = false,
    val canManage: Boolean = false
)

/** Paginated result for [SecretDataProvider.getUserSecretsWithAccess]. */
data class PaginatedSecretsWithAccessData(
    val data: List<SecretEntryWithAccessData>,
    val hasMore: Boolean
)

/**
 * Metadata for a secret (2FA information).
 */
data class SecretMetadataData(
    val twofaEnabled: Boolean = false,
    val twofaType: String? = null,
    val twofaSecret: String? = null,
    val recoveryCodes: List<String> = emptyList()
)

/**
 * Request data for creating a new secret.
 */
data class CreateSecretRequestData(
    val website: String,
    val username: String,
    val password: String,
    val notes: String? = null,
    val expirationDate: String? = null,
    val tags: List<String> = emptyList(),
    val twofaEnabled: Boolean = false,
    val twofaType: String? = null,
    val recoveryCodes: List<String> = emptyList()
)

/**
 * Request data for updating a secret.
 */
data class UpdateSecretRequestData(
    val secretId: String,
    val website: String,
    val username: String,
    val password: String,
    val notes: String? = null,
    val expirationDate: String? = null,
    val tags: List<String> = emptyList(),
    val twofaEnabled: Boolean = false,
    val twofaType: String? = null,
    val recoveryCodes: List<String> = emptyList()
)

/**
 * Request data for sharing a secret.
 */
data class ShareSecretRequestData(
    val secretId: String,
    val targetUserId: String? = null,
    val targetRoleId: String? = null,
    val notes: String? = null,
    val expiresAt: String? = null
)

/**
 * Request data for unsharing a secret.
 */
data class UnshareSecretRequestData(
    val secretId: String,
    val targetUserId: String? = null,
    val targetRoleId: String? = null
)

/**
 * Data class representing a secret share.
 */
data class SecretShareData(
    val shareId: String,
    val sharedWithUserId: String? = null,
    val sharedWithUserEmail: String? = null,
    val sharedWithRoleId: String? = null,
    val sharedWithRoleName: String? = null,
    val accessLevel: String,
    val sharedByEmail: String,
    val createdAt: String,
    val expiresAt: String? = null,
    val notes: String? = null
)

/**
 * A share plus an organisation target, when the share was granted to an organisation.
 */
data class SecretShareWithTargetData(
    val share: SecretShareData,
    val sharedWithOrgId: String? = null,
    val sharedWithOrgSlug: String? = null
)

/**
 * Paginated result for secrets with sharing information.
 */
data class PaginatedSecretsWithSharingData(
    val data: List<SecretEntryWithSharingData>,
    val hasMore: Boolean
)

/**
 * Secret entry with ownership and sharing information.
 * Used for read-only views (UserSecretList panel).
 */
data class SecretEntryWithSharingData(
    val id: String,
    val website: String,
    val username: String,
    val password: String,
    val notes: String? = null,
    val expirationDate: String? = null,
    val tags: List<String> = emptyList(),
    val metadata: SecretMetadataData? = null,
    val createdAt: String,
    val updatedAt: String,
    val isOwner: Boolean,
    val sharedByEmail: String? = null,
    val accessLevel: String
)

/** A sharing row plus server-published organisation ownership and management access. */
data class SecretEntryWithSharingAccessData(
    val secret: SecretEntryWithSharingData,
    val orgId: String? = null,
    val orgSlug: String? = null,
    val isOrgOwned: Boolean = false,
    val canManage: Boolean = false
)

/** Paginated result for [SecretDataProvider.getUserSecretsWithSharingAccess]. */
data class PaginatedSecretsWithSharingAccessData(
    val data: List<SecretEntryWithSharingAccessData>,
    val hasMore: Boolean
)
