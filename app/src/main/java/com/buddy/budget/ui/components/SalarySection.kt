package com.buddy.budget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.buddy.budget.model.BudgetDraft

/** Net salary, leftover, proventos and the optional fatura lump sum. */
@Composable
fun SalarySection(
    draft: BudgetDraft,
    leftoverHint: String,
    onSalaryChange: (String) -> Unit,
    onLeftoverChange: (String) -> Unit,
    onProventosChange: (String) -> Unit,
    onUseFaturaLumpChange: (Boolean) -> Unit,
    onFaturaLumpChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Income",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmountField("Net salary", draft.salary, onSalaryChange, Modifier.weight(1f))
                AmountField("Leftover", draft.leftover, onLeftoverChange, Modifier.weight(1f), supporting = leftoverHint)
            }
            AmountField(
                "Proventos",
                draft.proventos,
                onProventosChange,
                Modifier.fillMaxWidth(),
                supporting = "Income received before the fatura debits (e.g. dividends); offsets the fatura",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fatura as one total", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        "Use the card bill's total instead of itemizing fatura expenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Switch(checked = draft.useFaturaLump, onCheckedChange = onUseFaturaLumpChange)
            }
            if (draft.useFaturaLump) {
                AmountField("Fatura total", draft.faturaLump, onFaturaLumpChange, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AmountField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, supporting: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        prefix = { Text("R$ ") },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}
