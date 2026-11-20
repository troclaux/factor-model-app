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
