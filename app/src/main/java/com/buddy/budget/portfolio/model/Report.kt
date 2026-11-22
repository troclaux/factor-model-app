package com.buddy.budget.portfolio.model

import com.buddy.budget.i18n.tr

/** Plain-text versions of the results, laid out like the CLI output, for copying and sharing. */
object Report {

    private val rule = "=".repeat(60)

    fun text(result: AnalysisResult, riskAversion: Double?, capital: Double?): String = buildString {
        val width = result.tickers.maxOf { it.length }.coerceAtLeast(6) + 2
        fun section(title: String, body: StringBuilder.() -> Unit) {
            appendLine(rule)
            appendLine(title)
            appendLine(rule)
            body()
            appendLine()
        }

        section(tr("Tangency Portfolio (Maximum Sharpe Ratio)")) {
            result.tickers.forEachIndexed { index, ticker ->
                appendLine("${ticker.padEnd(width)} ${result.tangency.weights[index].percent().padStart(9)}")
            }
            appendPortfolioStats(result.tangency)
            result.tangencyRatios?.let { ratios ->
                appendLine()
                appendLine(tr("Verification (risk premium / marginal covariance):"))
                result.tickers.forEachIndexed { index, ticker -> appendLine("  ${ticker.padEnd(width)} ${"%.6f".format(ratios[index])}") }
                appendLine("  " + tr("Status: %s").format(if (result.tangencyVerified == true) "PASS" else "FAIL"))
            }
        }

        section(tr("Pearson Correlation Matrix")) {
            appendLine(" ".repeat(width) + result.tickers.joinToString(" ") { it.padStart(width) })
            result.tickers.forEachIndexed { row, ticker ->
                appendLine(ticker.padEnd(width) + result.correlation[row].joinToString(" ") { "%+.4f".format(it).padStart(width) })
            }
        }

        section(tr("Betas")) {
            val proxy = result.marketProxy
            appendLine("${tr("Asset").padEnd(width)} ${tr("Tangency").padStart(10)}" + if (proxy != null) " ${proxy.padStart(10)} ${tr("Adjusted").padStart(10)}" else "")
            result.tickers.forEachIndexed { index, ticker ->
                append("${ticker.padEnd(width)} ${"%.4f".format(result.betasToTangency[index]).padStart(10)}")
                if (result.marketBetas != null && result.adjustedBetas != null) {
                    append(" ${"%.4f".format(result.marketBetas[index]).padStart(10)} ${"%.4f".format(result.adjustedBetas[index]).padStart(10)}")
                }
                appendLine()
            }
            if (proxy != null) appendLine(tr("Adjusted β = 0.66 × β + 0.34 (Bloomberg)"))
        }

        val tangency = result.tangency.stats
        section(tr("Capital Market Line (CML)")) {
            appendLine("E[r] = ${result.riskFreeRate.percent()} + ${"%.4f".format(tangency.sharpeRatio)} * sigma")
        }

        section(tr("CML Allocations")) {
            appendLine("${"A".padStart(5)} ${tr("Tangency").padStart(10)} ${tr("Risk-free").padStart(10)} ${"E[r]".padStart(8)} ${"Vol".padStart(8)}")
            (listOf(1.0, 2.0, 5.0) + listOfNotNull(riskAversion)).distinct().sorted().forEach { aversion ->
                val allocation = PortfolioMath.cmlAllocation(aversion, result.riskFreeRate, tangency)
                appendLine(
                    "${"%.1f".format(aversion).padStart(5)} ${allocation.tangencyWeight.percent(1).padStart(10)} " +
                        "${allocation.riskFreeWeight.percent(1).padStart(10)} ${allocation.expectedReturn.percent().padStart(8)} " +
                        allocation.volatility.percent().padStart(8)
                )
            }
        }

        if (capital != null) {
            val tangencyWeight = riskAversion?.let { PortfolioMath.cmlAllocation(it, result.riskFreeRate, tangency).tangencyWeight } ?: 1.0
            val allocation = PortfolioMath.capitalAllocation(capital, tangencyWeight, result.tangency.weights, result.lastPrices)
            section(tr("Capital Allocation (%s)").format("%,.2f".format(capital))) {
                appendLine(tr("In tangency: %s · Risk-free: %s").format(tangencyWeight.percent(1), "%,.2f".format(allocation.riskFreeAmount)))
                appendLine("${tr("Asset").padEnd(width)} ${tr("Target").padStart(12)} ${tr("Price").padStart(10)} ${tr("Shares").padStart(8)} ${tr("Cost").padStart(12)}")
                result.tickers.forEachIndexed { index, ticker ->
                    appendLine(
                        "${ticker.padEnd(width)} ${"%,.2f".format(allocation.targets[index]).padStart(12)} " +
                            "${"%,.2f".format(result.lastPrices[index]).padStart(10)} ${"%.0f".format(allocation.shares[index]).padStart(8)} " +
                            "%,.2f".format(allocation.costs[index]).padStart(12)
                    )
                }
                appendLine(tr("Leftover cash: %s").format("%,.2f".format(allocation.leftover)))
            }
        }

        listOfNotNull(result.current, result.hypothetical).forEach { portfolio ->
            section(tr(portfolio.name)) {
                result.tickers.forEachIndexed { index, ticker ->
                    appendLine("${ticker.padEnd(width)} ${portfolio.weights[index].percent().padStart(9)}")
                }
                appendPortfolioStats(portfolio)
                appendLine(tr("Sharpe gap vs tangency: %s").format("%.4f".format(tangency.sharpeRatio - portfolio.stats.sharpeRatio)))
            }
        }

        section(tr("Data")) {
            fun field(label: String, value: String) = appendLine("${"$label:".padEnd(16)}$value")
            field(tr("Interval"), "${result.startDate} → ${result.endDate}")
            field(tr("Frequency"), tr("%s (%d periods/year)").format(result.frequency.label, result.frequency.periodsPerYear))
            field(tr("Observations"), tr("%d returns").format(result.observations))
            field(tr("Risk-free rate"), "${result.riskFreeRate.percent()} (${result.riskFreeSource})")
            field(tr("Short selling"), tr(if (result.allowShort) "allowed" else "forbidden"))
            field(tr("Market proxy"), result.marketProxy ?: tr("none"))
            riskAversion?.let { field(tr("Risk aversion"), "%.1f".format(it)) }
        }

        if (result.warnings.isNotEmpty()) {
            appendLine(tr("WARNINGS:"))
            result.warnings.forEach { appendLine("  - $it") }
        }
    }.trimEnd() + "\n"

