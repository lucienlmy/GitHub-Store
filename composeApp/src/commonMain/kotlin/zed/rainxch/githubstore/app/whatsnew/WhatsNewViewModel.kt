package zed.rainxch.githubstore.app.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.WhatsNewEntries
import zed.rainxch.core.domain.model.WhatsNewEntry
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.AppVersionInfo

class WhatsNewViewModel(
    private val tweaksRepository: TweaksRepository,
    private val appVersionInfo: AppVersionInfo,
) : ViewModel() {
    private val _pendingEntry = MutableStateFlow<WhatsNewEntry?>(null)
    val pendingEntry: StateFlow<WhatsNewEntry?> = _pendingEntry.asStateFlow()

    init {
        viewModelScope.launch {
            evaluate()
        }
    }

    private suspend fun evaluate() {
        val current = appVersionInfo.versionCode
        val lastSeen = tweaksRepository.getLastSeenWhatsNewVersionCode().first()

        if (lastSeen == null) {
            tweaksRepository.setLastSeenWhatsNewVersionCode(current)
            return
        }

        if (lastSeen >= current) return

        val entry = WhatsNewEntries.forVersionCode(current)
        if (entry == null || !entry.showAsSheet) {
            tweaksRepository.setLastSeenWhatsNewVersionCode(current)
            return
        }

        _pendingEntry.value = entry
    }

    fun markSeen() {
        val entry = _pendingEntry.value ?: return
        _pendingEntry.value = null
        viewModelScope.launch {
            tweaksRepository.setLastSeenWhatsNewVersionCode(entry.versionCode)
        }
    }

    val hasHistory: Boolean
        get() = WhatsNewEntries.all.size > 1
}
