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

    suspend fun deleteManualEntry(entry: TimeEntry) {
        if (entry.manual) dao.deleteManualEntry(entry.id)
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

        fun startOfDayMillis(day: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
