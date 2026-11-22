package com.buddy.budget.ui

import android.content.Context
import com.buddy.budget.model.AmountChange
import com.buddy.budget.model.BudgetHistory
import com.buddy.budget.model.BudgetReport
import com.buddy.budget.model.BudgetText
import com.buddy.budget.model.Category
import com.buddy.budget.model.DebtTaxItem
import com.buddy.budget.model.HistoryEntry
import com.buddy.budget.pdf.PdfWriter
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

/** The month's budget as a styled PDF: summary, timeline, breakdowns, categories (with a pie) and history. */
class BudgetPdf(
    context: Context,
    private val report: BudgetReport,
    private val monthKey: String,
    private val changes: List<AmountChange>,
    private val previousMonth: String?,
    private val history: List<HistoryEntry>,
) : PdfWriter(context, "Buddy · Monthly budget") {

    private val month = report.month

    fun writeTo(file: File) {
        startPage()
        header("Monthly budget · $monthKey", "Fatura debits on day ${month.faturaDay}  ·  generated ${LocalDate.now()}")
        summary()
        timeline()
        fatura()
        nonFatura()
        payments("Debts", month.debts)
        payments("Taxes", month.taxes)
        categories()
        changesVsLast()
        historySection()
        finish(file)
    }

    private fun summary() {
        section("Summary")
        val free = report.balance >= 0
        statTiles(
            listOf(
                "Net salary" to money(month.salary),
                "Leftover from last month" to money(month.leftover),
                "Inflows" to "+ ${money(report.totalInflow)}",
                "Outflows" to "− ${money(report.totalOutflow)}",
                "Fatura" to money(report.totalFatura),
                (if (free) "Free to spend" to money(report.balance) else "Deficit" to "− ${money(abs(report.balance))}"),
            ),
        )
        paragraph(
            "Free to spend = net salary + leftover + inflows − outflows. Inflows include proventos " +
                "(${money(month.proventos)}) and bonuses; the fatura is " +
                (month.faturaLump?.let { "a lump sum of ${money(it)}." } ?: "itemized below."),
        )
    }

    private fun timeline() {
        section("Payment timeline")
        table(
            headers = listOf("Item", "Day", "Flow", "Amount"),
            rows = report.timeline.map { entry ->
                listOf(entry.name, BudgetText.dayLabel(entry.day), if (entry.inflow) "in" else "out", signed(entry.amount, entry.inflow))
            },
        )
    }

    private fun fatura() {
        section("Fatura · day ${month.faturaDay}")
        val rows = mutableListOf<List<String>>()
        month.faturaLump?.let { rows += listOf("Lump sum", "", "out", signed(it, inflow = false)) }
        report.faturaItems.filter { it.amount > 0 }.forEach { rows += listOf(it.name, Category.label(it.category), if (it.inflow) "in" else "out", signed(it.amount, it.inflow)) }
        report.faturaDebts.filter { it.amount > 0 }.forEach { rows += listOf(it.name, "Debt", "out", signed(it.amount, inflow = false)) }
        report.faturaTaxes.filter { it.amount > 0 }.forEach { rows += listOf(it.name, "Taxes", "out", signed(it.amount, inflow = false)) }
        rows += listOf("Total", "", "", money(report.totalFatura))
        table(listOf("Item", "Category", "Flow", "Amount"), rows)
    }

    private fun nonFatura() {
        section("Non-fatura")
        val rows = report.nonFaturaItems.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.map {
            listOf(it.name, BudgetText.dayLabel(it.day), Category.label(it.category), signed(it.amount, it.inflow))
        } + listOf(listOf("Total (net)", "", "", money(report.totalNonFatura)))
        table(listOf("Item", "Day", "Category", "Amount"), rows)
    }

    private fun payments(title: String, items: List<DebtTaxItem>) {
        if (items.none { it.amount > 0 }) return
        section(title)
        val rows = items.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.map {
            listOf(it.name, BudgetText.dayLabel(it.day), if (it.isFatura) "yes" else "no", money(it.amount))
        } + listOf(listOf("Total", "", "", money(items.sumOf { it.amount })))
        table(listOf("Source", "Day", "On fatura", "Amount"), rows)
    }

    private fun categories() {
        section("Outflows by category")
        pie(report.byCategory.map { (category, amount) -> Category.label(category) to amount })
        val total = report.byCategory.sumOf { it.second }
        table(
            headers = listOf("Category", "Amount", "% of outflows"),
            rows = report.byCategory.map { (category, amount) ->
                listOf(Category.label(category), money(amount), "%.1f%%".format(if (total != 0.0) amount / total * 100 else 0.0))
            },
        )
    }

    private fun changesVsLast() {
        if (changes.isEmpty() || previousMonth == null) return
        section("Changes vs $previousMonth")
        table(listOf("Item", "Before", "Now", "Change"), changes.map { listOf(it.name, money(it.old), money(it.new), it.percent) })
    }

    private fun historySection() {
        if (history.isEmpty()) return
        section("History")
        table(
            headers = listOf("Month", "Salary", "Expenses", "Fatura", "Non-fatura", "Balance"),
            rows = history.map { listOf(it.monthKey, money(it.salary), money(it.total), money(it.fatura), money(it.nonFatura), money(it.effectiveBalance)) },
        )
        if (history.size > 1) {
            val averages = BudgetHistory.averages(history)
            paragraph("Averages over ${history.size} months", size = 11f, color = TEXT, bold = true)
            table(
                headers = null,
                rows = listOf(
                    listOf("Salary", money(averages.salary)),
                    listOf("Expenses", money(averages.expenses)),
                    listOf("Balance (salary − expenses)", money(averages.balance)),
                ) + averages.categories.map { (category, average) -> listOf(Category.label(category), money(average)) },
            )
        }
    }

    private fun signed(amount: Double, inflow: Boolean) = (if (inflow) "+ " else "− ") + money(amount)
}
