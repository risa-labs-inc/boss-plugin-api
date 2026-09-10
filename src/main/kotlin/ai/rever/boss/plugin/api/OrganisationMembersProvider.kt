package ai.rever.boss.plugin.api

/**
 * A person the current user shares at least one organisation with.
 *
 * Deliberately narrower than [UserData]. The gap this closes is that the only
 * existing routes to another person's identity hand over more than a recipient
 * picker needs: `userManagementProvider` is the admin directory and carries
 * roles, and `supabaseDataProvider` is a raw query escape hatch. A plugin
 * building a "send to teammate" list needs a name, an address to show, and an
 * id to send to, so this carries exactly that and no permissions, roles or
 * credentials.
 *
 * [organisationIds] is the set of organisations the caller and this person are
 * both in, not everything this person belongs to: it exists so a picker can
 * group or label its rows, and telling the caller about organisations they are
 * not a member of would leak the thing the provider is meant to avoid.
 */
data class CoMember(
    val userId: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val organisationIds: List<String>,
)

/**
 * Read access to the people the current user shares an organisation with.
 *
 * Every sharing or recipient-picker feature needs the same question answered:
 * "who are my active co-members, across every organisation I belong to,
 * deduplicated, excluding me". Without somewhere shared to ask it, each plugin
 * ends up adding its own `SECURITY DEFINER` RPC and migration to do the same
 * join. The screenshot-share plugin already did exactly that with
 * `list_shareable_recipients`, which is the evidence in
 * risa-labs-inc/boss-plugin-api#42 that this belongs here rather than in one
 * plugin.
 *
 * Implementors are expected to enforce that scoping server-side. The caller is
 * a plugin, so the answer must not depend on the plugin asking nicely: dedupe
 * by user id, return only active memberships, and exclude the caller.
 */
interface OrganisationMembersProvider {
    /**
     * Active co-members across every organisation the current user belongs to.
     *
     * @param query optional case-insensitive filter over display name and
     *   email. Null or blank returns everyone, subject to [limit].
     * @param limit maximum rows to return. Implementors may cap this lower.
     * @return the matching co-members, or a failure when the lookup could not
     *   be performed. An authenticated user with no co-members is a success
     *   carrying an empty list, not a failure.
     */
    suspend fun listCoMembers(
        query: String? = null,
        limit: Int = DEFAULT_LIMIT,
    ): Result<List<CoMember>>

    companion object {
        /**
         * Default page size, matching `getAllUsersWithRoles` and
         * `getUserSecrets` so callers moving between them are not surprised.
         */
        const val DEFAULT_LIMIT: Int = 50
    }
}
