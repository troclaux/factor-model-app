package com.buddy.budget.portfolio.data

import com.buddy.budget.portfolio.model.Frequency
import com.buddy.budget.portfolio.model.InputMode
import com.buddy.budget.portfolio.model.PeriodMode
import com.buddy.budget.portfolio.model.PortfolioInput
import com.buddy.budget.portfolio.model.PortfolioSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class LastAnalysisRepositoryTest {

    private val file = File.createTempFile("last_analysis", ".json").apply { deleteOnExit() }
    private val repository = LastAnalysisRepository(file)

    @Test
    fun roundTripsSettingsPricesAndRiskFreeRate() {
        val settings = PortfolioSettings(
            tickers = listOf("AAA.SA", "BBB.SA"),
            frequency = Frequency.WEEKLY,
            periodMode = PeriodMode.DATES,
            startDate = "2025-01-02",
            endLatest = true,
            current = PortfolioInput(InputMode.SHARES, mapOf("AAA.SA" to "10")),
        )
        val prices = PriceTable(
            tickers = listOf("AAA.SA", "BBB.SA", "^BVSP"),
            dates = listOf(LocalDate.of(2025, 1, 3), LocalDate.of(2025, 1, 10)),
            prices = arrayOf(doubleArrayOf(10.0, 20.0, 120000.0), doubleArrayOf(10.5, 19.5, 121000.0)),
        )
        val ranAt = LocalDateTime.of(2026, 11, 20, 14, 3, 5)
        repository.save(LastAnalysisRepository.LastAnalysis(settings, prices, 0.1415, "fetched from ^IRX", ranAt))

        val loaded = repository.load()!!
        assertEquals(settings, loaded.settings)
        assertEquals(prices.tickers, loaded.prices.tickers)
        assertEquals(prices.dates, loaded.prices.dates)
        prices.prices.indices.forEach { assertArrayEquals(prices.prices[it], loaded.prices.prices[it], 0.0) }
        assertEquals(0.1415, loaded.riskFreeRate, 0.0)
        assertEquals("fetched from ^IRX", loaded.riskFreeSource)
        assertEquals(ranAt, loaded.ranAt)
    }

    @Test
    fun missingOrCorruptFileLoadsNothing() {
        file.delete()
        assertNull(repository.load())
        file.writeText("{not json")
        assertNull(repository.load())
    }
}
