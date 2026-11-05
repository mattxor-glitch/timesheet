package com.mattxor.timesheet.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mattxor.timesheet.data.Project
import com.mattxor.timesheet.data.TimeEntry
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
