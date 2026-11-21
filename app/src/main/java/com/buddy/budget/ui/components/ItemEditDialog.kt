package com.buddy.budget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buddy.budget.model.Category
import com.buddy.budget.model.DraftItem

/** Whether the dialog edits this month's item or creates a new recurring one. */
enum class ItemDialogMode { EDIT, CREATE }

/**
 * Edits an item's name, category, amount, day, fatura and inflow flags (budget.py's "600 n d5 +" modifiers),
 * or creates a new recurring item. Fatura items have no day: they are paid on the fatura debit day.
 */
@Composable
fun ItemEditDialog(
    item: DraftItem,
    title: String,
    mode: ItemDialogMode,
    allowInflow: Boolean,
    allowCategory: Boolean,
    isNameTaken: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (DraftItem) -> Unit,
    onRemove: (() -> Unit)?,
    removeLabel: String = "Remove",
) {
    var name by remember { mutableStateOf(item.name) }
    var amount by remember { mutableStateOf(item.amount) }
    var day by remember { mutableStateOf(item.day?.toString() ?: "") }
    var isFatura by remember { mutableStateOf(item.isFatura) }
    var inflow by remember { mutableStateOf(item.inflow) }
    var category by remember { mutableStateOf(item.category) }
    val parsedDay = day.trim().toIntOrNull()
    val dayValid = day.isBlank() || parsedDay in 1..31
    val nameError = when {
        name.isBlank() -> "Enter a name"
        name.trim() != item.name && isNameTaken(name) -> "Another item already has this name"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (allowCategory) CategoryPicker(category) { category = it }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    prefix = { Text("R$ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = if (isFatura) "" else day,
                    onValueChange = { day = it },
                    label = { Text("Day of the month") },
                    placeholder = { Text(if (isFatura) "Paid on the fatura day" else "Blank if it varies") },
                    enabled = !isFatura,
                    isError = !isFatura && !dayValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow("On the credit card (fatura)", isFatura) { isFatura = it }
                if (allowInflow) SwitchRow("Inflow (adds to the balance)", inflow) { inflow = it }
                val note = when {
                    mode == ItemDialogMode.CREATE -> "Added to your defaults and to this month."
                    item.oneOff -> "Only for this month."
                    else -> "Name and category also change your defaults. Amount, day and flags apply to this month; use Save as defaults to keep them."
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = nameError == null && (isFatura || dayValid),
                onClick = {
                    onSave(
                        item.copy(
                            name = name.trim(),
                            amount = amount,
                            day = if (isFatura) null else parsedDay,
                            isFatura = isFatura,
                            inflow = allowInflow && inflow,
                            category = category,
                        ),
                    )
                },
            ) { Text(if (mode == ItemDialogMode.CREATE) "Add" else "Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text(removeLabel, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** A button showing the current category that opens a menu of every category. */
@Composable
private fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // Debt and taxes have their own sections; expenses pick from the rest.
    val choices = Category.entries.filter { it != Category.DEBT && it != Category.TAXES }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Category: ${Category.label(selected)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
                    onClick = {
                        onSelect(category.name.lowercase())
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
