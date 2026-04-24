package zed.rainxch.core.domain.util

import zed.rainxch.core.domain.model.InstalledApp

/**
 * Decision produced by [resolveExternalInstallVerdict] after an
 * externally-performed install/update surfaces via PackageManager.
 */
enum class VersionVerdict {
    /** System version meets or exceeds every signal we have. */
    UP_TO_DATE,

    /** System version is strictly older than at least one reliable signal. */
    UPDATE_AVAILABLE,

    /**
     * No signal was reliable enough to decide. The caller should
     * leave the current `isUpdateAvailable` flag alone and trigger
     * an authoritative re-check via
     * [zed.rainxch.core.domain.repository.InstalledAppsRepository.checkForUpdates].
     */
    UNKNOWN,
}

/**
 * Best-effort local verdict after an external install/update.
 *
 * An Android APK install gives us two pieces of truth — [newVersionName]
 * and [newVersionCode] — both of which are stricter than what we can
 * usually know about the tracked "latest" release. We try a ladder of
 * signals, stopping at the first one that can produce a reliable answer:
 *
 * 1. **versionCode comparison.** Monotonic integer by Android contract.
 *    Only usable when we've captured a non-zero `latestVersionCode` for
 *    this app (i.e. a previous install round stamped it). If both sides
 *    have a real `versionCode`, one comparison nails the answer.
 *
 * 2. **versionName vs latestVersionName.** These are the post-install
 *    values from PackageManager, which means same axis. Run through
 *    [VersionMath.normalizeVersion] so `1.2.3` and `v1.2.3-stable` line
 *    up, then semver-compare.
 *
 * 3. **versionName vs latestVersion (release tag).** Works when the
 *    maintainer's tag contains the real version (e.g. `v1.2.3`,
 *    `release-1.2.3`, `build-2025.04.10`) — [VersionMath.normalizeVersion]
 *    extracts the dotted-digit core.
 *
 * 4. **Give up.** Return [VersionVerdict.UNKNOWN]. The caller is
 *    expected to defer to the network-backed
 *    [zed.rainxch.core.domain.repository.InstalledAppsRepository.checkForUpdates],
 *    which does the same comparison with fresh GitHub release data.
 *
 * This function is pure — it does not read or write any state, and it
 * makes no network or disk calls. Callers combine it with an async
 * authoritative re-check so that an incorrect optimistic answer is
 * corrected within seconds.
 */
fun resolveExternalInstallVerdict(
    app: InstalledApp,
    newVersionName: String,
    newVersionCode: Long,
): VersionVerdict {
    // Priority 1: integer versionCode (most reliable when available).
    val latestVersionCode = app.latestVersionCode ?: 0L
    if (latestVersionCode > 0L && newVersionCode > 0L) {
        return if (newVersionCode >= latestVersionCode) {
            VersionVerdict.UP_TO_DATE
        } else {
            VersionVerdict.UPDATE_AVAILABLE
        }
    }

    // Priority 2: versionName ↔ latestVersionName (same axis — both
    // come from PackageManager on their respective installs).
    val latestName = app.latestVersionName
    if (!latestName.isNullOrBlank() && newVersionName.isNotBlank()) {
        val verdict = compareAndDecide(newVersionName, latestName)
        if (verdict != VersionVerdict.UNKNOWN) return verdict
    }

    // Priority 3: versionName ↔ latestVersion (release tag). Different
    // axis but VersionMath.normalizeVersion handles the common cases
    // (`v1.2.3`, `release-1.2.0`, `App-v1.2.0-stable`, …).
    val latestTag = app.latestVersion
    if (!latestTag.isNullOrBlank() && newVersionName.isNotBlank()) {
        val verdict = compareAndDecide(newVersionName, latestTag)
        if (verdict != VersionVerdict.UNKNOWN) return verdict
    }

    return VersionVerdict.UNKNOWN
}

private fun compareAndDecide(
    systemVersion: String,
    latestVersion: String,
): VersionVerdict {
    val system = VersionMath.normalizeVersion(systemVersion)
    val latest = VersionMath.normalizeVersion(latestVersion)
    if (system.isEmpty() || latest.isEmpty()) return VersionVerdict.UNKNOWN

    val cmp = VersionMath.compareVersions(system, latest)
    return when {
        cmp >= 0 -> VersionVerdict.UP_TO_DATE
        VersionMath.isVersionNewer(latest, system) -> VersionVerdict.UPDATE_AVAILABLE
        else -> VersionVerdict.UNKNOWN
    }
}
