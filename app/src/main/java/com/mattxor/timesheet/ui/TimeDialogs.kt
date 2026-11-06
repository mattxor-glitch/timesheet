package com.mattxor.timesheet.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mattxor.timesheet.data.Project
import com.mattxor.timesheet.data.TimeEntry
import com.mattxor.timesheet.data.TimesheetRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Long-press menu for a tile. */
@Composable
fun ProjectActionsDialog(
    project: Project,
    isRunning: Boolean,
    onDismiss: () -> Unit,
    onAddTime: () -> Unit,
    onChangeStart: () -> Unit,
    onEdit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${project.number} ${project.name}".trim()) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddTime, modifier = Modifier.fillMaxWidth()) { Text("Add time for today") }
                if (isRunning) {
                    OutlinedButton(onClick = onChangeStart, modifier = Modifier.fillMaxWidth()) {
                        Text("Change start time")
                    }
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit or remove project") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Adds a total amount of time (hours + minutes) as a manual entry. */
@Composable
fun AddTimeDialog(
    project: Project,
    onDismiss: () -> Unit,
    onAdd: (durationMillis: Long) -> Unit,
) {
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    val h = hours.toIntOrNull() ?: 0
    val m = minutes.toIntOrNull() ?: 0
    val validInput = (hours.isEmpty() || hours.toIntOrNull() != null) &&
        (minutes.isEmpty() || minutes.toIntOrNull() != null) && h in 0..24 && m in 0..59
    val total = (h * 60L + m) * 60_000L
    val valid = validInput && total > 0 && total <= 24 * 3_600_000L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Adds time to ${project.number} ${project.name} for today. " +
                        "It's logged as a manual entry stamped with the current time.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hours") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        isError = m > 59,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (validInput && total > 24 * 3_600_000L) {
                    Text("Up to 24 hours at a time", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(total) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Changes the start time of the running timer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeStartDialog(
    running: TimeEntry,
    earliestStart: Long,
    now: Long,
    onDismiss: () -> Unit,
    onChange: (newStart: Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(running.startMillis).atZone(zone).toLocalTime()
    val state = rememberTimePickerState(
        initialHour = current.hour,
        initialMinute = current.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    val day = LocalDate.ofEpochDay(running.day)
    // Keep the original seconds if the minute isn't changed, so an unchanged time stays unchanged.
    val picked = LocalTime.of(state.hour, state.minute)
    val pickedMillis = day.atTime(picked).atZone(zone).toInstant().toEpochMilli()
    val chosen = when {
        picked.hour == current.hour && picked.minute == current.minute -> running.startMillis
        // Picking the same minute the previous timer stopped in snaps to that exact stop time.
        pickedMillis < earliestStart && pickedMillis + 60_000 > earliestStart -> earliestStart
        else -> pickedMillis
    }
    val error = when {
        chosen > now -> "Can't be in the future"
        chosen < earliestStart -> "Can't be before ${formatTime(earliestStart)} (midnight or the previous timer's stop)"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change start time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Currently started at ${formatTime(running.startMillis)}. " +
                        "The change is logged as manual, with the original start and the time you changed it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TimeInput(state = state, modifier = Modifier.padding(top = 8.dp))
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null && chosen != running.startMillis,
                onClick = { onChange(chosen) },
            ) { Text("Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picker for the project an entry belongs to. */
@Composable
private fun ProjectPicker(selectedId: Long, projects: List<Project>, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = projects.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(projectLabel(selected), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(" ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            projects.forEach { p ->
                DropdownMenuItem(text = { Text(projectLabel(p)) }, onClick = { onSelect(p.id); open = false })
            }
        }
    }
}

/**
 * Edits one of today's entries. Timer entries: project, start and stop (start only while running).
 * Manual entries: project and amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryDialog(
    entry: TimeEntry,
    dayEntries: List<TimeEntry>,
    projects: Map<Long, Project>,
    now: Long,
    onDismiss: () -> Unit,
    onSaveTimer: (projectId: Long, start: Long, end: Long?) -> Unit,
    onSaveManual: (projectId: Long, durationMillis: Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val choices = projects.values
        .filter { !it.archived || it.id == entry.projectId }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Project::number).thenBy(String.CASE_INSENSITIVE_ORDER, Project::name))
    var projectId by rememberSaveable(entry.id) { mutableLongStateOf(entry.projectId) }

    if (entry.manual) {
        val origMinutes = (entry.durationMillis ?: 0) / 60_000
        var hours by rememberSaveable(entry.id) { mutableStateOf((origMinutes / 60).toString()) }
        var minutes by rememberSaveable(entry.id) { mutableStateOf((origMinutes % 60).toString()) }
        val h = hours.toIntOrNull() ?: 0
        val m = minutes.toIntOrNull() ?: 0
        val total = (h * 60L + m) * 60_000L
        // Keep the exact original amount if the hours/minutes weren't changed.
        val duration = if (h * 60L + m == origMinutes) (entry.durationMillis ?: 0) else total
        val error = when {
            m > 59 -> "Minutes must be 0–59"
            total <= 0 -> "Enter an amount of time"
            total > 24 * 3_600_000L -> "Up to 24 hours at a time"
            else -> null
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit manual entry") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Added at ${formatTime(entry.createdAtMillis)}", style = MaterialTheme.typography.bodyMedium)
                    ProjectPicker(projectId, choices) { projectId = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = hours,
                            onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                            label = { Text("Hours") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                            label = { Text("Minutes") },
                            singleLine = true,
                            isError = m > 59,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = error == null, onClick = { onSaveManual(projectId, duration) }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        return
    }

    val day = LocalDate.ofEpochDay(entry.day)
    val startLocal = Instant.ofEpochMilli(entry.startMillis).atZone(zone).toLocalTime()
    val endLocal = entry.endMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
    val startState = rememberTimePickerState(startLocal.hour, startLocal.minute, is24)
    val endState = rememberTimePickerState(endLocal?.hour ?: 0, endLocal?.minute ?: 0, is24)

    // Unchanged hour:minute keeps the original exact time (with its seconds).
    fun resolve(h: Int, m: Int, orig: Long?, origLocal: LocalTime?): Long =
        if (orig != null && origLocal != null && h == origLocal.hour && m == origLocal.minute) orig
        else day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    val newStart = resolve(startState.hour, startState.minute, entry.startMillis, startLocal)
    val newEnd = if (entry.endMillis == null) null else resolve(endState.hour, endState.minute, entry.endMillis, endLocal)
    val error = TimesheetRepository.validateTimerTimes(entry, newStart, newEnd, dayEntries, now)
    val changed = projectId != entry.projectId || newStart != entry.startMillis || newEnd != entry.endMillis

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit time entry") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProjectPicker(projectId, choices) { projectId = it }
                Text("Start", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                TimeInput(state = startState)
                if (entry.endMillis != null) {
                    Text("Stop", style = MaterialTheme.typography.labelLarge)
                    TimeInput(state = endState)
                } else {
                    Text("Still running", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "The entry is marked as manual and keeps its original times.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = error == null && changed, onClick = { onSaveTimer(projectId, newStart, newEnd) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
