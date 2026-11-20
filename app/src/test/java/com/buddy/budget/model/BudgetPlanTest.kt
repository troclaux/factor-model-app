package com.buddy.budget.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Reference numbers come from running budget.py's build_report and save_to_history on the same month. */
class BudgetPlanTest {

    private val expenses = listOf(
        ExpenseItem("Aluguel", 1200.0, 5, "housing"),
        ExpenseItem("Mercado", 800.0, null, "food", isFatura = true),
        ExpenseItem("Reembolso", 50.0, null, "other", isFatura = true, inflow = true),
        ExpenseItem("Academia", 100.0, null, "health", isFatura = true),
        ExpenseItem("Bonus 1", 200.0, null, "other", inflow = true),
        ExpenseItem("Energia", 120.0, 15, "utilities"),
    )
    private val debts = listOf(DebtTaxItem("Emprestimo", 500.0, 1), DebtTaxItem("Parcela", 150.0, null, isFatura = true))
    private val taxes = listOf(DebtTaxItem("DARF", 300.0, 20))
    private val itemized = BudgetMonth(5000.0, 300.0, 100.0, null, expenses, debts, taxes, faturaDay = 9)
    private val lump = itemized.copy(faturaLump = 1000.0)
    private val today = LocalDate.of(2026, 11, 20)

    @Test
    fun itemizedReportMatchesCli() {
        val report = buildBudgetReport(itemized)
        assertEquals(2480.0, report.balance, 1e-9)
        assertEquals(3170.0, report.totalOutflow, 1e-9)
        assertEquals(350.0, report.totalInflow, 1e-9) // fatura inflow 50 + proventos 100 + bonus 200
        assertEquals(900.0, report.totalFatura, 1e-9) // 800 + 100 + 150 debt − 50 − 100 proventos
        assertEquals(1120.0, report.totalNonFatura, 1e-9) // 1320 out − 200 in
        assertEquals(
            listOf("housing" to 1200.0, "food" to 800.0, "debt" to 650.0, "taxes" to 300.0, "utilities" to 120.0, "health" to 100.0),
            report.byCategory,
        )
        assertEquals(
            listOf("Emprestimo" to 1, "Aluguel" to 5, "Fatura" to 9, "Energia" to 15, "DARF" to 20, "Bonus 1" to null),
            report.timeline.map { it.name to it.day },
        )
    }

    @Test
    fun lumpSumSkipsFaturaItemsLikeCli() {
        val report = buildBudgetReport(lump)
        assertEquals(2330.0, report.balance, 1e-9)
        assertEquals(3270.0, report.totalOutflow, 1e-9)
        assertTrue(report.faturaItems.isEmpty())
    }

    @Test
    fun historyEntryMatchesCli() {
        val entry = BudgetHistory.entryFor(buildBudgetReport(itemized), today)
        assertEquals("2026-11", entry.month)
        assertEquals(3170.0, entry.total, 1e-9)
        assertEquals(250.0, entry.inflow, 1e-9) // expense inflows only; proventos are stored separately
        assertEquals(900.0, entry.fatura, 1e-9)
        assertEquals(1320.0, entry.nonFatura, 1e-9)
        assertEquals(650.0, entry.debt, 1e-9)
        assertEquals(300.0, entry.taxes, 1e-9)
        assertEquals(2480.0, entry.balance!!, 1e-9)
        assertEquals(mapOf("housing" to 1200.0, "food" to 800.0, "health" to 100.0, "utilities" to 120.0), entry.categories)

        val lumpEntry = BudgetHistory.entryFor(buildBudgetReport(lump), today)
        assertEquals(3270.0, lumpEntry.total, 1e-9)
        assertEquals(200.0, lumpEntry.inflow, 1e-9)
        assertEquals(1000.0, lumpEntry.fatura, 1e-9)
        assertEquals(mapOf("housing" to 1200.0, "utilities" to 120.0), lumpEntry.categories)
    }

