package zed.rainxch.core.domain.model

import kotlinx.datetime.Instant

data class MirrorConfig(
    val id: String,
    val name: String,
    val urlTemplate: String?,
    val type: MirrorType,
    val status: MirrorStatus,
    val latencyMs: Int?,
    val lastCheckedAt: Instant?,
)
