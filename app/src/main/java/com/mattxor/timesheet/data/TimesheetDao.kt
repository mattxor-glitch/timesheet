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

    @Query("SELECT * FROM time_entries WHERE day = :day")
    fun observeEntriesForDay(day: Long): Flow<List<TimeEntry>>

    @Query("SELECT * FROM time_entries WHERE endMillis IS NULL")
    suspend fun getRunning(): List<TimeEntry>

    @Query("SELECT * FROM time_entries WHERE endMillis IS NULL AND projectId = :projectId")
    suspend fun getRunningFor(projectId: Long): List<TimeEntry>

    @Insert
    suspend fun insertEntry(entry: TimeEntry): Long

    @Query("UPDATE time_entries SET endMillis = :endMillis WHERE id = :id")
    suspend fun closeEntry(id: Long, endMillis: Long)

    @Query(
        """
        SELECT day, projectId, SUM(endMillis - startMillis) AS totalMillis
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
