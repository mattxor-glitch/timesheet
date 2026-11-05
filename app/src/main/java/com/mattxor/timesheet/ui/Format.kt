package com.mattxor.timesheet.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val mdy = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
private val longDay = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
private val shortDay = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

fun formatDateMdy(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(mdy)

fun formatDayLong(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(longDay)

fun formatDayShort(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortDay)

/** 1:05:09 style, for live timers. */
fun formatClock(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) / 1000)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d:%02d", h, m, s)
}

/** "2:15 · 2.25 h" style, for history. */
fun formatHours(millis: Long): String {
    val totalMinutes = millis.coerceAtLeast(0) / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    val decimal = millis.coerceAtLeast(0) / 3_600_000.0
    return String.format(Locale.US, "%d:%02d · %.2f h", h, m, decimal)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
