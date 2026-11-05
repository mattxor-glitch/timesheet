package com.mattxor.timesheet.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimesheetDao {

    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY number COLLATE NOCASE, name COLLATE NOCASE")
    fun observeActiveProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects")
    fun observeAllProjects(): Flow<List<Project>>

    @Insert
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Query("SELECT * FROM time_entries WHERE day = :day ORDER BY startMillis, id")
    fun observeEntriesForDay(day: Long): Flow<List<TimeEntry>>

    @Query("SELECT * FROM time_entries WHERE endMillis IS NULL AND manual = 0")
    suspend fun getRunning(): List<TimeEntry>

    @Query("SELECT * FROM time_entries WHERE endMillis IS NULL AND manual = 0 AND projectId = :projectId")
    suspend fun getRunningFor(projectId: Long): List<TimeEntry>

    @Insert
    suspend fun insertEntry(entry: TimeEntry): Long

    @Update
    suspend fun updateEntry(entry: TimeEntry)

    @Query("DELETE FROM time_entries WHERE id = :id AND manual = 1")
    suspend fun deleteManualEntry(id: Long)

    /** Latest stop time of other timer entries on [day]; a changed start can't go before it. */
    @Query("SELECT MAX(endMillis) FROM time_entries WHERE day = :day AND manual = 0 AND endMillis IS NOT NULL AND id != :excludeId")
    suspend fun latestTimerEnd(day: Long, excludeId: Long): Long?

    @Query("UPDATE time_entries SET endMillis = :endMillis WHERE id = :id")
    suspend fun closeEntry(id: Long, endMillis: Long)

    @Query(
        """
        SELECT day, projectId,
            SUM(CASE WHEN manual = 1 THEN COALESCE(durationMillis, 0) ELSE endMillis - startMillis END) AS totalMillis
        FROM time_entries
        WHERE endMillis IS NOT NULL AND day < :today
        GROUP BY day, projectId
        ORDER BY day DESC
        """
    )
    fun observeHistory(today: Long): Flow<List<DayProjectTotal>>

    /** Deletes finished entries dated before [day]. Returns the number of rows removed. */
    @Query("DELETE FROM time_entries WHERE day < :day AND endMillis IS NOT NULL")
    suspend fun deleteBefore(day: Long): Int

    @Query("SELECT MIN(day) FROM time_entries")
    fun observeOldestDay(): Flow<Long?>
}
