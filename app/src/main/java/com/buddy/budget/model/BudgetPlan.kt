package com.buddy.budget.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** One month's plan: the config defaults with this month's edits. Mirrors a budget.py session. */
data class BudgetMonth(
    val salary: Double,
    val leftover: Double,
    val proventos: Double,
    /** One fatura total replacing the itemized fatura expenses (budget.py's fatura_lump); null means itemize. */
    val faturaLump: Double?,
    val expenses: List<ExpenseItem>,
    val debts: List<DebtTaxItem>,
    val taxes: List<DebtTaxItem>,
    val faturaDay: Int,
) {
    /** With a lump sum the individual fatura expenses are skipped, as in budget.py. */
    val activeExpenses: List<ExpenseItem> get() = if (faturaLump != null) expenses.filter { !it.isFatura } else expenses
}

/** A dated line of the payment timeline. The fatura line nets its outflows and inflows. */
data class TimelineEntry(val day: Int?, val name: String, val amount: Double, val inflow: Boolean, val isFatura: Boolean)

data class BudgetReport(
    val month: BudgetMonth,
    val faturaItems: List<ExpenseItem>,
    val nonFaturaItems: List<ExpenseItem>,
    val faturaDebts: List<DebtTaxItem>,
    val faturaTaxes: List<DebtTaxItem>,
    val totalFaturaOut: Double,
    val totalFaturaIn: Double,
    /** Net fatura bill: outflows minus inflows (proventos included). */
    val totalFatura: Double,
    /** Net non-fatura expenses (debts and taxes excluded). */
    val totalNonFatura: Double,
    val totalOutflow: Double,
    val totalInflow: Double,
    val balance: Double,
    /** Outflows by category key, largest first. */
    val byCategory: List<Pair<String, Double>>,
    val timeline: List<TimelineEntry>,
)

/** Port of budget.py's build_report totals. */
fun buildBudgetReport(month: BudgetMonth): BudgetReport {
    val expenses = month.activeExpenses
    val faturaItems = expenses.filter { it.isFatura }
    val nonFaturaItems = expenses.filter { !it.isFatura }
    val faturaDebts = month.debts.filter { it.isFatura }
    val faturaTaxes = month.taxes.filter { it.isFatura }
    val nonFaturaDebts = month.debts.filter { !it.isFatura }
    val nonFaturaTaxes = month.taxes.filter { !it.isFatura }

    val expenseFaturaOut = month.faturaLump ?: faturaItems.filter { !it.inflow }.sumOf { it.amount }
    val expenseFaturaIn = if (month.faturaLump != null) 0.0 else faturaItems.filter { it.inflow }.sumOf { it.amount }
    val totalFaturaOut = expenseFaturaOut + faturaDebts.sumOf { it.amount } + faturaTaxes.sumOf { it.amount }
    val totalFaturaIn = expenseFaturaIn + month.proventos
    val nonFaturaOut = nonFaturaItems.filter { !it.inflow }.sumOf { it.amount }
    val nonFaturaIn = nonFaturaItems.filter { it.inflow }.sumOf { it.amount }
    val totalOutflow = totalFaturaOut + nonFaturaOut + nonFaturaDebts.sumOf { it.amount } + nonFaturaTaxes.sumOf { it.amount }
    val totalInflow = totalFaturaIn + nonFaturaIn

    val byCategory = linkedMapOf<String, Double>()
    expenses.filter { !it.inflow }.forEach { byCategory[it.category] = (byCategory[it.category] ?: 0.0) + it.amount }
    if (month.debts.isNotEmpty()) byCategory["debt"] = month.debts.sumOf { it.amount }
    if (month.taxes.isNotEmpty()) byCategory["taxes"] = (byCategory["taxes"] ?: 0.0) + month.taxes.sumOf { it.amount }

    val totalFatura = totalFaturaOut - totalFaturaIn
    val dated = nonFaturaItems.map { TimelineEntry(it.day, it.name, it.amount, it.inflow, isFatura = false) } +
        (nonFaturaDebts + nonFaturaTaxes).map { TimelineEntry(it.day, it.name, it.amount, inflow = false, isFatura = false) }
    val fatura = if (totalFaturaOut != 0.0 || totalFaturaIn != 0.0) {
        listOf(TimelineEntry(month.faturaDay, "Fatura", abs(totalFatura), inflow = totalFatura < 0, isFatura = true))
    } else {
        emptyList()
    }
    // Known days in order, items without a day last.
    val timeline = (dated.filter { it.amount > 0 } + fatura).sortedBy { it.day ?: 32 }

    return BudgetReport(
        month = month,
        faturaItems = faturaItems,
        nonFaturaItems = nonFaturaItems,
        faturaDebts = faturaDebts,
        faturaTaxes = faturaTaxes,
        totalFaturaOut = totalFaturaOut,
        totalFaturaIn = totalFaturaIn,
        totalFatura = totalFatura,
        totalNonFatura = nonFaturaOut - nonFaturaIn,
        totalOutflow = totalOutflow,
        totalInflow = totalInflow,
        balance = month.salary + month.leftover + totalInflow - totalOutflow,
        byCategory = byCategory.filterValues { it > 0 }.toList().sortedByDescending { it.second },
        timeline = timeline,
    )
}

