package com.aura.music.util

import java.util.concurrent.TimeUnit

fun Long.formatDuration(): String {
    if (this <= 0) return "0:00"
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(TimeUnit.MILLISECONDS.toMinutes(this), seconds)
    }
}