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
 * Edits an item's name, category, amount, day and fatura flag (budget.py's "600 n d5" modifiers),
 * or creates a new recurring item. Fatura items have no day: they are paid on the fatura debit day.
 * With a [defaultAmount] the item is recurring: the dialog also edits its default amount, and saving
 * writes name, category, day and fatura to the defaults too.
 */
@Composable
fun ItemEditDialog(
    item: DraftItem,
    title: String,
    mode: ItemDialogMode,
    /** Categories to pick from; null hides the picker. */
    categories: List<Category>?,
    defaultAmount: String?,
    isNameTaken: (String) -> Boolean,
    onDismiss: () -> Unit,
    /** The edited item and the edited default amount (null when the item has none). */
    onSave: (DraftItem, String?) -> Unit,
    onRemove: (() -> Unit)?,
    removeLabel: String = "Remove",
) {
    var name by remember { mutableStateOf(item.name) }
    var amount by remember { mutableStateOf(item.amount) }
    var day by remember { mutableStateOf(item.day?.toString() ?: "") }
    var isFatura by remember { mutableStateOf(item.isFatura) }
    var defaultValue by remember { mutableStateOf(defaultAmount) }
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
                if (categories != null) CategoryPicker(category, categories) { category = it }
                AmountField(if (defaultValue != null) "This month" else "Amount", amount) { amount = it }
                defaultValue?.let { value -> AmountField("Default", value) { defaultValue = it } }
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
                val note = when {
                    mode == ItemDialogMode.CREATE -> "Added to your defaults and to this month."
                    defaultValue == null -> "Only for this month."
                    else -> "\"This month\" applies to this month only. Name, category, default, day and fatura are saved to your defaults."
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
                            category = category,
                        ),
                        defaultValue,
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

@Composable
private fun AmountField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        prefix = { Text("R$ ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A button showing the current category that opens a menu of [choices]. */
@Composable
private fun CategoryPicker(selected: String, choices: List<Category>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
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
