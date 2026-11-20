package com.buddy.budget.model

import java.time.LocalDate

/** An expense, debt or tax being edited. Amounts are kept as typed so the fields round-trip exactly. */
data class DraftItem(
    val name: String,
    val amount: String,
    val day: Int? = null,
    val isFatura: Boolean = false,
    val inflow: Boolean = false,
    val category: String = "other",
    /** One-off extra expense or bonus for this month only (budget.py's --expense / --bonus). */
    val oneOff: Boolean = false,
)

/** This month's plan as edited in the app; saved so edits survive restarts, and reset when the month changes. */
data class BudgetDraft(
    val monthKey: String,
    val salary: String,
    val leftover: String,
    val proventos: String,
    val useFaturaLump: Boolean,
    val faturaLump: String,
    val expenses: List<DraftItem>,
    val debts: List<DraftItem>,
    val taxes: List<DraftItem>,
) {
    fun toMonth(faturaDay: Int) = BudgetMonth(
        salary = parseAmount(salary),
        leftover = parseAmount(leftover),
        proventos = parseAmount(proventos),
        faturaLump = if (useFaturaLump) parseAmount(faturaLump) else null,
        expenses = expenses.map { ExpenseItem(it.name, parseAmount(it.amount), it.day, it.category, it.isFatura, it.inflow) },
        debts = debts.map { DebtTaxItem(it.name, parseAmount(it.amount), it.day, it.isFatura) },
        taxes = taxes.map { DebtTaxItem(it.name, parseAmount(it.amount), it.day, it.isFatura) },
        faturaDay = faturaDay,
    )

    companion object {
        /** A new month from the config, with leftover and fatura total remembered from last month (as budget.py does). */
        fun fromConfig(config: BudgetConfig, previous: HistoryEntry?, today: LocalDate): BudgetDraft {
            val faturaLump = BudgetHistory.defaultFaturaLump(previous)
            return BudgetDraft(
                monthKey = BudgetHistory.monthKey(today),
                salary = format(config.income),
                leftover = format(BudgetHistory.defaultLeftover(previous, today)),
                proventos = format(config.proventos.amount),
                useFaturaLump = faturaLump != 0.0,
                faturaLump = if (faturaLump != 0.0) format(faturaLump) else "",
                expenses = config.expenses.map { DraftItem(it.name, format(it.amount), it.day, it.isFatura, it.inflow, it.category) },
                debts = config.debts.map { DraftItem(it.name, format(it.amount), it.day, it.isFatura, category = "debt") },
                taxes = config.taxes.map { DraftItem(it.name, format(it.amount), it.day, it.isFatura, category = "taxes") },
            )
        }

        fun parseAmount(text: String): Double = text.trim().replace(",", ".").toDoubleOrNull() ?: 0.0

        fun format(amount: Double): String = amount.toBigDecimal().stripTrailingZeros().toPlainString()
    }
}