    /** Prices, returns, means and covariance behind the estimates (the CLI's --verbose). */
    fun intermediate(result: AnalysisResult): String = buildString {
        val width = result.tickers.maxOf { it.length }.coerceAtLeast(10) + 2
        val periods = result.frequency.periodsPerYear
        val label = result.frequency.label.lowercase()
        fun header(first: String) = first.padEnd(width) + result.tickers.joinToString(" ") { it.padStart(width) }
        fun row(first: String, values: DoubleArray, format: String) =
            first.padEnd(width) + values.joinToString(" ") { format.format(it).padStart(width) }

        appendLine(tr("Frequency: %s (%d periods/year)").format(label, periods))
        appendLine(tr("Risk-free rate (annual): %s (%s)").format("%.4f".format(result.riskFreeRate), result.riskFreeRate.percent()))
        appendLine(tr("Risk-free rate (%s): %s (%s)").format(label, "%.6f".format(result.riskFreeRate / periods), (result.riskFreeRate / periods).percent(4)))

        val priceRows = result.assetPrices.size
        appendLine()
        appendLine(tr("Closing prices (last 5 of %d rows):").format(priceRows))
        appendLine(header(tr("Date")))
        for (index in maxOf(0, priceRows - 5) until priceRows) appendLine(row(result.priceDates[index].toString(), result.assetPrices[index], "%.4f"))

        val returnRows = result.assetReturns.size
        appendLine()
        appendLine(tr("%s returns (last 5 of %d rows):").format(result.frequency.label, returnRows))
        appendLine(header(tr("Date")))
        // Return row i is the change into price row i + 1.
        for (index in maxOf(0, returnRows - 5) until returnRows) appendLine(row(result.priceDates[index + 1].toString(), result.assetReturns[index], "%.6f"))

        appendLine()
        appendLine(tr("Annualized expected returns:"))
        result.tickers.forEachIndexed { index, ticker ->
            val periodicMean = PortfolioMath.mean(PortfolioMath.column(result.assetReturns, index))
            appendLine("  ${ticker.padEnd(width)} $label: ${"%.6f".format(periodicMean)}  ${tr("annualized")}: ${"%.4f".format(result.expectedReturns[index])}")
        }

        appendLine()
        appendLine(tr("Annualized covariance matrix:"))
        appendLine(header(""))
        result.tickers.forEachIndexed { index, ticker -> appendLine(row(ticker, result.covariance[index], "%.6f")) }
    }.trimEnd() + "\n"

    private fun StringBuilder.appendPortfolioStats(portfolio: PortfolioSummary) {
        appendLine()
        fun stat(label: String, value: String) = appendLine("${"$label:".padEnd(17)}${value.padStart(8)}")
        stat(tr("Expected Return"), portfolio.stats.expectedReturn.percent())
        stat(tr("Volatility"), portfolio.stats.volatility.percent())
        stat(tr("Sharpe Ratio"), "%.4f".format(portfolio.stats.sharpeRatio))
        if (portfolio.marketBeta != null && portfolio.adjustedBeta != null) {
            stat(tr("Market Beta"), "%.4f".format(portfolio.marketBeta))
            stat(tr("BBG Adj. Beta"), "%.4f".format(portfolio.adjustedBeta))
        }
    }
}
