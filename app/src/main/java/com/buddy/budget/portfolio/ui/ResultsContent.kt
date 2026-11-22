package com.buddy.budget.portfolio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.budget.portfolio.PortfolioViewModel
import com.buddy.budget.portfolio.model.AnalysisResult
import com.buddy.budget.portfolio.model.Glossary
import com.buddy.budget.portfolio.model.PortfolioMath
import com.buddy.budget.portfolio.model.PortfolioSummary
import com.buddy.budget.portfolio.model.Report
import com.buddy.budget.portfolio.model.parseNumber
import com.buddy.budget.portfolio.model.percent
import kotlin.math.abs
import kotlinx.coroutines.launch
import com.buddy.budget.i18n.tr

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
                tr("Set up your assets and tap Analyze to see the tangency portfolio."),
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
        viewModel.analyzedAt?.let { ranAt ->
            item {
                Text(
                    tr("Analysis run on %s at %s. Tap Analyze on Setup to refresh.").format(ranAt.toLocalDate(), ranAt.toLocalTime()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { ReportActions(viewModel, result) }
        if (result.warnings.isNotEmpty()) item { WarningsCard(result.warnings) }
        item { SummaryCard(result) }
        item {
            ChartCard(tr("Mean-variance diagram"), "tangency.png") { capture ->
                MeanVarianceChart(result, capture)
                ChartLegend(result)
            }
        }
        item { WeightsCard(result, portfolios) }
        if (portfolios.size > 1) item { ComparisonCard(result, portfolios) }
        item { BetasCard(result) }
        item { AllocationCard(viewModel, result) }
        item {
            ChartCard(tr("Correlation"), "correlation_${result.startDate}_${result.endDate}.png") { capture ->
                CorrelationHeatmap(result, capture)
            }
        }
        item { DataCard(result) }
        item { IntermediateCard(result) }
        item { GlossaryCard() }
    }
}

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    tr("Warnings"),
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
    SectionCard(tr("Tangency portfolio")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
            StatTile(tr("Expected return"), tangency.stats.expectedReturn.percent(), Modifier.weight(1f))
            StatTile(tr("Volatility"), tangency.stats.volatility.percent(), Modifier.weight(1f))
            StatTile(tr("Sharpe ratio"), "%.4f".format(tangency.stats.sharpeRatio), Modifier.weight(1f))
            if (tangency.marketBeta != null && tangency.adjustedBeta != null) {
                StatTile(tr("Market beta"), "%.4f".format(tangency.marketBeta), Modifier.weight(1f))
                StatTile(tr("BBG adj. beta"), "%.4f".format(tangency.adjustedBeta), Modifier.weight(1f))
                Box(Modifier.weight(1f))
            }
        }
        Text(
            "${result.startDate} → ${result.endDate} · " + tr("%d %s returns").format(result.observations, result.frequency.label.lowercase()) + " · " +
                "rf ${result.riskFreeRate.percent()} (${result.riskFreeSource}) · " + tr(if (result.allowShort) "shorts allowed" else "shorts forbidden"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val ratios = result.tangencyRatios
        if (ratios != null) {
            val passed = result.tangencyVerified == true
            Text(
                tr(if (passed) "Verification: PASS · (μᵢ − rf) / Cov(rᵢ, r_T) is the same for every asset (%s to %s)" else "Verification: FAIL · (μᵢ − rf) / Cov(rᵢ, r_T) is not the same for every asset (%s to %s)")
                    .format("%.6f".format(ratios.min()), "%.6f".format(ratios.max())),
                style = MaterialTheme.typography.bodySmall,
                color = if (passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        } else {
            Text(
                tr("Verification skipped: it only applies when short selling is allowed."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportActions(viewModel: PortfolioViewModel, result: AnalysisResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportingPdf by remember { mutableStateOf(false) }
    val riskAversion = parseNumber(viewModel.settings.riskAversion)?.takeIf { it > 0 }
    val capital = parseNumber(viewModel.settings.capital)?.takeIf { it > 0 }
    fun report() = Report.text(result, riskAversion, capital)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                exportingPdf = true
                scope.launch {
                    try {
                        sharePdf(context, result, viewModel.analyzedSettings ?: viewModel.settings, riskAversion, capital, "portfolio_report_${result.endDate}.pdf")
                    } finally {
                        exportingPdf = false
                    }
                }
            },
            enabled = !exportingPdf,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, Modifier.size(18.dp))
            Text(tr(if (exportingPdf) "Creating PDF…" else "Share PDF report"), Modifier.padding(start = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyToClipboard(context, report()) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                Text(tr("Copy report"), Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { shareText(context, report(), "output.txt") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                Text(tr("Share .txt"), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** A chart card whose chart can be shared as a PNG. [content] must apply the given modifier to the chart canvas. */
@Composable
private fun ChartCard(title: String, fileName: String, content: @Composable (Modifier) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val background = MaterialTheme.colorScheme.surface.toArgb()
    SectionCard(title) {
        content(Modifier.recordInto(layer))
        TextButton(onClick = { scope.launch { shareImage(context, layer.toImageBitmap(), background, fileName) } }) {
            Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
            Text(tr("Share PNG"), Modifier.padding(start = 8.dp))
        }
    }
}

/** Explanations of every parameter and metric, collapsed by default. */
@Composable
private fun GlossaryCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("What these numbers mean"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = tr(if (expanded) "Collapse" else "Expand"))
            }
            if (expanded) {
                listOf(tr("Parameters") to Glossary.parameters, tr("Metrics") to Glossary.metrics).forEach { (title, entries) ->
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    entries.forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.term, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(entry.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntermediateCard(result: AnalysisResult) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Intermediate values"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (expanded) {
                    IconButton(onClick = { copyToClipboard(context, Report.intermediate(result)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = tr("Copy intermediate values"))
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = tr(if (expanded) "Collapse" else "Expand"))
                }
            }
            if (expanded) {
                SelectionContainer(Modifier.horizontalScroll(rememberScrollState())) {
                    Text(Report.intermediate(result), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
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
        LegendItem(MaterialTheme.colorScheme.primary, tr("Frontier"))
        LegendItem(CmlColor, tr("CML / risk-free"))
        LegendItem(TangencyColor, tr("Tangency"))
        LegendItem(MaterialTheme.colorScheme.onSurfaceVariant, tr("Assets"))
        if (result.current != null) LegendItem(CurrentColor, tr("My portfolio"))
        if (result.hypothetical != null) LegendItem(HypotheticalColor, tr("Hypothetical"))
    }
    Text(
        tr("x: annual volatility · y: annual expected return"),
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
    SectionCard(tr("Weights")) {
        TableRow(listOf(tr("Asset"), "") + portfolios.map { tr(it.name) }, header = true)
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
    SectionCard(tr("Portfolio comparison")) {
        TableRow(listOf("") + portfolios.map { tr(it.name) }, header = true)
        TableRow(listOf(tr("Expected return")) + portfolios.map { it.stats.expectedReturn.percent() })
        TableRow(listOf(tr("Volatility")) + portfolios.map { it.stats.volatility.percent() })
        TableRow(listOf(tr("Sharpe ratio")) + portfolios.map { "%.4f".format(it.stats.sharpeRatio) })
        if (result.marketProxy != null) {
            TableRow(listOf(tr("Market beta")) + portfolios.map { "%.4f".format(it.marketBeta) })
            TableRow(listOf(tr("BBG adj. beta")) + portfolios.map { "%.4f".format(it.adjustedBeta) })
        }
        TableRow(listOf(tr("Sharpe gap vs T")) + portfolios.map { "%.4f".format(result.tangency.stats.sharpeRatio - it.stats.sharpeRatio) })
    }
}

@Composable
private fun BetasCard(result: AnalysisResult) {
    val proxy = result.marketProxy
    SectionCard(tr("Betas")) {
        val header = listOf(tr("Asset"), "E[r]", "σ", tr("β tangency")) + if (proxy != null) listOf("β $proxy", tr("Adj. β")) else emptyList()
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
                tr("Adjusted β = 0.66 × β + 0.34 (Bloomberg shrinkage toward 1)"),
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

    SectionCard(tr("Capital Market Line")) {
        Text(
            "E[r] = ${result.riskFreeRate.percent()} + ${"%.4f".format(tangency.sharpeRatio)} × σ",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            tr("Your mix of risk-free and tangency depends on risk aversion A. Higher A is more conservative; ") +
                tr("a tangency share above 100% means borrowing at the risk-free rate."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = settings.riskAversion,
                onValueChange = { value -> viewModel.update { it.copy(riskAversion = value) } },
                label = { Text(tr("Risk aversion A")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = settings.capital,
                onValueChange = { value -> viewModel.update { it.copy(capital = value) } },
                label = { Text(tr("Capital")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        TableRow(listOf("A", tr("Tangency"), tr("Risk-free"), "E[r]", "σ"), header = true, firstWeight = 0.8f)
        aversions.forEach { aversion ->
            val allocation = PortfolioMath.cmlAllocation(aversion, result.riskFreeRate, tangency)
            val label = when {
                aversion >= 5 -> tr("conservative")
                aversion >= 2 -> tr("moderate")
                else -> tr("aggressive")
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
            Text(tr("Capital allocation"), style = MaterialTheme.typography.titleSmall)
            Text(
                tr("In tangency %s (%s) · risk-free %s (%s)").format(
                    tangencyWeight.percent(1), money(capital * tangencyWeight), (1 - tangencyWeight).percent(1), money(allocation.riskFreeAmount),
                ) + if (chosenAversion == null) tr(" · set A to split with the risk-free asset") else "",
                style = MaterialTheme.typography.bodySmall,
            )
            if (tangencyWeight > 1) {
                Text(tr("Negative risk-free amount means borrowing to buy more stocks."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            TableRow(listOf(tr("Asset"), tr("Target"), tr("Price"), tr("Shares"), tr("Cost")), header = true, firstWeight = 1.1f)
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
                tr("Leftover cash from rounding down: %s. Prices are the last close in the window.").format(money(allocation.leftover)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun money(value: Double) = "%,.2f".format(value)

@Composable
private fun DataCard(result: AnalysisResult) {
    SectionCard(tr("Data & assumptions")) {
        TableRow(listOf(tr("Interval"), "${result.startDate} → ${result.endDate}"), firstWeight = 1f)
        TableRow(listOf(tr("Frequency"), tr("%s (%d/year)").format(result.frequency.label, result.frequency.periodsPerYear)), firstWeight = 1f)
        TableRow(listOf(tr("Observations"), tr("%d returns").format(result.observations)), firstWeight = 1f)
        TableRow(listOf(tr("Risk-free rate"), "${result.riskFreeRate.percent()} (${result.riskFreeSource})"), firstWeight = 1f)
        TableRow(listOf(tr("Market proxy"), result.marketProxy ?: tr("none")), firstWeight = 1f)
        Text(
            tr("Mean-variance assumes Gaussian, i.i.d. returns and that past returns persist. Thin trading distorts ") +
                tr("covariances, CAPM misses size/value/momentum effects, and the CML assumes you borrow at the ") +
                tr("risk-free rate. Match the risk-free maturity to your horizon. This is analysis, not investment advice."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
