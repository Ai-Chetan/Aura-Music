package com.aura.music.util

/**
 * Shared vocabulary for download-stage labels and byte counters,
 * used by the Add panel and Library cards.
 */

fun downloadStageLabel(stage: String): String = when (stage) {
    "resolving" -> "Reading…"
    "downloading" -> "Downloading…"
    "saving" -> "Saving…"
    "done" -> "Done"
    else -> "Working…"
}

/** "12% • Downloading… • 3.4 / 8.1 MB" style detail line (parts omitted when unknown). */
fun downloadBytesDetail(
    percent: Int,
    bytesDone: Long?,
    bytesTotal: Long?,
    stageLabel: String? = null
): String {
    val mb = if (bytesDone != null && bytesTotal != null && bytesTotal > 0) {
        val done = bytesDone / 1_048_576.0
        val total = bytesTotal / 1_048_576.0
        "%.1f / %.1f MB".format(done, total)
    } else if (bytesDone != null && bytesDone > 0) {
        "%.1f MB".format(bytesDone / 1_048_576.0)
    } else {
        null
    }
    return listOfNotNull("$percent%", stageLabel, mb).joinToString(" • ")
}
