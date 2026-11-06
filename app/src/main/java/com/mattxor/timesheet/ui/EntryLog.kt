package com.mattxor.timesheet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Each entry has a menu to delete it, and to edit it when [canEdit] is true (today only).
 */
@Composable
fun EntryLog(
    entries: List<TimeEntry>,
    projects: Map<Long, Project>,
    now: Long,
    canEdit: Boolean,
    onDelete: (TimeEntry) -> Unit,
    onEditTimer: (entry: TimeEntry, projectId: Long, start: Long, end: Long?) -> Unit,
    onEditManual: (entry: TimeEntry, projectId: Long, durationMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf<TimeEntry?>(null) }
    var editing by remember { mutableStateOf<TimeEntry?>(null) }
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
            EntryRow(
                entry = entry,
                project = projects[entry.projectId],
                projects = projects,
                now = now,
                canEdit = canEdit,
                onEdit = { editing = entry },
                onDelete = { confirmDelete = entry },
            )
        }
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete entry?") },
            text = {
                Text(
                    "${describe(entry)} on ${projectLabel(projects[entry.projectId])} will be permanently deleted." +
                        if (entry.isRunning) " The timer stops." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entry)
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    editing?.let { original ->
        // Use the latest copy of the entry in case it changed (e.g. the timer was stopped).
        val entry = entries.firstOrNull { it.id == original.id }
        LaunchedEffect(entry == null) { if (entry == null) editing = null }
        if (entry != null) {
            EditEntryDialog(
                entry = entry,
                dayEntries = entries,
                projects = projects,
                now = now,
                onDismiss = { editing = null },
                onSaveTimer = { projectId, start, end ->
                    onEditTimer(entry, projectId, start, end)
                    editing = null
                },
                onSaveManual = { projectId, duration ->
                    onEditManual(entry, projectId, duration)
                    editing = null
                },
            )
        }
    }
}

private fun TimeEntry.sortTime(): Long = if (manual) createdAtMillis else startMillis

fun projectLabel(p: Project?): String = p?.let { "${it.number} ${it.name}".trim() } ?: "Deleted project"

private fun describe(e: TimeEntry): String = when {
    e.isAdjustment -> "Set total to " + formatDuration(e.setTotalMillis ?: 0)
    e.manual -> "+" + formatDuration(e.durationMillis ?: 0)
    e.endMillis == null -> "${formatTime(e.startMillis)} – running"
    else -> "${formatTime(e.startMillis)} – ${formatTime(e.endMillis)}"
}

/** "Edited at 11:40 a.m. · was 8:45 a.m. – 10:12 a.m. · was on 1001" */
private fun editNote(e: TimeEntry, projects: Map<Long, Project>): String? {
    val editedAt = e.startEditedAtMillis ?: return null
    val parts = mutableListOf("Edited at ${formatTime(editedAt)}")
    if (e.manual) {
        e.originalDurationMillis?.let { parts += "was +${formatDuration(it)}" }
    } else if (e.originalStartMillis != null || e.originalEndMillis != null) {
        val s = formatTime(e.originalStartMillis ?: e.startMillis)
        val end = e.originalEndMillis ?: e.endMillis
        parts += "was $s – ${end?.let { formatTime(it) } ?: "running"}"
    }
    e.originalProjectId?.let { parts += "was on ${projects[it]?.number?.ifBlank { null } ?: projectLabel(projects[it])}" }
    return parts.joinToString(" · ")
}

@Composable
private fun EntryRow(
    entry: TimeEntry,
    project: Project?,
    projects: Map<Long, Project>,
    now: Long,
    canEdit: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    describe(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                if (entry.manual || entry.isEdited) {
                    Spacer(Modifier.width(8.dp))
                    ManualTag()
                }
            }
            Text(
                projectLabel(project),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val notes = listOfNotNull(
                when {
                    entry.isAdjustment -> {
                        val delta = entry.durationMillis ?: 0
                        "At ${formatTime(entry.createdAtMillis)} · was ${formatDuration((entry.setTotalMillis ?: 0) - delta)} · " +
                            formatSignedDuration(delta)
                    }
                    entry.manual -> "Added at ${formatTime(entry.createdAtMillis)}"
                    else -> null
                },
                editNote(entry, projects),
            )
            notes.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!entry.manual) {
            Text(
                formatClock(entry.elapsed(now)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Entry options")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (canEdit && !entry.isAdjustment) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onDelete() },
                )
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
