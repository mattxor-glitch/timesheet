package com.mattxor.timesheet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mattxor.timesheet.data.Project
import com.mattxor.timesheet.data.TimeEntry

/**
 * The time-stamped log for one day: every start/stop and every manual entry, oldest first.
 */
@Composable
fun EntryLog(
    entries: List<TimeEntry>,
    projects: Map<Long, Project>,
    now: Long,
    onDeleteManual: (TimeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf<TimeEntry?>(null) }
    val sorted = entries.sortedWith(compareBy({ it.sortTime() }, { it.id }))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (sorted.isEmpty()) {
            Text(
                "No entries",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        sorted.forEach { entry ->
            EntryRow(entry, projects[entry.projectId], now, onDelete = { confirmDelete = entry })
        }
    }

    confirmDelete?.let { entry ->
        val p = projects[entry.projectId]
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete manual entry?") },
            text = {
                Text(
                    "+${formatDuration(entry.durationMillis ?: 0)} on ${p?.number ?: ""} ${p?.name ?: ""}, " +
                        "added at ${formatTime(entry.createdAtMillis)}."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteManual(entry)
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

private fun TimeEntry.sortTime(): Long = if (manual) createdAtMillis else startMillis

@Composable
private fun EntryRow(entry: TimeEntry, project: Project?, now: Long, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        entry.manual -> "+" + formatDuration(entry.durationMillis ?: 0)
                        entry.endMillis == null -> "${formatTime(entry.startMillis)} – running"
                        else -> "${formatTime(entry.startMillis)} – ${formatTime(entry.endMillis)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                if (entry.manual || entry.startEditedAtMillis != null) {
                    Spacer(Modifier.width(8.dp))
                    ManualTag()
                }
            }
            Text(
                "${project?.number ?: "?"} ${project?.name ?: "Deleted project"}".trim(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val note = when {
                entry.manual -> "Added at ${formatTime(entry.createdAtMillis)}"
                entry.startEditedAtMillis != null && entry.originalStartMillis != null ->
                    "Start changed from ${formatTime(entry.originalStartMillis)} at ${formatTime(entry.startEditedAtMillis)}"
                else -> null
            }
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!entry.manual) {
            Text(
                formatClock(entry.elapsed(now)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete manual entry", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ManualTag() {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            "MANUAL",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
