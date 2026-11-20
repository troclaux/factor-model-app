package com.buddy.budget.portfolio.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportTest {

    private val prices = PortfolioMathTest.PRICES
    private val dates = List(prices.size) { LocalDate.of(2023, 1, 31).plusMonths(it.toLong()) }

    private fun analyze(allowShort: Boolean) = Analysis.run(
        dates = dates,
        prices = prices,
        tickers = listOf("AAA", "BBB", "CCC", "DDD"),
        marketProxy = "MKT",
        riskFreeRate = 0.03,
        riskFreeSource = "entered",
        allowShort = allowShort,
        frequency = Frequency.MONTHLY,
        current = PortfolioInput(InputMode.WEIGHTS, mapOf("AAA" to "50", "BBB" to "50")),
        hypothetical = PortfolioInput(InputMode.SHARES, mapOf("CCC" to "10", "DDD" to "5")),
    )

    @Test
    fun reportCoversEverySection() {
        val result = analyze(allowShort = true)
        assertEquals(true, result.tangencyVerified)
        val text = Report.text(result, riskAversion = 3.0, capital = 10_000.0)
        listOf(
            "Status: PASS", "Pearson Correlation Matrix", "Betas", "E[r] = 3.00%", "CML Allocations",
            "Capital Allocation", "My portfolio", "Hypothetical", "Risk-free rate: 3.00% (entered)",
        ).forEach { assertTrue("missing $it", text.contains(it)) }
    }

    @Test
    fun verificationIsSkippedWithoutShorts() {
        val result = analyze(allowShort = false)
        assertEquals(null, result.tangencyVerified)
        assertTrue(!Report.text(result, null, null).contains("Verification"))
    }
}
