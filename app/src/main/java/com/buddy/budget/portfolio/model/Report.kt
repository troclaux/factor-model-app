package com.buddy.budget.portfolio.model

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

        section("Tangency Portfolio (Maximum Sharpe Ratio)") {
            result.tickers.forEachIndexed { index, ticker ->
                appendLine("${ticker.padEnd(width)} ${result.tangency.weights[index].percent().padStart(9)}")
            }
            appendPortfolioStats(result.tangency)
            result.tangencyRatios?.let { ratios ->
                appendLine()
                appendLine("Verification (risk premium / marginal covariance):")
                result.tickers.forEachIndexed { index, ticker -> appendLine("  ${ticker.padEnd(width)} ${"%.6f".format(ratios[index])}") }
                appendLine("  Status: ${if (result.tangencyVerified == true) "PASS" else "FAIL"}")
            }
        }

        section("Pearson Correlation Matrix") {
            appendLine(" ".repeat(width) + result.tickers.joinToString(" ") { it.padStart(width) })
            result.tickers.forEachIndexed { row, ticker ->
                appendLine(ticker.padEnd(width) + result.correlation[row].joinToString(" ") { "%+.4f".format(it).padStart(width) })
            }
        }

        section("Betas") {
            val proxy = result.marketProxy
            appendLine("${"Asset".padEnd(width)} ${"Tangency".padStart(10)}" + if (proxy != null) " ${proxy.padStart(10)} ${"Adjusted".padStart(10)}" else "")
            result.tickers.forEachIndexed { index, ticker ->
                append("${ticker.padEnd(width)} ${"%.4f".format(result.betasToTangency[index]).padStart(10)}")
                if (result.marketBetas != null && result.adjustedBetas != null) {
                    append(" ${"%.4f".format(result.marketBetas[index]).padStart(10)} ${"%.4f".format(result.adjustedBetas[index]).padStart(10)}")
                }
                appendLine()
            }
            if (proxy != null) appendLine("Adjusted β = 0.66 × β + 0.34 (Bloomberg)")
        }

        val tangency = result.tangency.stats
        section("Capital Market Line (CML)") {
            appendLine("E[r] = ${result.riskFreeRate.percent()} + ${"%.4f".format(tangency.sharpeRatio)} * sigma")
        }

        section("CML Allocations") {
            appendLine("${"A".padStart(5)} ${"Tangency".padStart(10)} ${"Risk-free".padStart(10)} ${"E[r]".padStart(8)} ${"Vol".padStart(8)}")
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
            section("Capital Allocation (%,.2f)".format(capital)) {
                appendLine("In tangency: ${tangencyWeight.percent(1)} · Risk-free: ${"%,.2f".format(allocation.riskFreeAmount)}")
                appendLine("${"Asset".padEnd(width)} ${"Target".padStart(12)} ${"Price".padStart(10)} ${"Shares".padStart(8)} ${"Cost".padStart(12)}")
                result.tickers.forEachIndexed { index, ticker ->
                    appendLine(
                        "${ticker.padEnd(width)} ${"%,.2f".format(allocation.targets[index]).padStart(12)} " +
                            "${"%,.2f".format(result.lastPrices[index]).padStart(10)} ${"%.0f".format(allocation.shares[index]).padStart(8)} " +
                            "%,.2f".format(allocation.costs[index]).padStart(12)
                    )
                }
                appendLine("Leftover cash: ${"%,.2f".format(allocation.leftover)}")
            }
        }

        listOfNotNull(result.current, result.hypothetical).forEach { portfolio ->
            section(portfolio.name) {
                result.tickers.forEachIndexed { index, ticker ->
                    appendLine("${ticker.padEnd(width)} ${portfolio.weights[index].percent().padStart(9)}")
                }
                appendPortfolioStats(portfolio)
                appendLine("Sharpe gap vs tangency: ${"%.4f".format(tangency.sharpeRatio - portfolio.stats.sharpeRatio)}")
            }
        }

        section("Data") {
            appendLine("Interval:       ${result.startDate} → ${result.endDate}")
            appendLine("Frequency:      ${result.frequency.label} (${result.frequency.periodsPerYear} periods/year)")
            appendLine("Observations:   ${result.observations} returns")
            appendLine("Risk-free rate: ${result.riskFreeRate.percent()} (${result.riskFreeSource})")
            appendLine("Short selling:  ${if (result.allowShort) "allowed" else "forbidden"}")
            appendLine("Market proxy:   ${result.marketProxy ?: "none"}")
            riskAversion?.let { appendLine("Risk aversion:  ${"%.1f".format(it)}") }
        }

        if (result.warnings.isNotEmpty()) {
            appendLine("WARNINGS:")
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

        appendLine("Frequency: $label ($periods periods/year)")
        appendLine("Risk-free rate (annual): ${"%.4f".format(result.riskFreeRate)} (${result.riskFreeRate.percent()})")
        appendLine("Risk-free rate ($label): ${"%.6f".format(result.riskFreeRate / periods)} (${(result.riskFreeRate / periods).percent(4)})")

        val priceRows = result.assetPrices.size
        appendLine()
        appendLine("Closing prices (last 5 of $priceRows rows):")
        appendLine(header("Date"))
        for (index in maxOf(0, priceRows - 5) until priceRows) appendLine(row(result.priceDates[index].toString(), result.assetPrices[index], "%.4f"))

        val returnRows = result.assetReturns.size
        appendLine()
        appendLine("${result.frequency.label} returns (last 5 of $returnRows rows):")
        appendLine(header("Date"))
        // Return row i is the change into price row i + 1.
        for (index in maxOf(0, returnRows - 5) until returnRows) appendLine(row(result.priceDates[index + 1].toString(), result.assetReturns[index], "%.6f"))

        appendLine()
        appendLine("Annualized expected returns:")
        result.tickers.forEachIndexed { index, ticker ->
            val periodicMean = PortfolioMath.mean(PortfolioMath.column(result.assetReturns, index))
            appendLine("  ${ticker.padEnd(width)} $label: ${"%.6f".format(periodicMean)}  annualized: ${"%.4f".format(result.expectedReturns[index])}")
        }

        appendLine()
        appendLine("Annualized covariance matrix:")
        appendLine(header(""))
        result.tickers.forEachIndexed { index, ticker -> appendLine(row(ticker, result.covariance[index], "%.6f")) }
    }.trimEnd() + "\n"

    private fun StringBuilder.appendPortfolioStats(portfolio: PortfolioSummary) {
        appendLine()
        appendLine("Expected Return: ${portfolio.stats.expectedReturn.percent().padStart(8)}")
        appendLine("Volatility:      ${portfolio.stats.volatility.percent().padStart(8)}")
        appendLine("Sharpe Ratio:    ${"%.4f".format(portfolio.stats.sharpeRatio).padStart(8)}")
        if (portfolio.marketBeta != null && portfolio.adjustedBeta != null) {
            appendLine("Market Beta:     ${"%.4f".format(portfolio.marketBeta).padStart(8)}")
            appendLine("BBG Adj. Beta:   ${"%.4f".format(portfolio.adjustedBeta).padStart(8)}")
        }
    }
}
