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
import com.buddy.budget.i18n.tr

/** The month's budget as a styled PDF: summary, timeline, breakdowns, categories (with a pie) and history. */
class BudgetPdf(
    context: Context,
    private val report: BudgetReport,
    private val monthKey: String,
    private val changes: List<AmountChange>,
    private val previousMonth: String?,
    private val history: List<HistoryEntry>,
) : PdfWriter(context, tr("Buddy · Monthly budget")) {

    private val month = report.month

    fun writeTo(file: File) {
        startPage()
        header(tr("Monthly budget · %s").format(monthKey), tr("Fatura debits on day %d  ·  generated %s").format(month.faturaDay, LocalDate.now()))
        summary()
        timeline()
        fatura()
        nonFatura()
        payments(tr("Debts"), month.debts)
        payments(tr("Taxes"), month.taxes)
        categories()
        changesVsLast()
        historySection()
        finish(file)
    }

    private fun summary() {
        section(tr("Summary"))
        val free = report.balance >= 0
        statTiles(
            listOf(
                tr("Net salary") to money(month.salary),
                tr("Leftover from last month") to money(month.leftover),
                tr("Inflows") to "+ ${money(report.totalInflow)}",
                tr("Outflows") to "− ${money(report.totalOutflow)}",
                tr("Fatura") to money(report.totalFatura),
                (if (free) tr("Free to spend") to money(report.balance) else tr("Deficit") to "− ${money(abs(report.balance))}"),
            ),
        )
        paragraph(
            tr("Free to spend = net salary + leftover + inflows − outflows. Inflows include proventos (%s) and bonuses; the fatura is ").format(money(month.proventos)) +
                (month.faturaLump?.let { tr("a lump sum of %s.").format(money(it)) } ?: tr("itemized below.")),
        )
    }

    private fun timeline() {
        section(tr("Payment timeline"))
        table(
            headers = listOf(tr("Item"), tr("Day"), tr("Flow"), tr("Amount")),
            rows = report.timeline.map { entry ->
                listOf(entry.name, BudgetText.dayLabel(entry.day), tr(if (entry.inflow) "in" else "out"), signed(entry.amount, entry.inflow))
            },
        )
    }

    private fun fatura() {
        section(tr("Fatura · day %d").format(month.faturaDay))
        val rows = mutableListOf<List<String>>()
        month.faturaLump?.let { rows += listOf(tr("Lump sum"), "", tr("out"), signed(it, inflow = false)) }
        report.faturaItems.filter { it.amount > 0 }.forEach { rows += listOf(it.name, Category.label(it.category), tr(if (it.inflow) "in" else "out"), signed(it.amount, it.inflow)) }
        report.faturaDebts.filter { it.amount > 0 }.forEach { rows += listOf(it.name, tr("Debt"), tr("out"), signed(it.amount, inflow = false)) }
        report.faturaTaxes.filter { it.amount > 0 }.forEach { rows += listOf(it.name, tr("Taxes"), tr("out"), signed(it.amount, inflow = false)) }
        rows += listOf(tr("Total"), "", "", money(report.totalFatura))
        table(listOf(tr("Item"), tr("Category"), tr("Flow"), tr("Amount")), rows)
    }

    private fun nonFatura() {
        section(tr("Non-fatura"))
        val rows = report.nonFaturaItems.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.map {
            listOf(it.name, BudgetText.dayLabel(it.day), Category.label(it.category), signed(it.amount, it.inflow))
        } + listOf(listOf(tr("Total (net)"), "", "", money(report.totalNonFatura)))
        table(listOf(tr("Item"), tr("Day"), tr("Category"), tr("Amount")), rows)
    }

    private fun payments(title: String, items: List<DebtTaxItem>) {
        if (items.none { it.amount > 0 }) return
        section(title)
        val rows = items.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.map {
            listOf(it.name, BudgetText.dayLabel(it.day), tr(if (it.isFatura) "yes" else "no"), money(it.amount))
        } + listOf(listOf(tr("Total"), "", "", money(items.sumOf { it.amount })))
        table(listOf(tr("Source"), tr("Day"), tr("On fatura"), tr("Amount")), rows)
    }

    private fun categories() {
        section(tr("Outflows by category"))
        pie(report.byCategory.map { (category, amount) -> Category.label(category) to amount })
        val total = report.byCategory.sumOf { it.second }
        table(
            headers = listOf(tr("Category"), tr("Amount"), tr("% of outflows")),
            rows = report.byCategory.map { (category, amount) ->
                listOf(Category.label(category), money(amount), "%.1f%%".format(if (total != 0.0) amount / total * 100 else 0.0))
            },
        )
    }

    private fun changesVsLast() {
        if (changes.isEmpty() || previousMonth == null) return
        section(tr("Changes vs %s").format(previousMonth))
        table(listOf(tr("Item"), tr("Before"), tr("Now"), tr("Change")), changes.map { listOf(it.name, money(it.old), money(it.new), it.percent) })
    }

    private fun historySection() {
        if (history.isEmpty()) return
        section(tr("History"))
        table(
            headers = listOf(tr("Month"), tr("Salary"), tr("Expenses"), tr("Fatura"), tr("Non-fatura"), tr("Balance")),
            rows = history.map { listOf(it.monthKey, money(it.salary), money(it.total), money(it.fatura), money(it.nonFatura), money(it.effectiveBalance)) },
        )
        if (history.size > 1) {
            val averages = BudgetHistory.averages(history)
            paragraph(tr("Averages over %d months").format(history.size), size = 11f, color = TEXT, bold = true)
            table(
                headers = null,
                rows = listOf(
                    listOf(tr("Salary"), money(averages.salary)),
                    listOf(tr("Expenses"), money(averages.expenses)),
                    listOf(tr("Balance (salary − expenses)"), money(averages.balance)),
                ) + averages.categories.map { (category, average) -> listOf(Category.label(category), money(average)) },
            )
        }
    }

    private fun signed(amount: Double, inflow: Boolean) = (if (inflow) "+ " else "− ") + money(amount)
}
