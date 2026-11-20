package com.buddy.budget.model

import kotlin.math.abs

/** Plain-text reports laid out like budget.py's output, for copying and sharing. */
object BudgetText {

    private const val WIDTH = 44
    private val rule = "─".repeat(WIDTH)
    private val doubleRule = "═".repeat(WIDTH)

    fun report(report: BudgetReport, changes: List<AmountChange>, previousMonth: String?): String = buildString {
        val month = report.month
        appendLine(doubleRule)
        appendLine("  MONTHLY BUDGET REPORT")
        appendLine(doubleRule)
        appendLine("  ${"Net salary".padEnd(16)} R$ ${money(month.salary)}")
        if (month.leftover != 0.0) appendLine("  ${"Leftover".padEnd(14)} + R$ ${money(month.leftover)}")

        section("PAYMENT TIMELINE")
        report.timeline.forEach { entry ->
            appendLine("  ${dayLabel(entry.day).padEnd(5)} ${trunc(entry.name, 17).padEnd(17)} ${if (entry.inflow) "+" else "-"}  ${money(entry.amount)}")
        }

        section("FATURA (d${month.faturaDay})")
        if (month.faturaLump != null) appendLine(line("(lump sum)", "", "-", month.faturaLump))
        report.faturaItems.filter { it.amount > 0 }.forEach { appendLine(line(it.name, short(it.category), if (it.inflow) "+" else "-", it.amount)) }
        report.faturaDebts.filter { it.amount > 0 }.forEach { appendLine(line(it.name, "Dbt", "-", it.amount)) }
        report.faturaTaxes.filter { it.amount > 0 }.forEach { appendLine(line(it.name, "Tax", "-", it.amount)) }
        if (month.proventos > 0) appendLine(line("Proventos", "", "+", month.proventos))
        appendLine("  $rule")
        appendLine("  ${"Total".padEnd(29)} ${money(report.totalFatura)}")

        section("NON-FATURA")
        report.nonFaturaItems.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.forEach {
            appendLine("  ${trunc(it.name, 14).padEnd(14)} ${dayLabel(it.day).padEnd(5)} ${short(it.category).padEnd(4)} ${if (it.inflow) "+" else "-"}  ${money(it.amount)}")
        }
        appendLine("  $rule")
        appendLine("  ${"Total (net)".padEnd(29)} ${money(report.totalNonFatura)}")

        paymentSection("DEBTS", month.debts)
        paymentSection("TAXES", month.taxes)

        section("BY CATEGORY (outflows)")
        report.byCategory.forEach { (category, total) -> appendLine("  ${Category.label(category).padEnd(25)} ${money(total)}") }
        appendLine("  $rule")
        appendLine("  ${"Total outflow".padEnd(25)} ${money(report.totalOutflow)}")
        if (report.totalInflow != 0.0) appendLine("  ${"Total inflow".padEnd(25)} ${money(report.totalInflow)}")

        if (changes.isNotEmpty() && previousMonth != null) {
            section("CHANGES VS $previousMonth")
            changes.forEach { appendLine("  ${trunc(it.name, 15).padEnd(15)} ${money(it.old)} → ${money(it.new)} ${it.percent.padStart(7)}") }
        }

        appendLine()
        appendLine(doubleRule)
        appendLine("  SUMMARY")
        appendLine(doubleRule)
        appendLine("  ${"Net salary".padEnd(16)} R$ ${money(month.salary)}")
        if (month.leftover != 0.0) appendLine("  ${"Leftover".padEnd(14)} + R$ ${money(month.leftover)}")
        if (report.totalInflow != 0.0) appendLine("  ${"Inflows".padEnd(14)} + R$ ${money(report.totalInflow)}")
        appendLine("  ${"Outflows".padEnd(14)} - R$ ${money(report.totalOutflow)}")
        appendLine("  ${"─".repeat(WIDTH - 4)}")
        val free = report.balance >= 0
        appendLine("  ${(if (free) "FREE TO SPEND" else "DEFICIT").padEnd(14)} ${if (free) "+" else "-"} R$ ${money(abs(report.balance))}")
        appendLine(doubleRule)
    }

    /** Port of budget.py's --history report; [entries] are already limited to the months to show. */
    fun history(entries: List<HistoryEntry>): String = buildString {
        if (entries.isEmpty()) {
            appendLine("No history recorded yet. Save a month first.")
            return@buildString
        }
        val categories = entries.flatMap { it.categories.keys }.toSortedSet()
        appendLine(doubleRule)
        appendLine("  BUDGET HISTORY  (${entries.size} month(s))")
        appendLine(doubleRule)
        entries.forEach { entry ->
            appendLine()
            appendLine("  ${entry.monthKey}  |  Salary: R$ ${money(entry.salary)}  |  Balance: R$ ${money(entry.effectiveBalance)}")
            appendLine("  $rule")
            appendLine("  ${"Category".padEnd(18)} ${"Amount".padStart(12)} ${"% of expenses".padStart(13)}")
            appendLine("  $rule")
            categories.forEach { category ->
                val amount = entry.categories[category] ?: 0.0
                if (amount > 0) {
                    val share = if (entry.total != 0.0) amount / entry.total * 100 else 0.0
                    appendLine("  ${Category.label(category).padEnd(18)} R$ ${money(amount)} ${"%.1f%%".format(share).padStart(12)}")
                }
            }
            appendLine("  $rule")
            appendLine("  ${"Total expenses".padEnd(18)} R$ ${money(entry.total)}")
            appendLine("  ${"  Fatura".padEnd(18)} R$ ${money(entry.fatura)}")
            appendLine("  ${"  Non-fatura".padEnd(18)} R$ ${money(entry.nonFatura)}")
        }
        if (entries.size > 1) {
            val averages = BudgetHistory.averages(entries)
            appendLine()
            appendLine("  $rule")
            appendLine("  AVERAGES  (over ${entries.size} months)")
            appendLine("  $rule")
            appendLine("  ${"Salary".padEnd(18)} R$ ${money(averages.salary)}")
            appendLine("  ${"Expenses".padEnd(18)} R$ ${money(averages.expenses)}")
            appendLine("  ${"Balance".padEnd(18)} R$ ${money(averages.balance)}")
            appendLine("  $rule")
            averages.categories.forEach { (category, average) -> appendLine("  ${Category.label(category).padEnd(18)} R$ ${money(average)}") }
        }
    }

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("  $title")
        appendLine("  $rule")
    }

    private fun StringBuilder.paymentSection(title: String, items: List<DebtTaxItem>) {
        if (items.none { it.amount > 0 }) return
        section(title)
        items.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.forEach {
            appendLine("  ${trunc(it.name, 16).padEnd(16)} ${dayLabel(it.day).padEnd(5)} ${(if (it.isFatura) "fat" else "   ")}  ${money(it.amount)}")
        }
        appendLine("  $rule")
        appendLine("  ${"Total".padEnd(29)} ${money(items.sumOf { it.amount })}")
    }

    private fun line(name: String, tag: String, sign: String, amount: Double) =
        "  ${trunc(name, 16).padEnd(16)} ${tag.padEnd(4)}  $sign  ${money(amount)}"

    fun dayLabel(day: Int?) = day?.let { "d$it" } ?: "---"

    private fun short(category: String) = Category.label(category).take(4)

    private fun trunc(text: String, length: Int) = if (text.length <= length) text else text.take(length - 1) + "…"

    private fun money(value: Double) = "%,.2f".format(value).padStart(10)
}
