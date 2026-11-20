package com.buddy.budget.portfolio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.budget.portfolio.PortfolioViewModel
import com.buddy.budget.portfolio.model.AnalysisResult
import com.buddy.budget.portfolio.model.PortfolioMath
import com.buddy.budget.portfolio.model.PortfolioSummary
import com.buddy.budget.portfolio.model.parseNumber
import com.buddy.budget.portfolio.model.percent
import kotlin.math.abs

@Composable
fun ResultsContent(viewModel: PortfolioViewModel, modifier: Modifier) {
    val result = viewModel.result
    if (result == null) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.QueryStats, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                "Set up your assets and tap Analyze to see the tangency portfolio.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        return
    }
    val portfolios = listOfNotNull(result.tangency, result.current, result.hypothetical)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (result.warnings.isNotEmpty()) item { WarningsCard(result.warnings) }
        item { SummaryCard(result) }
        item {
            SectionCard("Mean-variance diagram") {
                MeanVarianceChart(result)
                ChartLegend(result)
            }
        }
        item { WeightsCard(result, portfolios) }
        if (portfolios.size > 1) item { ComparisonCard(result, portfolios) }
        item { BetasCard(result) }
        item { AllocationCard(viewModel, result) }
        item {
            SectionCard("Correlation") {
                CorrelationHeatmap(result)
            }
        }
        item { DataCard(result) }
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "Warnings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            warnings.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(result: AnalysisResult) {
    val tangency = result.tangency
    SectionCard("Tangency portfolio") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
            StatTile("Expected return", tangency.stats.expectedReturn.percent(), Modifier.weight(1f))
            StatTile("Volatility", tangency.stats.volatility.percent(), Modifier.weight(1f))
            StatTile("Sharpe ratio", "%.4f".format(tangency.stats.sharpeRatio), Modifier.weight(1f))
            if (tangency.marketBeta != null && tangency.adjustedBeta != null) {
                StatTile("Market beta", "%.4f".format(tangency.marketBeta), Modifier.weight(1f))
                StatTile("BBG adj. beta", "%.4f".format(tangency.adjustedBeta), Modifier.weight(1f))
                Box(Modifier.weight(1f))
            }
        }
        Text(
            "${result.startDate} → ${result.endDate} · ${result.observations} ${result.frequency.label.lowercase()} returns · " +
                "rf ${result.riskFreeRate.percent()} · shorts ${if (result.allowShort) "allowed" else "forbidden"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartLegend(result: AnalysisResult) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LegendItem(MaterialTheme.colorScheme.primary, "Frontier")
        LegendItem(CmlColor, "CML / risk-free")
        LegendItem(TangencyColor, "Tangency")
        LegendItem(MaterialTheme.colorScheme.onSurfaceVariant, "Assets")
        if (result.current != null) LegendItem(CurrentColor, "My portfolio")
        if (result.hypothetical != null) LegendItem(HypotheticalColor, "Hypothetical")
    }
    Text(
        "x: annual volatility · y: annual expected return",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
    }
}

/** A simple table row: first cell takes the remaining width, the rest are right-aligned numbers. */
@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, firstWeight: Float = 1.4f) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(
                cell,
                style = style,
                color = color,
                maxLines = 1,
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(if (index == 0) firstWeight else 1f),
            )
        }
    }
}