/** One saved month, in budget.py's budget_history.json format (older entries lack some fields). */
data class HistoryEntry(
    val month: String? = null,
    val date: String = "",
    val salary: Double = 0.0,
    val leftover: Double = 0.0,
    val proventos: Double = 0.0,
    @SerializedName("fatura_lump") val faturaLump: Double? = null,
    val total: Double = 0.0,
    val inflow: Double = 0.0,
    val balance: Double? = null,
    val fatura: Double = 0.0,
    @SerializedName("non_fatura") val nonFatura: Double = 0.0,
    val debt: Double = 0.0,
    val taxes: Double = 0.0,
    val categories: Map<String, Double> = emptyMap(),
    val expenses: List<HistoryItem> = emptyList(),
    val debts: List<HistoryItem> = emptyList(),
    @SerializedName("taxes_list") val taxesList: List<HistoryItem> = emptyList(),
) {
    /** yyyy-MM; older entries only have the date. */
    val monthKey: String get() = month ?: date.take(7)

    /** Older entries have no balance; budget.py falls back to salary + leftover − total. */
    val effectiveBalance: Double get() = balance ?: (salary + leftover - total)
}

data class HistoryItem(
    val name: String = "",
    val amount: Double = 0.0,
    @SerializedName("is_fatura") val isFatura: Boolean = false,
    val day: Int? = null,
    val inflow: Boolean = false,
)

data class AmountChange(val name: String, val old: Double, val new: Double) {
    /** "+12.5%", or "new" when the old amount was zero. */
    val percent: String get() = if (old != 0.0) "%+.1f%%".format((new - old) / old * 100) else "new"
}

/** A default that differs from this month's value; [apply] writes it into the config. */
data class DefaultChange(val label: String, val apply: (BudgetConfig) -> BudgetConfig)

object BudgetHistory {

    fun monthKey(date: LocalDate): String = YearMonth.from(date).toString()

    /** Port of save_to_history: the entry stored for this month. */
    fun entryFor(report: BudgetReport, today: LocalDate): HistoryEntry {
        val month = report.month
        val expenses = month.activeExpenses
        val outflows = expenses.filter { !it.inflow }
        val categories = linkedMapOf<String, Double>()
        outflows.forEach { categories[it.category] = (categories[it.category] ?: 0.0) + it.amount }
        val totalDebt = month.debts.sumOf { it.amount }
        val totalTaxes = month.taxes.sumOf { it.amount }
        return HistoryEntry(
            month = monthKey(today),
            date = today.toString(),
            salary = month.salary,
            leftover = month.leftover,
            proventos = month.proventos,
            faturaLump = month.faturaLump,
            total = outflows.sumOf { it.amount } + (month.faturaLump ?: 0.0) + totalDebt + totalTaxes,
            inflow = expenses.filter { it.inflow }.sumOf { it.amount },
            balance = Math.round(report.balance * 100) / 100.0,
            fatura = month.faturaLump ?: outflows.filter { it.isFatura }.sumOf { it.amount },
            nonFatura = outflows.filter { !it.isFatura }.sumOf { it.amount },
            debt = totalDebt,
            taxes = totalTaxes,
            categories = categories,
            expenses = expenses.map { HistoryItem(it.name, it.amount, it.isFatura, it.day, it.inflow) },
            debts = month.debts.map { HistoryItem(it.name, it.amount, it.isFatura, it.day) },
            taxesList = month.taxes.map { HistoryItem(it.name, it.amount, it.isFatura, it.day) },
        )
    }

    /** Replaces any entry for the same month. Returns the new history and whether one was replaced. */
    fun upsert(history: List<HistoryEntry>, entry: HistoryEntry): Pair<List<HistoryEntry>, Boolean> {
        val replaced = history.any { it.monthKey == entry.monthKey }
        return history.filter { it.monthKey != entry.monthKey } + entry to replaced
    }

    /** The most recent entry from a month before [today]'s. */
    fun previousEntry(history: List<HistoryEntry>, today: LocalDate): HistoryEntry? =
        history.lastOrNull { it.monthKey < monthKey(today) }

