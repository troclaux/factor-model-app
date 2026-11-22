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
import com.buddy.budget.i18n.tr

/** The analysis as a styled A4 PDF: header, summary tiles, charts and tables, paginated. Units are points. */
class PdfReport(
    context: Context,
    private val result: AnalysisResult,
    /** The settings the analysis ran with. */
    private val settings: PortfolioSettings,
    private val riskAversion: Double?,
    private val capital: Double?,
) : PdfWriter(context, tr("Buddy · Portfolio analysis")) {
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
            tr("Portfolio analysis"),
            "${result.startDate} → ${result.endDate}  ·  " + tr("%d %s returns").format(result.observations, result.frequency.label.lowercase()) + "  ·  " +
                tr("generated %s").format(LocalDate.now()),
        )
    }

    private fun inputs() {
        section(tr("Inputs used"))
        fun describe(input: PortfolioInput): String {
            val filled = input.values.values.count { (parseNumber(it) ?: 0.0) != 0.0 }
            return if (filled == 0) tr("not set") else tr("%d positions, entered as %s").format(filled, input.mode.label.lowercase().removeSuffix(" %"))
        }
        table(
            headers = null,
            rows = listOf(
                listOf(tr("Risk-free rate"), "${result.riskFreeRate.percent()} (${result.riskFreeSource})"),
                listOf(
                    tr("Period"),
                    when (settings.periodMode) {
                        PeriodMode.LOOKBACK -> tr("%s %s back from the end of the previous month").format(settings.lookbackAmount.trim(), settings.lookbackUnit.label.lowercase())
                        PeriodMode.DATES -> tr("%s to %s").format(settings.startDate, if (settings.endLatest) tr("the latest available close") else settings.endDate)
                    },
                ),
                listOf(tr("Price window"), "${result.priceDates.first()} → ${result.endDate} " + tr("(first and last prices used)")),
                listOf(tr("Return frequency"), tr("%s (%d periods per year)").format(result.frequency.label, result.frequency.periodsPerYear)),
                listOf(tr("Observations"), tr("%d returns").format(result.observations)),
                listOf(tr("Short selling"), tr(if (result.allowShort) "allowed" else "forbidden (all weights ≥ 0)")),
                listOf(tr("Market benchmark"), result.marketProxy ?: tr("none")),
                listOf(tr("Risk aversion A"), riskAversion?.let { tr("%s (A = 1, 2 and 5 also shown)").format("%.1f".format(it)) } ?: tr("not set (A = 1, 2 and 5 shown)")),
                listOf(tr("Capital"), capital?.let { money(it) } ?: tr("not set")),
                listOf(tr("My portfolio"), describe(settings.current)),
                listOf(tr("Hypothetical portfolio"), describe(settings.hypothetical)),
                listOf(tr("Prices"), tr("Yahoo Finance adjusted closes")),
            ),
        )
        paragraph(tr("Tickers (%d): ").format(result.tickers.size) + result.tickers.joinToString(", "), color = TEXT)
    }

    private fun summary() {
        section(tr("Tangency portfolio"))
        val tangency = result.tangency
        val tiles = mutableListOf(
            tr("Expected return") to tangency.stats.expectedReturn.percent(),
            tr("Volatility") to tangency.stats.volatility.percent(),
            tr("Sharpe ratio") to "%.4f".format(tangency.stats.sharpeRatio),
        )
        if (tangency.marketBeta != null && tangency.adjustedBeta != null) {
            tiles += tr("Market beta (%s)").format(result.marketProxy) to "%.4f".format(tangency.marketBeta)
            tiles += tr("Bloomberg adj. beta") to "%.4f".format(tangency.adjustedBeta)
        }
        tiles += tr("Risk-free rate") to result.riskFreeRate.percent()
        statTiles(tiles)

        val verified = result.tangencyVerified
        when {
            verified == null -> paragraph(tr("Verification skipped: it only applies when short selling is allowed."))
            verified -> paragraph(tr("✓ Verification passed: (μᵢ − rf) / Cov(rᵢ, r_T) is the same for every asset."), color = ACCENT)
            else -> paragraph(tr("✗ Verification failed: (μᵢ − rf) / Cov(rᵢ, r_T) differs across assets."), color = ERROR)
        }
    }

    private fun warnings() {
        if (result.warnings.isEmpty()) return
        val paint = textPaint(9f, WARNING_TEXT)
        val layouts = result.warnings.map { staticLayout("•  $it", paint, CONTENT_WIDTH - 24f) }
        val height = 30f + layouts.sumOf { it.height + 4 }
        ensure(height + 12f)
        canvas.drawRoundRect(RectF(MARGIN, cursor, MARGIN + CONTENT_WIDTH, cursor + height), 8f, 8f, fill(WARNING_BACKGROUND))
        canvas.drawText(tr("Warnings"), MARGIN + 12f, cursor + 18f, text(11f, WARNING_TEXT, bold = true))
        var top = cursor + 26f
        layouts.forEach { layout ->
            drawLayout(layout, MARGIN + 12f, top)
            top += layout.height + 4
        }
        cursor += height + 12f
    }

    private fun meanVariance() {
        section(tr("Mean-variance diagram"))
        drawing(300f) { drawMeanVariance(result, measurer, chartPalette) }
        cursor += 8f
        val legend = mutableListOf(ACCENT to tr("Frontier"), CmlColor.toArgb() to "CML", TangencyColor.toArgb() to tr("Tangency"), MUTED to tr("Assets"))
        result.current?.let { legend += CurrentColor.toArgb() to tr(it.name) }
        result.hypothetical?.let { legend += HypotheticalColor.toArgb() to tr(it.name) }
        ensure(16f)
        var x = MARGIN
        val paint = text(9f, TEXT)
        legend.forEach { (color, label) ->
            canvas.drawCircle(x + 4f, cursor + 6f, 4f, fill(color))
            canvas.drawText(label, x + 12f, cursor + 9f, paint)
            x += 22f + paint.measureText(label)
        }
        cursor += 16f
        paragraph(tr("x: annual volatility · y: annual expected return. Solid frontier is efficient; dashed is not."))
    }

    private fun weights() {
        section(tr("Weights"))
        val portfolios = listOfNotNull(result.tangency, result.current, result.hypothetical)
        val order = result.tickers.indices.sortedByDescending { result.tangency.weights[it] }
        table(
            headers = listOf(tr("Asset")) + portfolios.map { tr(it.name) },
            rows = order.map { index -> listOf(shortTicker(result.tickers[index])) + portfolios.map { it.weights[index].percent(1) } },
            highlightColumn = 1,
        )
    }

    /** Each stock's share of each portfolio's value. Short positions cannot be drawn as slices and are listed instead. */
    private fun allocationPies() {
        section(tr("Allocation by value"))
        // Colors follow the ticker list, so a stock has the same color in every pie.
        val colors = result.tickers.withIndex().associate { (index, ticker) -> shortTicker(ticker) to pieColor(index) }
        listOfNotNull(result.tangency, result.current, result.hypothetical).forEach { portfolio ->
            paragraph(tr(portfolio.name), size = 11f, color = TEXT, bold = true)
            pie(result.tickers.indices.map { shortTicker(result.tickers[it]) to portfolio.weights[it] }) { label -> colors.getValue(label) }
            val shorts = result.tickers.indices.filter { portfolio.weights[it] < -1e-9 }
            if (shorts.isNotEmpty()) {
                paragraph(
                    tr("Short positions, not shown: ") + shorts.joinToString { "${shortTicker(result.tickers[it])} ${portfolio.weights[it].percent(1)}" } +
                        tr(". Slices are shares of the long positions."),
                )
            }
        }
    }

    private fun comparison() {
        val portfolios = listOfNotNull(result.tangency, result.current, result.hypothetical)
        if (portfolios.size < 2) return
        section(tr("Portfolio comparison"))
        val rows = mutableListOf(
            listOf(tr("Expected return")) + portfolios.map { it.stats.expectedReturn.percent() },
            listOf(tr("Volatility")) + portfolios.map { it.stats.volatility.percent() },
            listOf(tr("Sharpe ratio")) + portfolios.map { "%.4f".format(it.stats.sharpeRatio) },
        )
        if (result.marketProxy != null) {
            rows += listOf(tr("Market beta")) + portfolios.map { "%.4f".format(it.marketBeta) }
            rows += listOf(tr("Bloomberg adj. beta")) + portfolios.map { "%.4f".format(it.adjustedBeta) }
        }
        rows += listOf(tr("Sharpe gap vs tangency")) + portfolios.map { "%.4f".format(result.tangency.stats.sharpeRatio - it.stats.sharpeRatio) }
        table(listOf("") + portfolios.map { tr(it.name) }, rows)
    }

    private fun betas() {
        section(tr("Assets and betas"))
        val proxy = result.marketProxy
        val headers = listOf(tr("Asset"), "E[r]", "σ", tr("β tangency")) + if (proxy != null) listOf("β $proxy", tr("Adj. β")) else emptyList()
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
        if (proxy != null) paragraph(tr("Adjusted β = 0.66 × β + 0.34 (Bloomberg shrinkage toward 1)."))
    }

    private fun capitalMarketLine() {
        section(tr("Capital Market Line"))
        val tangency = result.tangency.stats
        paragraph("E[r] = ${result.riskFreeRate.percent()} + ${"%.4f".format(tangency.sharpeRatio)} × σ", size = 12f, color = TEXT, bold = true)
        paragraph(tr("How to split between the risk-free asset and the tangency portfolio for each risk aversion A. A tangency share above 100% means borrowing at the risk-free rate."))
        val aversions = (listOf(1.0, 2.0, 5.0) + listOfNotNull(riskAversion)).distinct().sorted()
        table(
            headers = listOf("A", tr("Profile"), tr("Tangency"), tr("Risk-free"), "E[r]", "σ"),
            rows = aversions.map { aversion ->
                val allocation = PortfolioMath.cmlAllocation(aversion, result.riskFreeRate, tangency)
                listOf(
                    "%.1f".format(aversion) + if (aversion == riskAversion) " ★" else "",
                    when {
                        aversion >= 5 -> tr("Conservative")
                        aversion >= 2 -> tr("Moderate")
                        else -> tr("Aggressive")
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
        section(tr("Capital allocation · %s").format(money(capital)))
        statTiles(
            listOf(
                tr("In tangency (%s)").format(tangencyWeight.percent(1)) to money(capital * tangencyWeight),
                tr("Risk-free") to money(allocation.riskFreeAmount),
                tr("Leftover from rounding") to money(allocation.leftover),
            ),
        )
        table(
            headers = listOf(tr("Asset"), tr("Target"), tr("Price"), tr("Shares"), tr("Cost")),
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
        paragraph(tr("Whole shares, rounded down, at the last close in the window.") + if (riskAversion == null) tr(" Set A in the app to split with the risk-free asset.") else "")
    }

    private fun correlation() {
        section(tr("Correlation of returns"))
        val labelSpace = 52f
        val cell = min(40f, (CONTENT_WIDTH - labelSpace) / result.tickers.size)
        drawing(labelSpace + cell * result.tickers.size) { drawCorrelation(result, measurer, chartPalette, labelSpace, cell) }
        cursor += 6f
        paragraph(tr("Pearson correlation of %s returns. Red is positive, blue is negative.").format(result.frequency.label.lowercase()))
    }

    private fun assumptions() {
        section(tr("Assumptions"))
        paragraph(
            tr("Mean-variance assumes Gaussian, i.i.d. returns and that past returns persist. Thin trading distorts ") +
                tr("covariances, CAPM misses size/value/momentum effects, and the CML assumes you borrow at the ") +
                tr("risk-free rate. Match the risk-free maturity to your horizon. This is analysis, not investment advice."),
        )
    }

    private fun glossary() {
        section(tr("What these numbers mean"))
        listOf(tr("Parameters") to Glossary.parameters, tr("Metrics") to Glossary.metrics).forEach { (title, entries) ->
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
