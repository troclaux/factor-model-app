package com.buddy.budget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddy.budget.model.BudgetReport
import com.buddy.budget.ui.theme.Green700
import com.buddy.budget.ui.theme.Red700
import java.text.NumberFormat
import java.util.Locale

/** budget.py's summary: net salary + leftover + inflows − outflows = free to spend (or deficit). */
@Composable
fun BalanceSummary(
    report: BudgetReport,
    modifier: Modifier = Modifier
) {
    val fmt = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val isPositive = report.balance >= 0
    val balanceColor = if (isPositive) Green700 else Red700
    val label = if (isPositive) "FREE TO SPEND" else "DEFICIT"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SummaryRow("Net salary", fmt.format(report.month.salary))
            if (report.month.leftover != 0.0) {
                SummaryRow("Leftover", "+ ${fmt.format(report.month.leftover)}")
            }
            if (report.totalInflow != 0.0) {
                SummaryRow("Inflows", "+ ${fmt.format(report.totalInflow)}")
            }
            SummaryRow("Outflows", "- ${fmt.format(report.totalOutflow)}")

            // Balance — the main number
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
                Text(
                    text = fmt.format(kotlin.math.abs(report.balance)),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = balanceColor
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