    /** Last month's balance, but only if the previous entry is from the immediately preceding month. */
    fun defaultLeftover(previous: HistoryEntry?, today: LocalDate): Double {
        val lastMonth = monthKey(today.withDayOfMonth(1).minusDays(1))
        return if (previous != null && previous.monthKey == lastMonth) Math.round(previous.effectiveBalance * 100) / 100.0 else 0.0
    }

    /** Last month's fatura lump sum, or 0 (itemize) if it had none. */
    fun defaultFaturaLump(previous: HistoryEntry?): Double = previous?.faturaLump ?: 0.0

    /** Items whose amount differs from the previous month's. */
    fun changesVsLast(previous: HistoryEntry?, month: BudgetMonth): List<AmountChange> {
        if (previous == null) return emptyList()
        val before = (previous.expenses + previous.debts + previous.taxesList).associate { it.name to it.amount }
        val current = month.activeExpenses.map { it.name to it.amount } + (month.debts + month.taxes).map { it.name to it.amount }
        return current.mapNotNull { (name, amount) ->
            val old = before[name] ?: return@mapNotNull null
            if (abs(amount - old) > 0.005) AmountChange(name, old, amount) else null
        }
    }

    /**
     * Every value this month that differs from the config: salary, proventos, and each expense, debt and
     * tax that exists in the config (amount, day, fatura, inflow). One-off items are never offered.
     */
    fun findDefaultChanges(config: BudgetConfig, month: BudgetMonth): List<DefaultChange> {
        val changes = mutableListOf<DefaultChange>()
        if (month.salary != config.income) {
            changes += DefaultChange("Net salary: ${number(config.income)} → ${number(month.salary)}") { it.copy(income = month.salary) }
        }
        if (month.proventos != config.proventos.amount) {
            changes += DefaultChange("Proventos: ${number(config.proventos.amount)} → ${number(month.proventos)}") {
                it.copy(proventos = it.proventos.copy(amount = month.proventos))
            }
        }
        month.activeExpenses.forEach { item ->
            val base = config.expenses.find { it.name == item.name } ?: return@forEach
            val diffs = describe(base.amount, item.amount, base.day, item.day, base.isFatura, item.isFatura) +
                listOfNotNull(if (base.inflow != item.inflow) "inflow ${base.inflow} → ${item.inflow}" else null)
            if (diffs.isNotEmpty()) {
                changes += DefaultChange("${item.name}: ${diffs.joinToString()}") { config ->
                    config.copy(expenses = config.expenses.map {
                        if (it.name == item.name) it.copy(amount = item.amount, day = item.day, isFatura = item.isFatura, inflow = item.inflow) else it
                    })
                }
            }
        }
        // updateSection applies a per-item change to the config's debts or taxes.
        fun offer(items: List<DebtTaxItem>, defaults: List<DebtTaxItem>, updateSection: (BudgetConfig, (DebtTaxItem) -> DebtTaxItem) -> BudgetConfig) {
            items.forEach { item ->
                val base = defaults.find { it.name == item.name } ?: return@forEach
                val diffs = describe(base.amount, item.amount, base.day, item.day, base.isFatura, item.isFatura)
                if (diffs.isNotEmpty()) {
                    changes += DefaultChange("${item.name}: ${diffs.joinToString()}") { config ->
                        updateSection(config) { if (it.name == item.name) it.copy(amount = item.amount, day = item.day, isFatura = item.isFatura) else it }
                    }
                }
            }
        }
        offer(month.debts, config.debts) { config, transform -> config.copy(debts = config.debts.map(transform)) }
        offer(month.taxes, config.taxes) { config, transform -> config.copy(taxes = config.taxes.map(transform)) }
        return changes
    }

    private fun describe(oldAmount: Double, newAmount: Double, oldDay: Int?, newDay: Int?, oldFatura: Boolean, newFatura: Boolean) =
        listOfNotNull(
            if (oldAmount != newAmount) "amount ${number(oldAmount)} → ${number(newAmount)}" else null,
            if (oldDay != newDay) "day ${oldDay ?: "none"} → ${newDay ?: "none"}" else null,
            if (oldFatura != newFatura) "fatura $oldFatura → $newFatura" else null,
        )

    private fun number(value: Double) = value.toBigDecimal().stripTrailingZeros().toPlainString()

    /** Averages over [entries], as in budget.py's history report (balance = salary − expenses). */
    data class Averages(val salary: Double, val expenses: Double, val balance: Double, val categories: List<Pair<String, Double>>)

    fun averages(entries: List<HistoryEntry>): Averages {
        val salary = entries.map { it.salary }.average()
        val expenses = entries.map { it.total }.average()
        val categories = entries.flatMap { it.categories.keys }.toSortedSet()
            .map { category -> category to entries.map { it.categories[category] ?: 0.0 }.average() }
            .filter { it.second > 0 }
        return Averages(salary, expenses, salary - expenses, categories)
    }
}
