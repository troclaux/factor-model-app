package com.buddy.budget.portfolio.data

import com.buddy.budget.portfolio.model.Frequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ResampleTest {

    // Trading days from Thu 2026-09-24 to Wed 2026-10-14 (no weekends), price = day index.
    private val dates = generateSequence(LocalDate.of(2026, 9, 24)) { it.plusDays(1) }
        .takeWhile { it <= LocalDate.of(2026, 10, 14) }
        .filter { it.dayOfWeek.value <= 5 }
        .toList()
    private val table = PriceTable(listOf("AAA"), dates, Array(dates.size) { doubleArrayOf(it.toDouble()) })

    private fun closes(frequency: Frequency): List<Pair<LocalDate, Double>> =
        YahooFinance.resample(table, frequency).let { resampled -> resampled.dates.zip(resampled.prices.map { it[0] }) }

    @Test
    fun weeklyKeepsTheLastCloseOfEachWeekIncludingAPartialLastWeek() {
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 25) to 1.0, // Fri
                LocalDate.of(2026, 10, 2) to 6.0, // Fri
                LocalDate.of(2026, 10, 9) to 11.0, // Fri
                LocalDate.of(2026, 10, 14) to 14.0, // Wed: the unfinished week ends on its last available close
            ),
            closes(Frequency.WEEKLY),
        )
    }

    @Test
    fun monthlyKeepsTheLastCloseOfEachMonth() {
        assertEquals(
            listOf(LocalDate.of(2026, 9, 30) to 4.0, LocalDate.of(2026, 10, 14) to 14.0),
            closes(Frequency.MONTHLY),
        )
    }

    @Test
    fun dailyIsUnchanged() {
        assertEquals(dates.size, YahooFinance.resample(table, Frequency.DAILY).dates.size)
    }
}
