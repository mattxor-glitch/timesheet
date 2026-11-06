package com.mattxor.timesheet.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mattxor.timesheet.data.Project
import com.mattxor.timesheet.data.TimeEntry
import com.mattxor.timesheet.data.TimesheetRepository

@Composable
fun TimersScreen(
    vm: TimesheetViewModel,
    modifier: Modifier,
    onEditProject: (Project) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val entries by vm.todayEntries.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val dbSize by vm.dbSizeBytes.collectAsStateWithLifecycle()
    val projectsById by vm.projectsById.collectAsStateWithLifecycle()
    var showLog by rememberSaveable { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Project?>(null) }
    var addTimeFor by remember { mutableStateOf<Project?>(null) }
    var changingStart by rememberSaveable { mutableStateOf(false) }
    val warningDismissed by vm.sizeWarningDismissed.collectAsStateWithLifecycle()

    val totals: Map<Long, Long> = entries.groupBy { it.projectId }
        .mapValues { (_, list) -> list.sumOf { it.elapsed(now) } }
    val running: TimeEntry? = entries.firstOrNull { it.isRunning }
    val runningProject = running?.let { r -> projects.firstOrNull { it.id == r.projectId } }
    val dayTotal = entries.sumOf { it.elapsed(now) }
    val haptics = LocalHapticFeedback.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (dbSize > TimesheetRepository.SIZE_WARNING_BYTES && !warningDismissed) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SizeWarning(dbSize, onManage = onOpenSettings, onDismiss = vm::dismissSizeWarning)
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (running != null) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Today", style = MaterialTheme.typography.labelLarge)
                        Text(
                            formatClock(dayTotal),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            if (runningProject != null) "Running: ${runningProject.number} ${runningProject.name}"
                            else "No timer running",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (running != null) {
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.stopAll()
                        }) { Text("Stop") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (running != null) {
                        TextButton(onClick = { changingStart = true }) { Text("Change start") }
                    }
                    TextButton(onClick = { showLog = !showLog }) {
                        Text(if (showLog) "Hide log" else "Today's log (${entries.size})")
                    }
                }
            }
        }

        if (showLog) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card {
                    EntryLog(
                        entries = entries,
                        projects = projectsById,
                        now = now,
                        canEdit = true,
                        onDelete = vm::deleteEntry,
                        onEditTimer = vm::editTimerEntry,
                        onEditManual = vm::editManualEntry,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }

        if (projects.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "No projects yet.\nTap \"+ Project\" to add your first project number and name.\n" +
                        "Long-press a tile later to add time, change the start time, or edit it.",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        items(projects, key = { it.id }) { project ->
            ProjectTile(
                project = project,
                totalMillis = totals[project.id] ?: 0L,
                isRunning = running?.projectId == project.id,
                onTap = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.onTileTapped(project)
                },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actionsFor = project
                },
            )
        }
    }

    actionsFor?.let { project ->
        ProjectActionsDialog(
            project = project,
            isRunning = running?.projectId == project.id,
            onDismiss = { actionsFor = null },
            onAddTime = {
                actionsFor = null
                addTimeFor = project
            },
            onChangeStart = {
                actionsFor = null
                changingStart = true
            },
            onEdit = {
                actionsFor = null
                onEditProject(project)
            },
        )
    }

    addTimeFor?.let { project ->
        AddTimeDialog(
            project = project,
            onDismiss = { addTimeFor = null },
            onAdd = { duration ->
                vm.addManualTime(project, duration)
                addTimeFor = null
            },
        )
    }

    // If the timer stops while the dialog is open, close it rather than reopening later.
    LaunchedEffect(running == null) { if (running == null) changingStart = false }
    if (changingStart) {
        val entry = running
        if (entry != null) {
            val earliest by produceState<Long?>(null, entry.id, entry.startMillis) {
                value = vm.earliestStartFor(entry)
            }
            earliest?.let { e ->
                ChangeStartDialog(
                    running = entry,
                    earliestStart = e,
                    now = now,
                    onDismiss = { changingStart = false },
                    onChange = { newStart ->
                        vm.changeRunningStart(newStart)
                        changingStart = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectTile(
    project: Project,
    totalMillis: Long,
    isRunning: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = if (isRunning) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    Card(
        colors = colors,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isRunning) 6.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp)
                .combinedClickable(onClick = onTap, onLongClick = onLongPress, onLongClickLabel = "Edit project")
                .padding(14.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        project.number,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isRunning) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Running", modifier = Modifier.size(22.dp))
                    }
                }
                Text(
                    project.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    formatClock(totalMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun SizeWarning(sizeBytes: Long, onManage: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("History database is large", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "It's using ${formatBytes(sizeBytes)}, over the 50 MB limit. " +
                    "You can clear history before a date in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                TextButton(onClick = onManage) { Text("Clear history") }
            }
        }
    }
}
