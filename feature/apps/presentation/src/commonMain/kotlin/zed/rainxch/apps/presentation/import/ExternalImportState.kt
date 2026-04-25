package zed.rainxch.apps.presentation.import

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.apps.presentation.import.model.CandidateUi
import zed.rainxch.apps.presentation.import.model.ImportPhase
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi

data class ExternalImportState(
    val phase: ImportPhase = ImportPhase.Idle,
    val totalCandidates: Int = 0,
    val autoImported: Int = 0,
    val skipped: Int = 0,
    val manuallyLinked: Int = 0,
    val cards: ImmutableList<CandidateUi> = persistentListOf(),
    val currentCardIndex: Int = 0,
    val currentExpanded: Boolean = false,
    val searchOverrideQuery: String = "",
    val searchOverrideResults: ImmutableList<RepoSuggestionUi> = persistentListOf(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val isPermissionDenied: Boolean = false,
    val visiblePackageCount: Int = 0,
    val invisiblePackageCountEstimate: Int = 0,
    val showCompletionToast: Boolean = false,
    val errorMessage: String? = null,
) {
    val currentCard: CandidateUi?
        get() = cards.getOrNull(currentCardIndex)

    val cardsRemaining: Int
        get() = (cards.size - currentCardIndex).coerceAtLeast(0)

    val isWizardComplete: Boolean
        get() = phase == ImportPhase.Done || (cards.isNotEmpty() && currentCardIndex >= cards.size)
}
