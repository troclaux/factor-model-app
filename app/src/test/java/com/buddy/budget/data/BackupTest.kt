package com.buddy.budget.data

import com.buddy.budget.model.BudgetConfig
import com.buddy.budget.model.HistoryEntry
import com.buddy.budget.portfolio.model.PortfolioSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {

    @Test
    fun roundTripsWithoutThePluggySecret() {
        val backup = Backup(
            exportedAt = "2026-11-20T21:00",
            budgetConfig = BudgetConfig(income = 5000.0),
            budgetHistory = listOf(HistoryEntry(month = "2026-10", total = 3000.0)),
            portfolioSettings = PortfolioSettings(tickers = listOf("AAA.SA"), pluggyClientId = "id", pluggyClientSecret = "pluggy_sk_secret"),
        )
        val json = backup.toJson()
        assertFalse(json.contains("pluggy_sk_secret"))

        val restored = Backup.fromJson(json)
        assertEquals(backup.copy(portfolioSettings = backup.portfolioSettings!!.copy(pluggyClientSecret = "")), restored)
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("""{"app": "something else"}""") }
        assertThrows(IllegalArgumentException::class.java) { Backup.fromJson("""[1, 2, 3]""") }
    }
}
