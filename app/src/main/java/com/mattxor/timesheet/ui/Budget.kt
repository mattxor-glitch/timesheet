package com.mattxor.timesheet.ui

import androidx.compose.ui.graphics.Color
import com.mattxor.timesheet.data.Project
import com.mattxor.timesheet.data.TimeEntry
import java.util.Locale
import kotlin.math.abs

/** Orange at or below this many hours left; red at zero or below. */
const val BUDGET_WARN_MILLIS = 10L * 3_600_000

val BudgetOrange = Color(0xFFEF6C00)
val BudgetRed = Color(0xFFD32F2F)

enum class BudgetLevel { OK, LOW, OVER }

fun budgetLevel(remainingMillis: Long): BudgetLevel = when {
    remainingMillis <= 0 -> BudgetLevel.OVER
    remainingMillis <= BUDGET_WARN_MILLIS -> BudgetLevel.LOW
    else -> BudgetLevel.OK
}

fun BudgetLevel.color(): Color? = when (this) {
    BudgetLevel.OK -> null
    BudgetLevel.LOW -> BudgetOrange
    BudgetLevel.OVER -> BudgetRed
}

/**
 * All time ever logged on [project]: finished entries ([totals]), time from history that was
 * cleared ([Project.prunedMillis]) and any running timer.
 */
fun usedMillis(project: Project, totals: Map<Long, Long>, todayEntries: List<TimeEntry>, now: Long): Long =
    (totals[project.id] ?: 0L) + project.prunedMillis +
        todayEntries.filter { it.isRunning && it.projectId == project.id }.sumOf { it.elapsed(now) }

/** Remaining labour hours, or null when the project has no budget. */
fun remainingMillis(project: Project, totals: Map<Long, Long>, todayEntries: List<TimeEntry>, now: Long): Long? =
    project.budgetMillis?.let { it - usedMillis(project, totals, todayEntries, now) }

/** "12:30" or "−3:15" (hours:minutes). */
fun formatSignedHm(millis: Long): String {
    val minutes = (abs(millis) + 30_000) / 60_000 // nearest minute, so used + left add up
    val sign = if (millis < 0 && minutes > 0) "−" else ""
    return String.format(Locale.US, "%s%d:%02d", sign, minutes / 60, minutes % 60)
}

/** Parses labour hours typed as "37.5", "37,5" or "37:30". Blank = no budget (null); invalid = -1. */
fun parseHoursInput(text: String): Long? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val parts = t.split(':')
    return try {
        when (parts.size) {
            1 -> {
                val h = t.replace(',', '.').toDouble()
                if (h < 0 || h > 100_000) -1 else Math.round(h * 3_600_000)
            }
            2 -> {
                val h = parts[0].ifBlank { "0" }.toLong()
                val m = parts[1].ifBlank { "0" }.toLong()
                if (h < 0 || m !in 0..59 || h > 100_000) -1 else (h * 60 + m) * 60_000
            }
            else -> -1
        }
    } catch (e: NumberFormatException) {
        -1
    }
}

/** Budget shown back in the input box: "37.5", "40". */
fun formatHoursInput(millis: Long): String {
    val hours = millis / 3_600_000.0
    return if (hours == Math.floor(hours)) hours.toLong().toString()
    else String.format(Locale.US, "%.2f", hours).trimEnd('0').trimEnd('.')
}
