package ai.rever.boss.plugin.api

/**
 * Generic Supabase data provider for plugins.
 *
 * Allows plugins to execute Postgrest `select` queries and `rpc` calls
 * using JSON strings for data exchange, without importing the Supabase SDK.
 *
 * Only read operations (select) and server-side functions (rpc) are exposed.
 * All mutations should go through RPC functions that enforce RLS and business logic.
 */
interface SupabaseDataProvider {
    /**
     * Execute a Postgrest SELECT query on a table.
     *
     * @param table The table name to query
     * @param columns Column selection string (e.g., "*", "id,email,name")
     * @param filters List of filters to apply
     * @param range Optional pagination range (inclusive)
     * @return Result containing a JSON array string of matching rows
     */
    suspend fun select(
        table: String,
        columns: String = "*",
        filters: List<QueryFilter> = emptyList(),
        range: QueryRange? = null
    ): Result<String>

    /**
     * Execute a Postgrest RPC call to a server-side function.
     *
     * @param function The function name to call
     * @param parameters JSON string of function parameters (default: "{}")
     * @return Result containing the JSON response string
     */
    suspend fun rpc(
        function: String,
        parameters: String = "{}"
    ): Result<String>
}

/**
 * A single filter condition for a query.
 */
data class QueryFilter(val column: String, val operator: FilterOperator, val value: String)

/**
 * Supported filter operators for Postgrest queries.
 */
enum class FilterOperator {
    EQ, NEQ, GT, GTE, LT, LTE, LIKE, ILIKE, IN, IS
}

/**
 * Pagination range for queries (inclusive on both ends).
 */
data class QueryRange(val from: Long, val to: Long)
