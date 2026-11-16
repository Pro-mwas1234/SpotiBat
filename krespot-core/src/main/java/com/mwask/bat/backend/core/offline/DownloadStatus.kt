package com.mwask.bat.backend.core.offline

/** Where one song's download stands. */
enum class DownloadPhase { QUEUED, RUNNING, DONE, FAILED }

/**
 * Per-track download progress, shared between the player that produces it and the Compose
 * layer that draws it.
 *
 * [percent] is 0f..1f, or -1f while the downloader does not know the size yet. [phase] is not
 * redundant: a queued song and a song that has genuinely transferred nothing both read as 0f,
 * and only the phase tells them apart.
 */
data class DownloadStatus(
    val phase: DownloadPhase,
    val percent: Float = -1f,
    /** Short reason the download stopped, only set when [phase] is FAILED. */
    val error: String? = null,
) {
    /** 0f..1f for a determinate bar, or null when the bar should spin instead. */
    val barValue: Float? get() = if (percent >= 0f) percent.coerceIn(0f, 1f) else null

    /** True while the row should show a bar rather than a settled label. */
    val working: Boolean get() = phase == DownloadPhase.QUEUED || phase == DownloadPhase.RUNNING
}
