package com.buddy.budget.portfolio.model

import java.time.LocalDate

data class PortfolioSummary(
    val name: String,
    val weights: Vector,
    val stats: PortfolioStats,
    val marketBeta: Double?,
    val adjustedBeta: Double?,
)

data class AnalysisResult(
    val tickers: List<String>,
    val riskFreeRate: Double,
    val riskFreeSource: String,
    val allowShort: Boolean,
    val marketProxy: String?,
    val frequency: Frequency,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val observations: Int,
    val expectedReturns: Vector,
    val volatilities: Vector,
    val lastPrices: Vector,
    val tangency: PortfolioSummary,
    val current: PortfolioSummary?,
    val hypothetical: PortfolioSummary?,
    val betasToTangency: Vector,
    val marketBetas: Vector?,
    val adjustedBetas: Vector?,
    val frontier: Frontier,
    val correlation: Matrix,
    val warnings: List<String>,
    /** (μᵢ − rf) / Cov(rᵢ, r_T) per asset; null when shorts are forbidden (the check only holds unconstrained). */
    val tangencyRatios: Vector?,
    val tangencyVerified: Boolean?,
    /** Intermediate values for cross-checking (the CLI's --verbose). */
    val priceDates: List<LocalDate>,
    val assetPrices: Matrix,
    val assetReturns: Matrix,
    val covariance: Matrix,
)

/** Mirrors the CLI pipeline: returns → estimates → tangency → betas → custom portfolios → frontier. */
object Analysis {

    fun run(
        dates: List<LocalDate>,
        prices: Matrix,
        tickers: List<String>,
        marketProxy: String?,
        riskFreeRate: Double,
        riskFreeSource: String,
        allowShort: Boolean,
        frequency: Frequency,
        current: PortfolioInput,
        hypothetical: PortfolioInput,
    ): AnalysisResult {
        val allReturns = PortfolioMath.simpleReturns(prices)
        val assetReturns = Array(allReturns.size) { row -> DoubleArray(tickers.size) { allReturns[row][it] } }
        val (expected, cov) = PortfolioMath.estimateParameters(assetReturns, frequency.periodsPerYear)
        val lastPrices = prices.last().copyOfRange(0, tickers.size)
        val warnings = mutableListOf<String>()

        val minVarianceReturn = PortfolioMath.minVarianceReturn(expected, cov)
        if (riskFreeRate >= minVarianceReturn) {
            warnings += "Risk-free rate (${riskFreeRate.percent()}) is at or above the minimum-variance portfolio " +
                "return (${minVarianceReturn.percent()}). The tangency portfolio may not exist or may be degenerate."
        }

        val tangencyWeights = if (allowShort) {
            PortfolioMath.tangencyWeights(expected, cov, riskFreeRate)
        } else {
            PortfolioMath.tangencyWeightsLongOnly(expected, cov, riskFreeRate)
        }

        // The proxy is fetched as an extra last column unless it is already one of the tickers.
        val marketBetas = marketProxy?.let { proxy ->
            val marketIndex = if (proxy in tickers) tickers.indexOf(proxy) else tickers.size
            PortfolioMath.marketBetas(assetReturns, PortfolioMath.column(allReturns, marketIndex))
        }
        val adjustedBetas = marketBetas?.let { PortfolioMath.bloombergAdjust(it) }

        // Portfolio beta is linear in the weights: β_p = Σ wᵢβᵢ.
        fun summarize(name: String, weights: Vector) = PortfolioSummary(
            name = name,
            weights = weights,
            stats = PortfolioMath.statistics(weights, expected, cov, riskFreeRate),
            marketBeta = marketBetas?.let { PortfolioMath.dot(weights, it) },
            adjustedBeta = adjustedBetas?.let { PortfolioMath.dot(weights, it) },
        )

        fun userPortfolio(name: String, input: PortfolioInput): PortfolioSummary? {
            val values = DoubleArray(tickers.size) { parseNumber(input.values[tickers[it]] ?: "") ?: 0.0 }
            if (values.all { it == 0.0 }) return null
            val weights = when (input.mode) {
                InputMode.SHARES -> PortfolioMath.weightsFromShares(values, lastPrices)
                InputMode.WEIGHTS -> {
                    val total = values.sum()
                    if (kotlin.math.abs(total - 100.0) > 0.5) {
                        warnings += "$name weights add up to ${"%.1f".format(total)}%, not 100%."
                    }
                    DoubleArray(values.size) { values[it] / 100.0 }
                }
            }
            return summarize(name, weights)
        }

        tickers.forEachIndexed { index, ticker ->
            if (tangencyWeights[index] < -1e-9) warnings += "$ticker has a short position (${tangencyWeights[index].percent()})."
        }
        val tangencyRatios = if (allowShort) PortfolioMath.tangencyRatios(tangencyWeights, expected, cov, riskFreeRate) else null

        if (assetReturns.size < 60) {
            warnings += "Small sample (${assetReturns.size} observations). Estimates may be unreliable."
        }

        return AnalysisResult(
            tickers = tickers,
            riskFreeRate = riskFreeRate,
            riskFreeSource = riskFreeSource,
            allowShort = allowShort,
            marketProxy = marketProxy,
            frequency = frequency,
            startDate = dates[1],
            endDate = dates.last(),
            observations = assetReturns.size,
            expectedReturns = expected,
            volatilities = DoubleArray(tickers.size) { kotlin.math.sqrt(cov[it][it]) },
            lastPrices = lastPrices,
            tangency = summarize("Tangency", tangencyWeights),
            current = userPortfolio("My portfolio", current),
            hypothetical = userPortfolio("Hypothetical", hypothetical),
            betasToTangency = PortfolioMath.betasToPortfolio(cov, tangencyWeights),
            marketBetas = marketBetas,
            adjustedBetas = adjustedBetas,
            frontier = PortfolioMath.frontier(expected, cov, allowShort),
            correlation = PortfolioMath.correlationMatrix(assetReturns),
            warnings = warnings,
            tangencyRatios = tangencyRatios,
            tangencyVerified = tangencyRatios?.let { it.max() - it.min() < 1e-8 },
            priceDates = dates,
            assetPrices = Array(prices.size) { row -> prices[row].copyOfRange(0, tickers.size) },
            assetReturns = assetReturns,
            covariance = cov,
        )
    }
}

fun Double.percent(digits: Int = 2): String = "%.${digits}f%%".format(this * 100)
