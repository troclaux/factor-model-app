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
import com.buddy.budget.i18n.tr

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
                text = tr("Income"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AmountField(tr("Net salary"), draft.salary, onSalaryChange, Modifier.weight(1f))
                AmountField(tr("Leftover"), draft.leftover, onLeftoverChange, Modifier.weight(1f), supporting = leftoverHint)
            }
            AmountField(
                tr("Proventos"),
                draft.proventos,
                onProventosChange,
                Modifier.fillMaxWidth(),
                supporting = tr("Income such as dividends; an inflow of its own, not taken off the fatura"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Fatura as one total"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        tr("Use the card bill's total instead of itemizing fatura expenses"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Switch(checked = draft.useFaturaLump, onCheckedChange = onUseFaturaLumpChange)
            }
            if (draft.useFaturaLump) {
                AmountField(tr("Fatura total"), draft.faturaLump, onFaturaLumpChange, Modifier.fillMaxWidth())
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
