package com.mattxor.timesheet.data

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
 * One stretch of time on a project. [day] is the local date (LocalDate.toEpochDay()) the
 * stretch belongs to; a running timer has [endMillis] == null.
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
)

data class DayProjectTotal(
    val day: Long,
    val projectId: Long,
    val totalMillis: Long,
)
