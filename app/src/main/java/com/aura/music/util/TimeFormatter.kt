package com.aura.music.util

import java.util.concurrent.TimeUnit

fun Long.formatDuration(): String {
    if (this <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
    return "%d:%02d".format(minutes, seconds)
}