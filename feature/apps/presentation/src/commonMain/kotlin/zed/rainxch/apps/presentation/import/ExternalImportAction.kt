package zed.rainxch.apps.presentation.import

import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi

sealed interface ExternalImportAction {
    data object OnStart : ExternalImportAction

    data object OnRequestPermission : ExternalImportAction

    data object OnPermissionGranted : ExternalImportAction

    data object OnPermissionDenied : ExternalImportAction

    data object OnSkipCurrentCard : ExternalImportAction

    data object OnSkipForever : ExternalImportAction

    data class OnPickSuggestion(val suggestion: RepoSuggestionUi) : ExternalImportAction

    data object OnExpandCurrentCard : ExternalImportAction

    data object OnCollapseCurrentCard : ExternalImportAction

    data class OnSearchOverrideChanged(val query: String) : ExternalImportAction

    data object OnSearchOverrideSubmit : ExternalImportAction

    data object OnUndoLast : ExternalImportAction

    data object OnExit : ExternalImportAction

    data object OnDismissCompletionToast : ExternalImportAction
}