    @Test
    fun upsertReplacesTheSameMonth() {
        val first = HistoryEntry(month = "2026-11", total = 1.0)
        val (history, replaced) = BudgetHistory.upsert(listOf(HistoryEntry(month = "2026-10"), first), HistoryEntry(month = "2026-11", total = 2.0))
        assertTrue(replaced)
        assertEquals(listOf("2026-10" to 0.0, "2026-11" to 2.0), history.map { it.monthKey to it.total })
        assertFalse(BudgetHistory.upsert(history, HistoryEntry(month = "2026-12")).second)
    }

    @Test
    fun rememberedValuesComeOnlyFromTheMonthBefore() {
        val october = HistoryEntry(month = "2026-10", salary = 5000.0, leftover = 100.0, total = 3000.0, faturaLump = 1500.0)
        val previous = BudgetHistory.previousEntry(listOf(october, HistoryEntry(month = "2026-11")), today)
        assertEquals("2026-10", previous!!.monthKey)
        // No stored balance (older entries): salary + leftover − total.
        assertEquals(2100.0, BudgetHistory.defaultLeftover(previous, today), 1e-9)
        assertEquals(1500.0, BudgetHistory.defaultFaturaLump(previous), 1e-9)
        // An older month does not carry its leftover over.
        assertEquals(0.0, BudgetHistory.defaultLeftover(october.copy(month = "2026-08"), today), 1e-9)
        assertNull(BudgetHistory.previousEntry(listOf(HistoryEntry(month = "2026-11")), today))
    }

    @Test
    fun olderEntriesUseTheirDateAsMonth() {
        val legacy = HistoryEntry(date = "2026-03-25", salary = 10.0, leftover = 1.0, total = 4.0)
        assertEquals("2026-03", legacy.monthKey)
        assertEquals(7.0, legacy.effectiveBalance, 1e-9)
    }

    @Test
    fun changesVsLastMonth() {
        val previous = HistoryEntry(
            month = "2026-10",
            expenses = listOf(HistoryItem("Aluguel", 1000.0), HistoryItem("Mercado", 800.0), HistoryItem("Energia", 0.0)),
            debts = listOf(HistoryItem("Emprestimo", 500.0)),
        )
        val changes = BudgetHistory.changesVsLast(previous, itemized)
        assertEquals(listOf("Aluguel", "Energia"), changes.map { it.name })
        assertEquals("+20.0%", changes[0].percent)
        assertEquals("new", changes[1].percent)
    }

    @Test
    fun defaultChangesCoverSalaryProventosAndConfigItemsOnly() {
        val config = BudgetConfig(
            income = 4800.0,
            proventos = Proventos(100.0, 9),
            expenses = listOf(ExpenseItem("Aluguel", 1200.0, 5, "housing"), ExpenseItem("Energia", 100.0, 26, "utilities")),
            debts = listOf(DebtTaxItem("Emprestimo", 500.0, 1)),
            taxes = listOf(DebtTaxItem("DARF", 250.0, 20)),
        )
        val changes = BudgetHistory.findDefaultChanges(config, itemized)
        assertEquals(
            listOf("Net salary: 4800 → 5000", "Energia: amount 100 → 120, day 26 → 15", "DARF: amount 250 → 300"),
            changes.map { it.label },
        )
        val updated = changes.fold(config) { current, change -> change.apply(current) }
        assertEquals(5000.0, updated.income, 1e-9)
        assertEquals(ExpenseItem("Energia", 120.0, 15, "utilities"), updated.expenses[1])
        assertEquals(300.0, updated.taxes[0].amount, 1e-9)
        assertEquals(config.debts, updated.debts)
    }

    @Test
    fun averagesLikeTheCliHistoryReport() {
        val averages = BudgetHistory.averages(
            listOf(
                HistoryEntry(salary = 5000.0, total = 3000.0, categories = mapOf("food" to 800.0)),
                HistoryEntry(salary = 6000.0, total = 4000.0, categories = mapOf("food" to 600.0, "housing" to 1000.0)),
            ),
        )
        assertEquals(5500.0, averages.salary, 1e-9)
        assertEquals(3500.0, averages.expenses, 1e-9)
        assertEquals(2000.0, averages.balance, 1e-9)
        assertEquals(listOf("food" to 700.0, "housing" to 500.0), averages.categories)
    }
}
