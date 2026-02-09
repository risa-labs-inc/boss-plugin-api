package ai.rever.boss.plugin.api

import kotlinx.serialization.Serializable

/**
 * Semantic version representation with comparison support.
 * Follows semver spec: major.minor.patch[-prerelease]
 *
 * ## Prerelease Ordering
 * Known types are ordered: alpha < beta < rc < stable (no prerelease)
 *
 * Unknown prerelease types (e.g., "nightly", "dev", "snapshot") are treated as
 * greater than "rc" but less than stable. This means:
 * - `8.16.28-nightly.1` > `8.16.28-rc.1`
 * - `8.16.28-nightly.1` < `8.16.28` (stable)
 * - Unknown types are compared lexicographically among themselves
 */
@Serializable
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<Version> {

    companion object {
        /**
         * Ordering for known prerelease types.
         * Unknown types get ordinal 99 (greater than rc but less than stable).
         */
        private val PRERELEASE_ORDER = mapOf(
            "alpha" to 0,
            "beta" to 1,
            "rc" to 2
        )

        /**
         * Parse a version string into a Version object.
         * Accepts formats like "8.16.28", "v8.16.28", "8.16.28-alpha.1"
         *
         * @return Version object or null if parsing fails
         */
        fun parse(versionString: String): Version? {
            return try {
                val cleanVersion = versionString.removePrefix("v").trim()
                val parts = cleanVersion.split("-", limit = 2)
                val versionPart = parts[0]
                val preRelease = parts.getOrNull(1)

                val versionNumbers = versionPart.split(".")
                if (versionNumbers.size >= 3) {
                    Version(
                        major = versionNumbers[0].toInt(),
                        minor = versionNumbers[1].toInt(),
                        patch = versionNumbers[2].toInt(),
                        preRelease = preRelease
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun compareTo(other: Version): Int {
        // Compare major.minor.patch
        val result = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (result != 0) return result

        // Handle pre-release versions (pre-release < stable)
        return when {
            this.preRelease == null && other.preRelease == null -> 0
            this.preRelease == null && other.preRelease != null -> 1  // stable > prerelease
            this.preRelease != null && other.preRelease == null -> -1 // prerelease < stable
            else -> comparePreRelease(this.preRelease!!, other.preRelease!!)
        }
    }

    /**
     * Compares two prerelease strings according to semantic versioning rules.
     * Ordering: alpha < beta < rc, with numerical comparison within each type.
     */
    private fun comparePreRelease(a: String, b: String): Int {
        val aParsed = parsePreRelease(a)
        val bParsed = parsePreRelease(b)

        val typeComparison = aParsed.first.compareTo(bParsed.first)
        if (typeComparison != 0) return typeComparison

        return aParsed.second.compareTo(bParsed.second)
    }

    /**
     * Parses a prerelease string into (type ordinal, number).
     * Unknown types get ordinal 99 (greater than rc but less than stable).
     */
    private fun parsePreRelease(preRelease: String): Pair<Int, Int> {
        val parts = preRelease.split(".", limit = 2)
        val type = parts.getOrNull(0)?.lowercase() ?: ""
        val number = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val typeOrdinal = PRERELEASE_ORDER[type] ?: 99

        return Pair(typeOrdinal, number)
    }

    override fun toString(): String {
        return if (preRelease != null) {
            "$major.$minor.$patch-$preRelease"
        } else {
            "$major.$minor.$patch"
        }
    }

    fun isNewerThan(other: Version): Boolean = this > other
}
