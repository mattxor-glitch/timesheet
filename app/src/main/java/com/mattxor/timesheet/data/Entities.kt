package com.mattxor.timesheet.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val name: String,
    /** Removed projects are hidden from the tiles but kept so history still shows their names. */
    val archived: Boolean = false,
)

/**
 * One line in the time log. [day] is the local date (LocalDate.toEpochDay()) it belongs to.
 *
 * Timer entries: [startMillis]..[endMillis] are the start/stop timestamps; a running timer has
 * [endMillis] == null. If the start was changed by hand, [originalStartMillis] holds the old start
 * and [startEditedAtMillis] when the change was made.
 *
 * Manual entries ([manual] == true): a total amount of time, [durationMillis], added at
 * [createdAtMillis]. Their start/end are both set to the time they were added.
 */
@Entity(
    tableName = "time_entries",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index("day"), Index("endMillis")],
)
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val day: Long,
    val startMillis: Long,
    val endMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val manual: Boolean = false,
    val durationMillis: Long? = null,
    @ColumnInfo(defaultValue = "0") val createdAtMillis: Long = startMillis,
    val originalStartMillis: Long? = null,
    val startEditedAtMillis: Long? = null,
) {
    fun elapsed(now: Long): Long =
        if (manual) (durationMillis ?: 0L).coerceAtLeast(0)
        else ((endMillis ?: now) - startMillis).coerceAtLeast(0)

    val isRunning: Boolean get() = !manual && endMillis == null
}

data class DayProjectTotal(
    val day: Long,
    val projectId: Long,
    val totalMillis: Long,
)
