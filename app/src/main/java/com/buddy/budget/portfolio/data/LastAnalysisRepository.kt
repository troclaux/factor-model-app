package com.buddy.budget.portfolio.data

import com.buddy.budget.portfolio.model.PortfolioSettings
import com.google.gson.Gson
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The inputs of the last analysis (settings, prices and risk-free rate), so the results can be
 * recomputed when the app reopens without downloading anything.
 */
class LastAnalysisRepository(private val file: File) {

    data class LastAnalysis(
        val settings: PortfolioSettings,
        val prices: PriceTable,
        val riskFreeRate: Double,
        val riskFreeSource: String,
        val ranAt: LocalDateTime,
    )

    /** JSON-friendly copy: dates as ISO strings. */
    private data class Stored(
        val settings: PortfolioSettings,
        val tickers: List<String>,
        val dates: List<String>,
        val prices: List<DoubleArray>,
        val riskFreeRate: Double,
        val riskFreeSource: String,
        val ranAt: String,
    )

    private val gson = Gson()

    fun save(analysis: LastAnalysis) {
        val stored = Stored(
            settings = analysis.settings,
            tickers = analysis.prices.tickers,
            dates = analysis.prices.dates.map { it.toString() },
            prices = analysis.prices.prices.toList(),
            riskFreeRate = analysis.riskFreeRate,
            riskFreeSource = analysis.riskFreeSource,
            ranAt = analysis.ranAt.toString(),
        )
        file.writeText(gson.toJson(stored))
    }

    /** Null when nothing was saved yet or the file is from an incompatible version. */
    fun load(): LastAnalysis? = runCatching {
        val stored = gson.fromJson(file.readText(), Stored::class.java)
        LastAnalysis(
            settings = stored.settings,
            prices = PriceTable(stored.tickers, stored.dates.map(LocalDate::parse), stored.prices.toTypedArray()),
            riskFreeRate = stored.riskFreeRate,
            riskFreeSource = stored.riskFreeSource,
            ranAt = LocalDateTime.parse(stored.ranAt),
        )
    }.getOrNull()
}
