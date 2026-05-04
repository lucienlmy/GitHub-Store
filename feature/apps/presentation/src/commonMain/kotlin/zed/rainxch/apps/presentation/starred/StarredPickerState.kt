package zed.rainxch.apps.presentation.starred

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class StarredPickerState(
    val phase: Phase = Phase.LoadingStars,
    val totalStarred: Int = 0,
    val candidates: ImmutableList<StarredCandidateUi> = persistentListOf(),
    val scanProgress: Int = 0,
    val scanTotal: Int = 0,
    val showWithoutApk: Boolean = false,
    val searchQuery: String = "",
    val sortRule: StarredPickerSortRule = StarredPickerSortRule.RecentlyStarred,
    val isAuthenticated: Boolean = true,
    val errorMessage: String? = null,
    val rateLimited: Boolean = false,
) {
    enum class Phase {
        LoadingStars,
        ScanningReleases,
        Ready,
        Empty,
    }
}

data class StarredCandidateUi(
    val repoId: Long,
    val owner: String,
    val name: String,
    val ownerAvatarUrl: String,
    val description: String?,
    val stargazersCount: Int,
    val starredAt: Long?,
    val hasApkRelease: Boolean,
    val isAlreadyTracked: Boolean,
    val latestReleaseTag: String?,
)

enum class StarredPickerSortRule {
    RecentlyStarred,
    Alphabetical,
    MostStars,
}
