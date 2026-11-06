package com.mattxor.timesheet.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TimesheetRepository(context: Context, val settings: Settings) {
    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    val dao = db.dao()

    /** Tapping a tile: stop whatever is running; start [projectId] unless it was the one running. */
    suspend fun toggle(projectId: Long, now: Long = System.currentTimeMillis()) = db.withTransaction {
        rolloverLocked(now)
        val running = dao.getRunning()
        running.forEach { dao.closeEntry(it.id, now) }
        if (running.none { it.projectId == projectId }) {
            dao.insertEntry(TimeEntry(projectId = projectId, day = dayOf(now), startMillis = now))
        }
    }

    suspend fun stopAll(now: Long = System.currentTimeMillis()) = db.withTransaction {
        rolloverLocked(now)
        dao.getRunning().forEach { dao.closeEntry(it.id, now) }
    }

    /** Adds a total amount of time to [projectId] for today, stamped with when it was added. */
    suspend fun addManualTime(projectId: Long, durationMillis: Long, now: Long = System.currentTimeMillis()) {
        require(durationMillis > 0)
        dao.insertEntry(
            TimeEntry(
                projectId = projectId,
                day = dayOf(now),
                startMillis = now,
                endMillis = now,
                manual = true,
                durationMillis = durationMillis,
                createdAtMillis = now,
            )
        )
    }

    /** Deletes any entry (including a running timer, which simply stops counting). */
    suspend fun deleteEntry(entry: TimeEntry) {
        dao.deleteEntry(entry.id)
    }

    /**
     * Edits a timer entry. [newEnd] is ignored for a running timer. Returns an error message,
     * or null on success. The first value of each changed field is kept, and the edit is stamped.
     */
    suspend fun editTimerEntry(
        id: Long,
        projectId: Long,
        newStart: Long,
        newEnd: Long?,
        now: Long = System.currentTimeMillis(),
    ): String? = db.withTransaction {
        val e = dao.getEntry(id) ?: return@withTransaction "That entry no longer exists"
        if (e.manual) return@withTransaction "Not a timer entry"
        val end = if (e.endMillis == null) null else (newEnd ?: return@withTransaction "Enter a stop time")
        validateTimerTimes(e, newStart, end, dao.getEntriesForDay(e.day), now)?.let { return@withTransaction it }
        if (projectId == e.projectId && newStart == e.startMillis && end == e.endMillis) return@withTransaction null
        dao.updateEntry(
            e.copy(
                projectId = projectId,
                startMillis = newStart,
                endMillis = end,
                originalStartMillis = e.originalStartMillis ?: e.startMillis.takeIf { it != newStart },
                originalEndMillis = e.originalEndMillis ?: e.endMillis.takeIf { it != end },
                originalProjectId = e.originalProjectId ?: e.projectId.takeIf { it != projectId },
                startEditedAtMillis = now,
            )
        )
        null
    }

    /** Edits a manual entry's amount and/or project. */
    suspend fun editManualEntry(
        id: Long,
        projectId: Long,
        durationMillis: Long,
        now: Long = System.currentTimeMillis(),
    ): String? = db.withTransaction {
        val e = dao.getEntry(id) ?: return@withTransaction "That entry no longer exists"
        if (!e.manual) return@withTransaction "Not a manual entry"
        if (durationMillis <= 0) return@withTransaction "Enter an amount of time"
        if (projectId == e.projectId && durationMillis == e.durationMillis) return@withTransaction null
        dao.updateEntry(
            e.copy(
                projectId = projectId,
                durationMillis = durationMillis,
                originalDurationMillis = e.originalDurationMillis ?: e.durationMillis.takeIf { it != durationMillis },
                originalProjectId = e.originalProjectId ?: e.projectId.takeIf { it != projectId },
                startEditedAtMillis = now,
            )
        )
        null
    }

    /** Earliest start the running [entry] may be moved to: midnight or the previous timer's stop. */
    suspend fun earliestStartFor(entry: TimeEntry): Long =
        maxOf(startOfDayMillis(entry.day), dao.latestTimerEnd(entry.day, entry.id) ?: Long.MIN_VALUE)

    /**
     * Moves the start of the running timer to [newStart]. Keeps the first original start and
     * stamps when the change was made. Returns an error message, or null on success.
     */
    suspend fun changeRunningStart(newStart: Long, now: Long = System.currentTimeMillis()): String? =
        db.withTransaction {
            rolloverLocked(now)
            val running = dao.getRunning().firstOrNull() ?: return@withTransaction "No timer is running"
            val earliest = earliestStartFor(running)
            when {
                newStart > now -> "The start time can't be in the future"
                newStart < earliest -> "The start time can't be before ${formatTimeOfDay(earliest)}"
                newStart == running.startMillis -> null
                else -> {
                    dao.updateEntry(
                        running.copy(
                            startMillis = newStart,
                            originalStartMillis = running.originalStartMillis ?: running.startMillis,
                            startEditedAtMillis = now,
                        )
                    )
                    null
                }
            }
        }

    suspend fun addProject(number: String, name: String) {
        dao.insertProject(Project(number = number.trim(), name = name.trim()))
    }

    suspend fun updateProject(project: Project) {
        dao.updateProject(project.copy(number = project.number.trim(), name = project.name.trim()))
    }

    /** Hides the tile but keeps the project so its history still shows up. */
    suspend fun removeProject(project: Project, now: Long = System.currentTimeMillis()) = db.withTransaction {
        dao.getRunningFor(project.id).forEach { dao.closeEntry(it.id, now) }
        dao.updateProject(project.copy(archived = true))
    }

    /**
     * Daily reset. A timer still running from an earlier day is closed at that day's midnight.
     * If "carry over" is on, it continues on the new day starting from zero.
     */
    suspend fun rollover(now: Long = System.currentTimeMillis()) = db.withTransaction { rolloverLocked(now) }

    private suspend fun rolloverLocked(now: Long) {
        val today = dayOf(now)
        val carry = settings.carryOverMidnight.value
        for (entry in dao.getRunning()) {
            var day = entry.day
            var entryId = entry.id
            while (day < today) {
                val midnight = startOfDayMillis(day + 1)
                dao.closeEntry(entryId, midnight)
                day += 1
                if (!carry) break
                entryId = dao.insertEntry(
                    TimeEntry(projectId = entry.projectId, day = day, startMillis = midnight)
                )
            }
        }
    }

    /** Applies the retention setting. */
    suspend fun pruneHistory(now: Long = System.currentTimeMillis()): Int {
        val days = settings.retention.value.days ?: return 0
        return dao.deleteBefore(dayOf(now) - days)
    }

    /** Clears history dated before [day] (exclusive) and shrinks the database file. */
    suspend fun clearBefore(day: Long): Int {
        val deleted = dao.deleteBefore(day)
        compact()
        return deleted
    }

    suspend fun compact() = withContext(Dispatchers.IO) {
        val sqlite = db.openHelper.writableDatabase
        sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        sqlite.execSQL("VACUUM")
        sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
    }

    /** Size on disk of the database including its WAL and shared-memory files. */
    suspend fun databaseSizeBytes(): Long = withContext(Dispatchers.IO) {
        val main = appContext.getDatabasePath(AppDatabase.NAME)
        listOf(main, java.io.File(main.path + "-wal"), java.io.File(main.path + "-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    companion object {
        const val SIZE_WARNING_BYTES = 50L * 1024 * 1024

        fun dayOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

        fun formatTimeOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))

        /**
         * Checks new start/stop times for a timer entry on its own day: inside that day, not in
         * the future, stop after start, and not overlapping another timer entry.
         * [end] == null means the entry is still running.
         */
        fun validateTimerTimes(entry: TimeEntry, start: Long, end: Long?, dayEntries: List<TimeEntry>, now: Long): String? {
            val dayStart = startOfDayMillis(entry.day)
            val dayEnd = startOfDayMillis(entry.day + 1)
            val effectiveEnd = end ?: now
            return when {
                start < dayStart -> "Start can't be before midnight"
                start > now -> "Start can't be in the future"
                end != null && end > now -> "Stop can't be in the future"
                end != null && end > dayEnd -> "Stop can't be after midnight"
                end != null && end <= start -> "Stop must be after start"
                else -> dayEntries
                    .firstOrNull { o ->
                        !o.manual && o.id != entry.id && o.day == entry.day &&
                            start < (o.endMillis ?: now) && effectiveEnd > o.startMillis
                    }
                    ?.let { o ->
                        "Overlaps another entry (${formatTimeOfDay(o.startMillis)} – " +
                            "${o.endMillis?.let { formatTimeOfDay(it) } ?: "running"})"
                    }
            }
        }

        fun startOfDayMillis(day: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
