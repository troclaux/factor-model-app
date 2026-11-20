package com.buddy.budget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.buddy.budget.model.DraftItem

/**
 * Edits an item's amount, day, fatura and inflow flags (budget.py's "600 n d5 +" modifiers).
 * Fatura items have no day: they are paid on the fatura debit day. One-off items can be renamed and removed.
 */
@Composable
fun ItemEditDialog(
    item: DraftItem,
    allowInflow: Boolean,
    onDismiss: () -> Unit,
    onSave: (DraftItem) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(item.name) }
    var amount by remember { mutableStateOf(item.amount) }
    var day by remember { mutableStateOf(item.day?.toString() ?: "") }
    var isFatura by remember { mutableStateOf(item.isFatura) }
    var inflow by remember { mutableStateOf(item.inflow) }
    val parsedDay = day.trim().toIntOrNull()
    val dayValid = day.isBlank() || parsedDay in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.oneOff) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = isFatura || dayValid,
                onClick = {
                    onSave(
                        item.copy(
                            name = name.trim().ifEmpty { item.name },
                            amount = amount,
                            day = if (isFatura) null else parsedDay,
                            isFatura = isFatura,
                            inflow = allowInflow && inflow,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
