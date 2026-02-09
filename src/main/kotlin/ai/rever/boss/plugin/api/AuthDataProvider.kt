package ai.rever.boss.plugin.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Provider interface for authentication state.
 *
 * This interface abstracts authentication functionality to allow
 * auth-dependent panels to be extracted to separate modules.
 */
interface AuthDataProvider {
    /**
     * Current authenticated user, or null if not authenticated.
     */
    val currentUser: StateFlow<UserData?>

    /**
     * Whether the current user has admin role.
     */
    val isAdmin: StateFlow<Boolean>

    /**
     * Check if the current user has a specific permission.
     */
    fun hasPermission(permission: String): Boolean

    /**
     * Check if the current user has any of the specified permissions.
     */
    fun hasAnyPermission(vararg permissions: String): Boolean

    /**
     * Get all permissions for the current user.
     */
    val userPermissions: StateFlow<Set<String>>
}

/**
 * Data class representing an authenticated user.
 */
data class UserData(
    val id: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val roles: List<String>,
    val createdAt: Long
)

/**
 * Provider interface for user management operations (admin only).
 */
interface UserManagementProvider {
    /**
     * Get all users with their roles (admin only).
     */
    suspend fun getAllUsersWithRoles(limit: Int = 50, offset: Int = 0): Result<PaginatedUsersData>

    /**
     * Search users by email (admin only).
     */
    suspend fun searchUsersByEmail(query: String, limit: Int = 50, offset: Int = 0): Result<PaginatedUsersData>

    /**
     * Assign a role to a user (admin only).
     */
    suspend fun assignRole(userId: String, roleName: String): Result<Unit>

    /**
     * Remove a role from a user (admin only).
     */
    suspend fun removeRole(userId: String, roleName: String): Result<Unit>

    /**
     * Delete a user (admin only).
     */
    suspend fun deleteUser(userId: String): Result<Unit>

    /**
     * Get all available roles.
     */
    suspend fun getAllRoles(): Result<List<RoleInfoData>>
}

/**
 * Paginated result for user queries.
 */
data class PaginatedUsersData(
    val data: List<UserWithRolesData>,
    val hasMore: Boolean
)

/**
 * Data class representing a user with their roles.
 */
data class UserWithRolesData(
    val id: String,
    val email: String,
    val displayName: String?,
    val roles: List<String>,
    val createdAt: Long,
    val lastSignIn: Long?
)

/**
 * Data class representing a role.
 */
data class RoleInfoData(
    val id: String,
    val name: String,
    val description: String?,
    val permissions: List<String>,
    val createdAt: Long,
    val isSystem: Boolean = false
)

/**
 * Data class representing a permission.
 */
data class PermissionInfoData(
    val id: String,
    val name: String,
    val description: String?,
    val createdAt: Long,
    val isSystem: Boolean = false
)

/**
 * Data class representing a role with its assigned permissions.
 */
data class RoleWithPermissionsData(
    val roleName: String,
    val permissions: List<String> = emptyList()
)

/**
 * Provider interface for role and permission management (admin only).
 */
interface RoleManagementProvider {
    /**
     * Get all roles.
     */
    suspend fun getAllRoles(): Result<List<RoleInfoData>>

    /**
     * Get all permissions.
     */
    suspend fun getAllPermissions(): Result<List<PermissionInfoData>>

    /**
     * Create a new role.
     */
    suspend fun createRole(name: String, description: String?): Result<RoleInfoData>

    /**
     * Create a new permission.
     */
    suspend fun createPermission(name: String, description: String?): Result<PermissionInfoData>

    /**
     * Delete a role by name.
     */
    suspend fun deleteRole(roleName: String): Result<Unit>

    /**
     * Delete a permission by name.
     */
    suspend fun deletePermission(permissionName: String): Result<Unit>

    /**
     * Assign a permission to a role by names.
     */
    suspend fun assignPermissionToRole(roleName: String, permissionName: String): Result<Unit>

    /**
     * Remove a permission from a role by names.
     */
    suspend fun removePermissionFromRole(roleName: String, permissionName: String): Result<Unit>

    /**
     * Get all permissions assigned to a specific role.
     */
    suspend fun getRolePermissions(roleName: String): Result<RoleWithPermissionsData>

    /**
     * Validate role name format (client-side validation).
     * @return Error message if invalid, null if valid
     */
    fun validateRoleName(roleName: String): String?

    /**
     * Validate permission name format (client-side validation).
     * @return Error message if invalid, null if valid
     */
    fun validatePermissionName(permissionName: String): String?
}

/**
 * Provider interface for Plugin Store API key management.
 *
 * This allows plugins to create, list, and revoke API keys for
 * CI/CD publishing workflows.
 */
interface PluginStoreApiKeyProvider {
    /**
     * Create a new API key.
     *
     * @param name Display name for the key
     * @param scopes List of scopes (e.g., "publish", "version", "finalize")
     * @param expiresInDays Optional expiration in days (null = never expires)
     * @return Result containing the full API key (shown only once) and key info
     */
    suspend fun createApiKey(
        name: String,
        scopes: List<String> = listOf("publish", "version", "finalize"),
        expiresInDays: Int? = null
    ): Result<ApiKeyCreationResult>

    /**
     * List all API keys for the current user.
     * Note: Full keys are never returned, only prefixes.
     */
    suspend fun listApiKeys(): Result<List<ApiKeyInfo>>

    /**
     * Revoke an API key.
     *
     * @param keyId The UUID of the key to revoke
     */
    suspend fun revokeApiKey(keyId: String): Result<Unit>

    /**
     * Check if the current user can manage API keys.
     * Returns true if user is admin or has plugin_admin role.
     */
    suspend fun canManageApiKeys(): Boolean
}

/**
 * Result of creating an API key.
 */
data class ApiKeyCreationResult(
    /** The full API key - only shown once! */
    val apiKey: String,
    /** Information about the created key */
    val keyInfo: ApiKeyInfo
)

/**
 * Information about an API key (without the actual key).
 */
data class ApiKeyInfo(
    val id: String,
    val name: String,
    /** First part of the key for identification (e.g., "boss_pk_a1B2c3D4") */
    val keyPrefix: String,
    val scopes: List<String>,
    val createdAt: Long,
    val lastUsedAt: Long?,
    val expiresAt: Long?,
    val isRevoked: Boolean
)
