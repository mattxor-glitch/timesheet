package com.mattxor.timesheet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mattxor.timesheet.data.Project

@Composable
fun ProjectDialog(
    project: Project?,
    onDismiss: () -> Unit,
    onSave: (number: String, name: String, budgetMillis: Long?) -> Unit,
    onRemove: (() -> Unit)?,
    usedMillis: Long? = null,
) {
    var number by rememberSaveable { mutableStateOf(project?.number ?: "") }
    var name by rememberSaveable { mutableStateOf(project?.name ?: "") }
    var hours by rememberSaveable { mutableStateOf(project?.budgetMillis?.let { formatHoursInput(it) } ?: "") }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val budget = parseHoursInput(hours)
    val budgetInvalid = budget == -1L
    val valid = (number.isNotBlank() || name.isNotBlank()) && !budgetInvalid

    if (confirmRemove && project != null && onRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove project?") },
            text = {
                Text("${project.number} ${project.name} will be removed from the tiles. Its history is kept.")
            },
            confirmButton = { TextButton(onClick = onRemove) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project == null) "Add project" else "Edit project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("Project number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == ':' }.take(9) },
                    label = { Text("Labour hours allotted") },
                    placeholder = { Text("Optional, e.g. 40 or 37.5") },
                    singleLine = true,
                    isError = budgetInvalid,
                    supportingText = {
                        when {
                            budgetInvalid -> Text("Enter hours like 40, 37.5 or 37:30")
                            usedMillis != null && budget != null ->
                                Text("Used ${formatSignedHm(usedMillis)} · left ${formatSignedHm(budget - usedMillis)}")
                            usedMillis != null -> Text("Used so far: ${formatSignedHm(usedMillis)}")
                            else -> Text("Leave blank for no limit")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(number, name, budget) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = { confirmRemove = true }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
