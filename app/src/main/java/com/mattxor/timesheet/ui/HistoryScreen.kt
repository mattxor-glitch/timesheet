package com.mattxor.timesheet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HistoryScreen(vm: TimesheetViewModel, modifier: Modifier) {
    val days by vm.history.collectAsStateWithLifecycle()
    val projects by vm.projectsById.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(setOf<Long>()) }

    if (days.isEmpty()) {
        Text(
            "No history yet.\nEach day's times are saved here after midnight.",
            modifier = modifier.fillMaxWidth().padding(32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(days, key = { it.day }) { day ->
            val isOpen = day.day in expanded
            Card(onClick = { expanded = if (isOpen) expanded - day.day else expanded + day.day }) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatDayLong(day.day),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatHours(day.totalMillis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    day.totals.forEach { row ->
                        val project = projects[row.projectId]
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                project?.number ?: "?",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                project?.name ?: "Deleted project",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                formatHours(row.totalMillis),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    if (isOpen) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(
                            "Time log",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        val entries by remember(day.day) { vm.entriesForDay(day.day) }
                            .collectAsStateWithLifecycle(initialValue = emptyList())
                        EntryLog(
                            entries = entries,
                            projects = projects,
                            now = System.currentTimeMillis(),
                            onDeleteManual = vm::deleteManualEntry,
                        )
                    } else {
                        Text(
                            "Tap to show start/stop times",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
