package com.mattxor.timesheet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mattxor.timesheet.data.Project
import kotlinx.coroutines.delay

enum class Tab(val label: String, val icon: ImageVector) {
    TIMERS("Timers", Icons.Filled.PlayArrow),
    HISTORY("History", Icons.Filled.DateRange),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimesheetApp(vm: TimesheetViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.TIMERS) }
    var editing by remember { mutableStateOf<ProjectEdit?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val today by vm.today.collectAsStateWithLifecycle()
    val projectsById by vm.projectsById.collectAsStateWithLifecycle()
    val projectTotals by vm.projectTotals.collectAsStateWithLifecycle()
    val todayEntries by vm.todayEntries.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    // Tick once a second while visible; also performs the midnight reset.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.tick()
                delay(1000 - System.currentTimeMillis() % 1000)
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = tab != Tab.TIMERS) { tab = Tab.TIMERS }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (tab) {
                            Tab.TIMERS -> formatDayShort(today)
                            Tab.HISTORY -> "History"
                            Tab.SETTINGS -> "Settings"
                        }
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            if (t == Tab.SETTINGS) vm.refreshDatabaseSize()
                        },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == Tab.TIMERS) {
                ExtendedFloatingActionButton(
                    onClick = { editing = ProjectEdit(null) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Project") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.TIMERS -> TimersScreen(
                vm = vm,
                modifier = modifier,
                onEditProject = { editing = ProjectEdit(it) },
                onOpenSettings = {
                    tab = Tab.SETTINGS
                    vm.refreshDatabaseSize()
                },
            )
            Tab.HISTORY -> HistoryScreen(vm = vm, modifier = modifier)
            Tab.SETTINGS -> SettingsScreen(vm = vm, modifier = modifier)
        }
    }

    editing?.let { edit ->
        ProjectDialog(
            project = edit.project,
            onDismiss = { editing = null },
            onSave = { number, name, budget ->
                if (edit.project == null) vm.addProject(number, name, budget)
                else vm.updateProject(edit.project.copy(number = number, name = name, budgetMillis = budget))
                editing = null
            },
            usedMillis = edit.project?.let { p ->
                // Use the latest copy of the project (its cleared-history time may have changed).
                val latest = projectsById[p.id] ?: p
                usedMillis(latest, projectTotals, todayEntries, now)
            },
            onRemove = edit.project?.let { p ->
                {
                    vm.removeProject(p)
                    editing = null
                }
            },
        )
    }
}

/** Wrapper so "add new" (null project) can be distinguished from "no dialog". */
data class ProjectEdit(val project: Project?)
