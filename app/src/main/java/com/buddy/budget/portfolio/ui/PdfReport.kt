package com.buddy.budget.portfolio.ui

import android.content.Context
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.buddy.budget.pdf.PdfWriter
import com.buddy.budget.portfolio.model.AnalysisResult
import com.buddy.budget.portfolio.model.Glossary
import com.buddy.budget.portfolio.model.PeriodMode
import com.buddy.budget.portfolio.model.PortfolioInput
import com.buddy.budget.portfolio.model.PortfolioMath
import com.buddy.budget.portfolio.model.PortfolioSettings
import com.buddy.budget.portfolio.model.parseNumber
import com.buddy.budget.portfolio.model.percent
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.min

/** The analysis as a styled A4 PDF: header, summary tiles, charts and tables, paginated. Units are points. */
class PdfReport(
    context: Context,
    private val result: AnalysisResult,
    /** The settings the analysis ran with. */
    private val settings: PortfolioSettings,
    private val riskAversion: Double?,
    private val capital: Double?,
) : PdfWriter(context, "Buddy · Portfolio analysis") {
    private val chartPalette = ChartPalette(
        frontier = Color(ACCENT),
        asset = Color(MUTED),
        grid = Color(RULE),
        label = Color(MUTED),
        text = Color(TEXT),
        neutral = Color(0xFFEEEEEE),
    )

    fun writeTo(file: File) {
        startPage()
        header()
        inputs()
        summary()
        warnings()
        meanVariance()
        weights()
        allocationPies()
        comparison()
        betas()
        capitalMarketLine()
        capitalAllocation()
        correlation()
        assumptions()
        glossary()
        finish(file)
    }

    // ---------- sections ----------

    private fun header() {
        header(
            "Portfolio analysis",
            "${result.startDate} → ${result.endDate}  ·  ${result.observations} ${result.frequency.label.lowercase()} returns  ·  " +
                "generated ${LocalDate.now()}",
        )
    }

    private fun inputs() {
        section("Inputs used")
        val proxy = settings.riskFreeProxy.trim().uppercase()
        fun describe(input: PortfolioInput): String {
            val filled = input.values.values.count { (parseNumber(it) ?: 0.0) != 0.0 }
            return if (filled == 0) "not set" else "$filled positions, entered as ${input.mode.label.lowercase().removeSuffix(" %")}"
        }
        table(
            headers = null,
            rows = listOf(
                listOf("Risk-free rate", result.riskFreeRate.percent() + if (proxy.isNotEmpty()) " (latest yield of $proxy)" else " (entered)"),
                listOf(
                    "Period",
                    when (settings.periodMode) {
                        PeriodMode.LOOKBACK -> "${settings.lookbackAmount.trim()} ${settings.lookbackUnit.label.lowercase()} back from the end of the previous month"
                        PeriodMode.DATES -> "${settings.startDate} to " + if (settings.endLatest) "the latest available close" else settings.endDate
                    },
                ),
                listOf("Price window", "${result.priceDates.first()} → ${result.endDate} (first and last prices used)"),
                listOf("Return frequency", "${result.frequency.label} (${result.frequency.periodsPerYear} periods per year)"),
                listOf("Observations", "${result.observations} returns"),
                listOf("Short selling", if (result.allowShort) "allowed" else "forbidden (all weights ≥ 0)"),
                listOf("Market benchmark", result.marketProxy ?: "none"),
                listOf("Risk aversion A", riskAversion?.let { "%.1f (A = 1, 2 and 5 also shown)".format(it) } ?: "not set (A = 1, 2 and 5 shown)"),
                listOf("Capital", capital?.let { money(it) } ?: "not set"),
                listOf("My portfolio", describe(settings.current)),
                listOf("Hypothetical portfolio", describe(settings.hypothetical)),
                listOf("Prices", "Yahoo Finance adjusted closes"),
            ),
        )
        paragraph("Tickers (${result.tickers.size}): " + result.tickers.joinToString(", "), color = TEXT)
    }

    private fun summary() {
        section("Tangency portfolio")
        val tangency = result.tangency
        val tiles = mutableListOf(
            "Expected return" to tangency.stats.expectedReturn.percent(),
            "Volatility" to tangency.stats.volatility.percent(),
            "Sharpe ratio" to "%.4f".format(tangency.stats.sharpeRatio),
        )
        if (tangency.marketBeta != null && tangency.adjustedBeta != null) {
            tiles += "Market beta (${result.marketProxy})" to "%.4f".format(tangency.marketBeta)
            tiles += "Bloomberg adj. beta" to "%.4f".format(tangency.adjustedBeta)
        }
        tiles += "Risk-free rate" to result.riskFreeRate.percent()
        statTiles(tiles)

        val verified = result.tangencyVerified
        when {
            verified == null -> paragraph("Verification skipped: it only applies when short selling is allowed.")
            verified -> paragraph("✓ Verification passed: (μᵢ − rf) / Cov(rᵢ, r_T) is the same for every asset.", color = ACCENT)
            else -> paragraph("✗ Verification failed: (μᵢ − rf) / Cov(rᵢ, r_T) differs across assets.", color = ERROR)
        }
    }

    private fun warnings() {
        if (result.warnings.isEmpty()) return
        val paint = textPaint(9f, WARNING_TEXT)
        val layouts = result.warnings.map { staticLayout("•  $it", paint, CONTENT_WIDTH - 24f) }
        val height = 30f + layouts.sumOf { it.height + 4 }
        ensure(height + 12f)
        canvas.drawRoundRect(RectF(MARGIN, cursor, MARGIN + CONTENT_WIDTH, cursor + height), 8f, 8f, fill(WARNING_BACKGROUND))
        canvas.drawText("Warnings", MARGIN + 12f, cursor + 18f, text(11f, WARNING_TEXT, bold = true))
        var top = cursor + 26f
        layouts.forEach { layout ->
            drawLayout(layout, MARGIN + 12f, top)
            top += layout.height + 4
        }
        cursor += height + 12f
    }

    private fun meanVariance() {
        section("Mean-variance diagram")
        drawing(300f) { drawMeanVariance(result, measurer, chartPalette) }
        cursor += 8f
        val legend = mutableListOf(ACCENT to "Frontier", CmlColor.toArgb() to "CML", TangencyColor.toArgb() to "Tangency", MUTED to "Assets")
        result.current?.let { legend += CurrentColor.toArgb() to it.name }
        result.hypothetical?.let { legend += HypotheticalColor.toArgb() to it.name }
        ensure(16f)
        var x = MARGIN
        val paint = text(9f, TEXT)
        legend.forEach { (color, label) ->
            canvas.drawCircle(x + 4f, cursor + 6f, 4f, fill(color))
            canvas.drawText(label, x + 12f, cursor + 9f, paint)
            x += 22f + paint.measureText(label)
        }
        cursor += 16f
        paragraph("x: annual volatility · y: annual expected return. Solid frontier is efficient; dashed is not.")
    }

    private fun weights() {
        section("Weights")
        val portfolios = listOfNotNull(result.tangency, result.current, result.hypothetical)
        val order = result.tickers.indices.sortedByDescending { result.tangency.weights[it] }
        table(
            headers = listOf("Asset") + portfolios.map { it.name },
            rows = order.map { index -> listOf(shortTicker(result.tickers[index])) + portfolios.map { it.weights[index].percent(1) } },
            highlightColumn = 1,
        )
    }

    /** Each stock's share of each portfolio's value. Short positions cannot be drawn as slices and are listed instead. */
    private fun allocationPies() {
        section("Allocation by value")
        // Colors follow the ticker list, so a stock has the same color in every pie.
        val colors = result.tickers.withIndex().associate { (index, ticker) -> shortTicker(ticker) to pieColor(index) }
        listOfNotNull(result.tangency, result.current, result.hypothetical).forEach { portfolio ->
            paragraph(portfolio.name, size = 11f, color = TEXT, bold = true)
            pie(result.tickers.indices.map { shortTicker(result.tickers[it]) to portfolio.weights[it] }) { label -> colors.getValue(label) }
            val shorts = result.tickers.indices.filter { portfolio.weights[it] < -1e-9 }
            if (shorts.isNotEmpty()) {
                paragraph(
                    "Short positions, not shown: " + shorts.joinToString { "${shortTicker(result.tickers[it])} ${portfolio.weights[it].percent(1)}" } +
                        ". Slices are shares of the long positions.",
                )
            }
        }
    }

    private fun comparison() {
        val portfolios = listOfNotNull(result.tangency, result.current, result.hypothetical)
        if (portfolios.size < 2) return
        section("Portfolio comparison")
        val rows = mutableListOf(
            listOf("Expected return") + portfolios.map { it.stats.expectedReturn.percent() },
            listOf("Volatility") + portfolios.map { it.stats.volatility.percent() },
            listOf("Sharpe ratio") + portfolios.map { "%.4f".format(it.stats.sharpeRatio) },
        )
        if (result.marketProxy != null) {
            rows += listOf("Market beta") + portfolios.map { "%.4f".format(it.marketBeta) }
            rows += listOf("Bloomberg adj. beta") + portfolios.map { "%.4f".format(it.adjustedBeta) }
        }
        rows += listOf("Sharpe gap vs tangency") + portfolios.map { "%.4f".format(result.tangency.stats.sharpeRatio - it.stats.sharpeRatio) }
        table(listOf("") + portfolios.map { it.name }, rows)
    }

    private fun betas() {
        section("Assets and betas")
        val proxy = result.marketProxy
        val headers = listOf("Asset", "E[r]", "σ", "β tangency") + if (proxy != null) listOf("β $proxy", "Adj. β") else emptyList()
        val rows = result.tickers.indices.map { index ->
            listOf(
                shortTicker(result.tickers[index]),
                result.expectedReturns[index].percent(1),
                result.volatilities[index].percent(1),
                "%.3f".format(result.betasToTangency[index]),
            ) + if (result.marketBetas != null && result.adjustedBetas != null) {
                listOf("%.3f".format(result.marketBetas[index]), "%.3f".format(result.adjustedBetas[index]))
            } else {
                emptyList()
            }
        }
        table(headers, rows)
        if (proxy != null) paragraph("Adjusted β = 0.66 × β + 0.34 (Bloomberg shrinkage toward 1).")
    }

    private fun capitalMarketLine() {
        section("Capital Market Line")
        val tangency = result.tangency.stats
        paragraph("E[r] = ${result.riskFreeRate.percent()} + ${"%.4f".format(tangency.sharpeRatio)} × σ", size = 12f, color = TEXT, bold = true)
        paragraph("How to split between the risk-free asset and the tangency portfolio for each risk aversion A. A tangency share above 100% means borrowing at the risk-free rate.")
        val aversions = (listOf(1.0, 2.0, 5.0) + listOfNotNull(riskAversion)).distinct().sorted()
        table(
            headers = listOf("A", "Profile", "Tangency", "Risk-free", "E[r]", "σ"),
            rows = aversions.map { aversion ->
                val allocation = PortfolioMath.cmlAllocation(aversion, result.riskFreeRate, tangency)
                listOf(
                    "%.1f".format(aversion) + if (aversion == riskAversion) " ★" else "",
                    when {
                        aversion >= 5 -> "Conservative"
                        aversion >= 2 -> "Moderate"
                        else -> "Aggressive"
                    },
                    allocation.tangencyWeight.percent(1),
                    allocation.riskFreeWeight.percent(1),
                    allocation.expectedReturn.percent(),
                    allocation.volatility.percent(),
                )
            },
        )
    }

    private fun capitalAllocation() {
        val capital = capital ?: return
        val tangencyWeight = riskAversion?.let { PortfolioMath.cmlAllocation(it, result.riskFreeRate, result.tangency.stats).tangencyWeight } ?: 1.0
        val allocation = PortfolioMath.capitalAllocation(capital, tangencyWeight, result.tangency.weights, result.lastPrices)
        section("Capital allocation · ${money(capital)}")
        statTiles(
            listOf(
                "In tangency (${tangencyWeight.percent(1)})" to money(capital * tangencyWeight),
                "Risk-free" to money(allocation.riskFreeAmount),
                "Leftover from rounding" to money(allocation.leftover),
            ),
        )
        table(
            headers = listOf("Asset", "Target", "Price", "Shares", "Cost"),
            rows = result.tickers.indices.filter { abs(allocation.targets[it]) > 0.005 }.map { index ->
                listOf(
                    shortTicker(result.tickers[index]),
                    money(allocation.targets[index]),
                    money(result.lastPrices[index]),
                    "%.0f".format(allocation.shares[index]),
                    money(allocation.costs[index]),
                )
            },
        )
        paragraph("Whole shares, rounded down, at the last close in the window." + if (riskAversion == null) " Set A in the app to split with the risk-free asset." else "")
    }

    private fun correlation() {
        section("Correlation of returns")
        val labelSpace = 52f
        val cell = min(40f, (CONTENT_WIDTH - labelSpace) / result.tickers.size)
        drawing(labelSpace + cell * result.tickers.size) { drawCorrelation(result, measurer, chartPalette, labelSpace, cell) }
        cursor += 6f
        paragraph("Pearson correlation of ${result.frequency.label.lowercase()} returns. Red is positive, blue is negative.")
    }

    private fun assumptions() {
        section("Assumptions")
        paragraph(
            "Mean-variance assumes Gaussian, i.i.d. returns and that past returns persist. Thin trading distorts covariances, " +
                "CAPM misses size/value/momentum effects, and the CML assumes you borrow at the risk-free rate. Match the " +
                "risk-free maturity to your horizon. This is analysis, not investment advice.",
        )
    }

    private fun glossary() {
        section("What these numbers mean")
        listOf("Parameters" to Glossary.parameters, "Metrics" to Glossary.metrics).forEach { (title, entries) ->
            paragraph(title, size = 11f, color = ACCENT, bold = true)
            entries.forEach { entry ->
                val term = staticLayout(entry.term, textPaint(9.5f, TEXT, bold = true), CONTENT_WIDTH)
                val explanation = staticLayout(entry.explanation, textPaint(9f, MUTED), CONTENT_WIDTH)
                // Keep each term with its explanation on the same page.
                ensure((term.height + explanation.height + 10).toFloat())
                drawLayout(term, MARGIN, cursor)
                cursor += term.height + 2f
                drawLayout(explanation, MARGIN, cursor)
                cursor += explanation.height + 8f
            }
            cursor += 4f
        }
    }
}