@Composable
private fun WeightsCard(result: AnalysisResult, portfolios: List<PortfolioSummary>) {
    val order = result.tickers.indices.sortedByDescending { result.tangency.weights[it] }
    val largest = result.tangency.weights.maxOf { abs(it) }.coerceAtLeast(1e-9)
    SectionCard("Weights") {
        TableRow(listOf("Asset", "") + portfolios.map { it.name }, header = true)
        order.forEach { index ->
            val weight = result.tangency.weights[index]
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(shortTicker(result.tickers[index]), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.4f))
                Box(Modifier.weight(1f).height(10.dp)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((abs(weight) / largest).toFloat().coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (weight >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    )
                }
                portfolios.forEach { portfolio ->
                    Text(
                        portfolio.weights[index].percent(1),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(result: AnalysisResult, portfolios: List<PortfolioSummary>) {
    SectionCard("Portfolio comparison") {
        TableRow(listOf("") + portfolios.map { it.name }, header = true)
        TableRow(listOf("Expected return") + portfolios.map { it.stats.expectedReturn.percent() })
        TableRow(listOf("Volatility") + portfolios.map { it.stats.volatility.percent() })
        TableRow(listOf("Sharpe ratio") + portfolios.map { "%.4f".format(it.stats.sharpeRatio) })
        if (result.marketProxy != null) {
            TableRow(listOf("Market beta") + portfolios.map { "%.4f".format(it.marketBeta) })
            TableRow(listOf("BBG adj. beta") + portfolios.map { "%.4f".format(it.adjustedBeta) })
        }
        TableRow(listOf("Sharpe gap vs T") + portfolios.map { "%.4f".format(result.tangency.stats.sharpeRatio - it.stats.sharpeRatio) })
    }
}

@Composable
private fun BetasCard(result: AnalysisResult) {
    val proxy = result.marketProxy
    SectionCard("Betas") {
        val header = listOf("Asset", "E[r]", "σ", "β tangency") + if (proxy != null) listOf("β $proxy", "Adj. β") else emptyList()
        TableRow(header, header = true, firstWeight = 1.2f)
        result.tickers.indices.forEach { index ->
            val cells = listOf(
                shortTicker(result.tickers[index]),
                result.expectedReturns[index].percent(1),
                result.volatilities[index].percent(1),
                "%.3f".format(result.betasToTangency[index]),
            ) + if (result.marketBetas != null && result.adjustedBetas != null) {
                listOf("%.3f".format(result.marketBetas[index]), "%.3f".format(result.adjustedBetas[index]))
            } else {
                emptyList()
            }
            TableRow(cells, firstWeight = 1.2f)
        }
        if (proxy != null) {
            Text(
                "Adjusted β = 0.66 × β + 0.34 (Bloomberg shrinkage toward 1)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AllocationCard(viewModel: PortfolioViewModel, result: AnalysisResult) {
    val settings = viewModel.settings
    val tangency = result.tangency.stats
    val chosenAversion = parseNumber(settings.riskAversion)?.takeIf { it > 0 }
    val aversions = (listOf(1.0, 2.0, 5.0) + listOfNotNull(chosenAversion)).distinct().sorted()

    SectionCard("Capital Market Line") {
        Text(
            "E[r] = ${result.riskFreeRate.percent()} + ${"%.4f".format(tangency.sharpeRatio)} × σ",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "Your mix of risk-free and tangency depends on risk aversion A. Higher A is more conservative; " +
                "a tangency share above 100% means borrowing at the risk-free rate.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = settings.riskAversion,
                onValueChange = { value -> viewModel.update { it.copy(riskAversion = value) } },
                label = { Text("Risk aversion A") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = settings.capital,
                onValueChange = { value -> viewModel.update { it.copy(capital = value) } },
                label = { Text("Capital") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        TableRow(listOf("A", "Tangency", "Risk-free", "E[r]", "σ"), header = true, firstWeight = 0.8f)
        aversions.forEach { aversion ->
            val allocation = PortfolioMath.cmlAllocation(aversion, result.riskFreeRate, tangency)
            val label = when {
                aversion >= 5 -> "conservative"
                aversion >= 2 -> "moderate"
                else -> "aggressive"
            }
            TableRow(
                listOf(
                    "%.1f".format(aversion) + if (aversion == chosenAversion) " ★" else "",
                    allocation.tangencyWeight.percent(1),
                    allocation.riskFreeWeight.percent(1),
                    allocation.expectedReturn.percent(),
                    allocation.volatility.percent(),
                ),
                firstWeight = 0.8f,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val capital = parseNumber(settings.capital)?.takeIf { it > 0 }
        if (capital != null) {
            val tangencyWeight = chosenAversion?.let { PortfolioMath.cmlAllocation(it, result.riskFreeRate, tangency).tangencyWeight } ?: 1.0
            val allocation = PortfolioMath.capitalAllocation(capital, tangencyWeight, result.tangency.weights, result.lastPrices)
            HorizontalDivider()
            Text("Capital allocation", style = MaterialTheme.typography.titleSmall)
            Text(
                "In tangency ${tangencyWeight.percent(1)} (${money(capital * tangencyWeight)}) · " +
                    "risk-free ${(1 - tangencyWeight).percent(1)} (${money(allocation.riskFreeAmount)})" +
                    if (chosenAversion == null) " · set A to split with the risk-free asset" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            if (tangencyWeight > 1) {
                Text("Negative risk-free amount means borrowing to buy more stocks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            TableRow(listOf("Asset", "Target", "Price", "Shares", "Cost"), header = true, firstWeight = 1.1f)
            result.tickers.indices.filter { abs(allocation.targets[it]) > 0.005 }.forEach { index ->
                TableRow(
                    listOf(
                        shortTicker(result.tickers[index]),
                        money(allocation.targets[index]),
                        money(result.lastPrices[index]),
                        "%.0f".format(allocation.shares[index]),
                        money(allocation.costs[index]),
                    ),
                    firstWeight = 1.1f,
                )
            }
            Text(
                "Leftover cash from rounding down: ${money(allocation.leftover)}. Prices are the last close in the window.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun money(value: Double) = "%,.2f".format(value)

@Composable
private fun DataCard(result: AnalysisResult) {
    SectionCard("Data & assumptions") {
        TableRow(listOf("Interval", "${result.startDate} → ${result.endDate}"), firstWeight = 1f)
        TableRow(listOf("Frequency", "${result.frequency.label} (${result.frequency.periodsPerYear}/year)"), firstWeight = 1f)
        TableRow(listOf("Observations", "${result.observations} returns"), firstWeight = 1f)
        TableRow(listOf("Market proxy", result.marketProxy ?: "none"), firstWeight = 1f)
        Text(
            "Mean-variance assumes Gaussian, i.i.d. returns and that past returns persist. Thin trading distorts " +
                "covariances, CAPM misses size/value/momentum effects, and the CML assumes you borrow at the " +
                "risk-free rate. Match the risk-free maturity to your horizon. This is analysis, not investment advice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
