package com.mattxor.timesheet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mattxor.timesheet.data.DayProjectTotal
import com.mattxor.timesheet.data.Project
import com.mattxor.timesheet.data.Retention
import com.mattxor.timesheet.data.Settings
import com.mattxor.timesheet.data.TimeEntry
import com.mattxor.timesheet.data.TimesheetRepository
import com.mattxor.timesheet.data.TimesheetRepository.Companion.dayOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HistoryDay(val day: Long, val totals: List<DayProjectTotal>) {
    val totalMillis: Long get() = totals.sumOf { it.totalMillis }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimesheetViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val repo = TimesheetRepository(app, settings)
    private val dayChangeLock = Mutex()

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _today = MutableStateFlow(dayOf(_now.value))
    val today: StateFlow<Long> = _today.asStateFlow()

    val projects: StateFlow<List<Project>> = repo.dao.observeActiveProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every project including removed ones, for naming rows in history. */
    val projectsById: StateFlow<Map<Long, Project>> = repo.dao.observeAllProjects()
        .map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val todayEntries: StateFlow<List<TimeEntry>> = _today
        .flatMapLatest { repo.dao.observeEntriesForDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistoryDay>> = _today
        .flatMapLatest { repo.dao.observeHistory(it) }
        .map { rows ->
            rows.groupBy { it.day }
                .map { (day, totals) -> HistoryDay(day, totals.sortedByDescending { it.totalMillis }) }
                .sortedByDescending { it.day }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All-time finished time per project (running timers are added on screen). */
    val projectTotals: StateFlow<Map<Long, Long>> = repo.dao.observeProjectTotals()
        .map { list -> list.associate { it.projectId to it.totalMillis } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val oldestDay: StateFlow<Long?> = repo.dao.observeOldestDay()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val retention: StateFlow<Retention> = settings.retention
    val carryOverMidnight: StateFlow<Boolean> = settings.carryOverMidnight

    private val _dbSizeBytes = MutableStateFlow(0L)
    val dbSizeBytes: StateFlow<Long> = _dbSizeBytes.asStateFlow()

    private val _sizeWarningDismissed = MutableStateFlow(false)
    val sizeWarningDismissed: StateFlow<Boolean> = _sizeWarningDismissed.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            dayChangeLock.withLock {
                repo.rollover()
                if (repo.pruneHistory() > 0) repo.compact()
            }
            refreshDatabaseSize()
        }
    }

    /** Called once a second while the app is on screen. Handles the daily reset. */
    fun tick() {
        val now = System.currentTimeMillis()
        _now.value = now
        val day = dayOf(now)
        if (day != _today.value) {
            viewModelScope.launch {
                dayChangeLock.withLock {
                    if (dayOf(System.currentTimeMillis()) == _today.value) return@withLock
                    repo.rollover(now)
                    repo.pruneHistory(now)
                    _today.value = day
                }
                refreshDatabaseSize()
            }
        }
    }

    fun onTileTapped(project: Project) {
        viewModelScope.launch { repo.toggle(project.id) }
    }

    fun addManualTime(project: Project, durationMillis: Long) {
        viewModelScope.launch {
            repo.addManualTime(project.id, durationMillis)
            _messages.send("Added ${formatDuration(durationMillis)} to ${project.number} ${project.name}".trim())
        }
    }

    fun setTodayTotal(project: Project, targetMillis: Long) {
        viewModelScope.launch {
            val delta = repo.setTodayTotal(project.id, targetMillis)
            _messages.send(
                if (delta == 0L) "No change"
                else "${projectLabel(project)} set to ${formatDuration(targetMillis)} today"
            )
        }
    }

    fun deleteEntry(entry: TimeEntry) {
        viewModelScope.launch {
            repo.deleteEntry(entry)
            _messages.send("Entry deleted")
        }
    }

    fun editTimerEntry(entry: TimeEntry, projectId: Long, newStart: Long, newEnd: Long?) {
        viewModelScope.launch {
            _messages.send(repo.editTimerEntry(entry.id, projectId, newStart, newEnd) ?: "Entry updated")
        }
    }

    fun editManualEntry(entry: TimeEntry, projectId: Long, durationMillis: Long) {
        viewModelScope.launch {
            _messages.send(repo.editManualEntry(entry.id, projectId, durationMillis) ?: "Entry updated")
        }
    }

    suspend fun earliestStartFor(entry: TimeEntry): Long = repo.earliestStartFor(entry)

    fun changeRunningStart(newStart: Long) {
        viewModelScope.launch {
            val error = repo.changeRunningStart(newStart)
            _messages.send(error ?: "Start time changed to ${TimesheetRepository.formatTimeOfDay(newStart)}")
        }
    }

    fun entriesForDay(day: Long) = repo.dao.observeEntriesForDay(day)

    fun stopAll() {
        viewModelScope.launch { repo.stopAll() }
    }

    fun addProject(number: String, name: String, budgetMillis: Long?) {
        viewModelScope.launch { repo.addProject(number, name, budgetMillis) }
    }

    fun updateProject(project: Project) {
        viewModelScope.launch { repo.updateProject(project) }
    }

    fun removeProject(project: Project) {
        viewModelScope.launch { repo.removeProject(project) }
    }

    fun setCarryOverMidnight(value: Boolean) = settings.setCarryOverMidnight(value)

    fun setRetention(value: Retention) {
        settings.setRetention(value)
        viewModelScope.launch {
            val deleted = repo.pruneHistory()
            if (deleted > 0) {
                repo.compact()
                _messages.send("Removed $deleted old time entries")
            }
            refreshDatabaseSize()
        }
    }

    /** Deletes all history dated before [day]. */
    fun clearHistoryBefore(day: Long) {
        viewModelScope.launch {
            val deleted = repo.clearBefore(day)
            refreshDatabaseSize()
            _messages.send(
                if (deleted == 0) "Nothing to clear before ${formatDateMdy(day)}"
                else "Cleared $deleted time entries before ${formatDateMdy(day)}"
            )
        }
    }

    fun dismissSizeWarning() {
        _sizeWarningDismissed.value = true
    }

    fun refreshDatabaseSize() {
        viewModelScope.launch {
            val size = repo.databaseSizeBytes()
            _dbSizeBytes.value = size
            if (size <= TimesheetRepository.SIZE_WARNING_BYTES) _sizeWarningDismissed.value = false
        }
    }
}
