package zed.rainxch.core.domain.util

/**
 * Single source of truth for version-string normalization and ordering
 * across the app. Both the periodic update check
 * (`InstalledAppsRepositoryImpl.checkForUpdates`) and the external-install
 * detection path (`ExternalInstallVerdict`) now call through here so a
 * single comparator change propagates everywhere instead of drifting
 * between private copies.
 *
 * Design invariants:
 *  - Every public function is pure; no I/O, no time, no randomness.
 *  - Inputs are `String?` where realistic so callers don't have to
 *    guard against nulls from the DB or the release feed.
 *  - Semver-compatible strings get semver semantics (including
 *    `-preRelease` ordering per spec: `1.0.0-beta < 1.0.0`).
 *  - Non-semver strings degrade gracefully: we try to extract a
 *    dotted-digit core (so `release-1.2.3` still compares like
 *    `1.2.3`), and only fall back to lexicographic comparison when
 *    the string has no recognisable version core at all.
 */
object VersionMath {
    /**
     * Reduces a tag or installed-version string to a form that
     * [parseSemanticVersion] can digest.
     *
     * Strategy, in order:
     *   1. Trim and strip common tag prefixes (`refs/tags/`, `v`, `V`).
     *   2. Drop `+build` metadata (per semver spec, ignored for
     *      ordering).
     *   3. If the result parses as semver, return it.
     *   4. Otherwise extract the first dotted-digit substring
     *      (optionally followed by a `-pre` identifier) and return
     *      that — handles maintainer prefixes like `release-1.2.0`,
     *      `App-v1.2.0-stable`, `build-2025.04.10`.
     *   5. If nothing numeric is found at all, return the cleaned
     *      string so the caller can fall back to equality / lex.
     *
     * Examples:
     *   `v1.2.3`               → `1.2.3`
     *   `1.2.3+sha.abcd`       → `1.2.3`
     *   `1.2.3-rc1`            → `1.2.3-rc1`
     *   `release-1.2.0`        → `1.2.0`
     *   `App-v1.2.0-stable`    → `1.2.0-stable`
     *   `build-2025.04.10`     → `2025.04.10`
     *   `refs/tags/v1.2.3`     → `1.2.3`
     *   `not-a-version`        → `not-a-version`
     *   `null`                 → `""`
     */
    fun normalizeVersion(version: String?): String {
        if (version.isNullOrBlank()) return ""
        val withoutRefs =
            version
                .trim()
                .removePrefix("refs/tags/")
                .removePrefix("v")
                .removePrefix("V")
                .trim()
        val withoutBuildMetadata = withoutRefs.substringBefore('+')
        if (parseSemanticVersion(withoutBuildMetadata) != null) {
            return withoutBuildMetadata
        }
        val match = DOTTED_DIGIT_PATTERN.find(withoutBuildMetadata)
        return match?.value ?: withoutBuildMetadata
    }

    /**
     * Returns `true` if [candidate] is strictly newer than [current]
     * after normalization. Handles semver (including pre-release
     * ordering per spec) and falls back to lexicographic comparison
     * for strings with no parseable version core.
     *
     * Both arguments are normalized via [normalizeVersion] before
     * comparison, so callers can pass raw tag strings.
     */
    fun isVersionNewer(candidate: String?, current: String?): Boolean {
        val normCandidate = normalizeVersion(candidate)
        val normCurrent = normalizeVersion(current)
        if (normCandidate.isEmpty() || normCurrent.isEmpty()) return false
        if (normCandidate == normCurrent) return false
        return compareNormalized(normCandidate, normCurrent) > 0
    }

    /**
     * Three-way comparison of two raw version strings after
     * normalization. Returns a positive int if [a] > [b], negative if
     * [a] < [b], `0` if equal or both empty.
     *
     * Use this when you need the full ordering (e.g. detecting
     * downgrades). Prefer [isVersionNewer] when you just need a
     * boolean.
     */
    fun compareVersions(a: String?, b: String?): Int {
        val normA = normalizeVersion(a)
        val normB = normalizeVersion(b)
        return compareNormalized(normA, normB)
    }

    private fun compareNormalized(a: String, b: String): Int {
        if (a == b) return 0
        val parsedA = parseSemanticVersion(a)
        val parsedB = parseSemanticVersion(b)
        if (parsedA != null && parsedB != null) {
            return compareSemver(parsedA, parsedB)
        }
        // Neither is parseable as semver — last-resort lexicographic
        // comparison. Callers should treat this as low-confidence.
        return a.compareTo(b)
    }

    private fun compareSemver(a: SemanticVersion, b: SemanticVersion): Int {
        val maxLen = maxOf(a.numbers.size, b.numbers.size)
        for (i in 0 until maxLen) {
            val ai = a.numbers.getOrElse(i) { 0L }
            val bi = b.numbers.getOrElse(i) { 0L }
            if (ai != bi) return ai.compareTo(bi)
        }
        // Numeric parts equal — spec: stable > pre-release when
        // pre-release only present on one side.
        return when {
            a.preRelease == null && b.preRelease == null -> 0
            a.preRelease == null -> 1 // a has no pre, so a > b
            b.preRelease == null -> -1
            else -> comparePreRelease(a.preRelease, b.preRelease)
        }
    }

    /**
     * Compare pre-release identifiers per semver spec:
     *  - Identifiers consisting of only digits are compared
     *    numerically.
     *  - Identifiers with letters are compared lexically.
     *  - Numeric identifiers always have lower precedence than
     *    alphanumeric.
     *  - A larger set of pre-release fields has higher precedence if
     *    all preceding are equal.
     */
    private fun comparePreRelease(a: String, b: String): Int {
        val aParts = a.split(".")
        val bParts = b.split(".")
        for (i in 0 until minOf(aParts.size, bParts.size)) {
            val ap = aParts[i]
            val bp = bParts[i]
            val aNum = ap.toLongOrNull()
            val bNum = bp.toLongOrNull()
            val cmp =
                when {
                    aNum != null && bNum != null -> aNum.compareTo(bNum)
                    aNum != null -> -1 // numeric < alphanumeric
                    bNum != null -> 1
                    else -> ap.compareTo(bp)
                }
            if (cmp != 0) return cmp
        }
        return aParts.size.compareTo(bParts.size)
    }

    private data class SemanticVersion(
        val numbers: List<Long>,
        val preRelease: String?,
    )

    private fun parseSemanticVersion(version: String): SemanticVersion? {
        if (version.isEmpty()) return null
        val hyphenIndex = version.indexOf('-')
        val numberPart = if (hyphenIndex >= 0) version.substring(0, hyphenIndex) else version
        val preRelease =
            if (hyphenIndex >= 0 && hyphenIndex < version.length - 1) {
                version.substring(hyphenIndex + 1)
            } else {
                null
            }
        val parts = numberPart.split(".")
        val numbers = parts.mapNotNull { it.toLongOrNull() }
        if (numbers.isEmpty() || numbers.size != parts.size) return null
        return SemanticVersion(numbers, preRelease)
    }

    private val DOTTED_DIGIT_PATTERN = Regex("""\d+(?:\.\d+)*(?:-[\w.]+)?""")
}
