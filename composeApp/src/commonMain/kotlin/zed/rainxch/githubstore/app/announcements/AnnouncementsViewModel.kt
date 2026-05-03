package zed.rainxch.githubstore.app.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.Announcement
import zed.rainxch.core.domain.model.AnnouncementCategory
import zed.rainxch.core.domain.repository.AnnouncementsFeedSnapshot
import zed.rainxch.core.domain.repository.AnnouncementsRepository
import zed.rainxch.core.domain.utils.BrowserHelper

class AnnouncementsViewModel(
    private val repository: AnnouncementsRepository,
    private val browserHelper: BrowserHelper,
) : ViewModel() {
    private val logger = Logger.withTag("AnnouncementsViewModel")

    val feed: StateFlow<AnnouncementsFeedSnapshot> =
        repository
            .observeFeed()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    AnnouncementsFeedSnapshot(
                        items = emptyList(),
                        dismissedIds = emptySet(),
                        acknowledgedIds = emptySet(),
                        mutedCategories = emptySet(),
                        lastFetchedAtMillis = 0L,
                        lastRefreshFailed = false,
                    ),
            )

    val unreadCount: StateFlow<Int> =
        feed
            .map { it.unreadCount }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val pendingCriticalAcknowledgment: StateFlow<Announcement?> =
        feed
            .map { it.pendingCriticalAcknowledgment }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            try {
                repository.refresh()
            } catch (t: Throwable) {
                logger.e(t) { "Initial announcements refresh failed" }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                repository.refresh()
            } catch (t: Throwable) {
                logger.e(t) { "Manual announcements refresh failed" }
            }
        }
    }

    fun dismiss(announcement: Announcement) {
        viewModelScope.launch {
            try {
                repository.dismiss(announcement.id)
                if (!announcement.requiresAcknowledgment) {
                    repository.acknowledge(announcement.id)
                }
            } catch (t: Throwable) {
                logger.e(t) { "Failed to dismiss ${announcement.id}" }
            }
        }
    }

    fun acknowledge(announcement: Announcement) {
        viewModelScope.launch {
            try {
                repository.acknowledge(announcement.id)
            } catch (t: Throwable) {
                logger.e(t) { "Failed to acknowledge ${announcement.id}" }
            }
        }
    }

    fun openCta(announcement: Announcement) {
        val url = announcement.ctaUrl ?: return
        viewModelScope.launch {
            try {
                repository.acknowledge(announcement.id)
            } catch (t: Throwable) {
                logger.e(t) { "Failed to acknowledge before opening CTA ${announcement.id}" }
            }
        }
        browserHelper.openUrl(url) { error ->
            logger.w("Failed to open CTA url for ${announcement.id}: $error")
        }
    }

    fun setMuted(
        category: AnnouncementCategory,
        muted: Boolean,
    ) {
        viewModelScope.launch {
            try {
                repository.setMuted(category, muted)
            } catch (t: Throwable) {
                logger.e(t) { "Failed to toggle mute for $category" }
            }
        }
    }
}
