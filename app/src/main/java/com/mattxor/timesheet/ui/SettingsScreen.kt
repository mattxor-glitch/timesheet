package com.mattxor.timesheet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mattxor.timesheet.data.Retention
import com.mattxor.timesheet.data.TimesheetRepository

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: TimesheetViewModel, modifier: Modifier) {
    val retention by vm.retention.collectAsStateWithLifecycle()
    val carryOver by vm.carryOverMidnight.collectAsStateWithLifecycle()
    val dbSize by vm.dbSizeBytes.collectAsStateWithLifecycle()
    val oldest by vm.oldestDay.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    var showRetention by rememberSaveable { mutableStateOf(false) }
    var pendingRetention by rememberSaveable { mutableStateOf<Retention?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var pendingClearDay by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SectionHeader("History")
        SettingRow(
            title = "Keep history for",
            subtitle = retention.label,
            onClick = { showRetention = true },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { vm.setCarryOverMidnight(!carryOver) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Keep timer running past midnight", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (carryOver) "A running timer continues on the new day, starting from zero"
                    else "A running timer is stopped at midnight",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = carryOver, onCheckedChange = { vm.setCarryOverMidnight(it) })
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionHeader("Storage")
        val over = dbSize > TimesheetRepository.SIZE_WARNING_BYTES
        SettingRow(
            title = "Database size",
            subtitle = formatBytes(dbSize) + if (over) " — over the 50 MB limit" else " (warning at 50 MB)",
            subtitleColor = if (over) MaterialTheme.colorScheme.error else null,
            onClick = { vm.refreshDatabaseSize() },
        )
        SettingRow(
            title = "Oldest record",
            subtitle = oldest?.let { formatDateMdy(it) } ?: "No records yet",
            onClick = null,
        )
        OutlinedButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("Clear history before a date…") }
    }

    if (showRetention) {
        AlertDialog(
            onDismissRequest = { showRetention = false },
            title = { Text("Keep history for") },
            text = {
                Column(Modifier.selectableGroup()) {
                    Retention.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = option == retention,
                                    role = Role.RadioButton,
                                    onClick = {
                                        showRetention = false
                                        val cutoff = option.days?.let { today - it }
                                        val wouldDelete = cutoff != null && oldest != null && oldest!! < cutoff
                                        if (wouldDelete) pendingRetention = option else vm.setRetention(option)
                                    },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == retention, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRetention = false }) { Text("Cancel") } },
        )
    }

    pendingRetention?.let { option ->
        val cutoff = today - (option.days ?: 0)
        AlertDialog(
            onDismissRequest = { pendingRetention = null },
            title = { Text("Delete older history?") },
            text = {
                Text("Keeping ${option.label.lowercase()} will permanently delete history before ${formatDateMdy(cutoff)}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setRetention(option)
                    pendingRetention = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingRetention = null }) { Text("Cancel") } },
        )
    }

    if (showDatePicker) {
        val todayUtcMs = today * DAY_MS
        val state = rememberDatePickerState(
            initialSelectedDateMillis = todayUtcMs - 30 * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMs
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        pendingClearDay = state.selectedDateMillis?.let { Math.floorDiv(it, DAY_MS) }
                        showDatePicker = false
                    },
                ) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(
                state = state,
                title = {
                    Text(
                        "Clear history before",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    )
                },
            )
        }
    }

    pendingClearDay?.let { day ->
        AlertDialog(
            onDismissRequest = { pendingClearDay = null },
            title = { Text("Clear history?") },
            text = {
                Text(
                    "All history before ${formatDateMdy(day)} will be permanently deleted. " +
                        "Entries on ${formatDateMdy(day)} and later are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistoryBefore(day)
                    pendingClearDay = null
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingClearDay = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    subtitleColor: androidx.compose.ui.graphics.Color? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
