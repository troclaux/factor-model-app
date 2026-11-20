package com.buddy.budget.model

import com.google.gson.annotations.SerializedName

/** Recurring defaults, in the same JSON format as budget.py's budget_config.json. */
data class BudgetConfig(
    @SerializedName("fatura_debit_day") val faturaDebitDay: Int = 9,
    /** Default net salary (after IR, INSS and other payroll deductions). */
    val income: Double = 0.0,
    val proventos: Proventos = Proventos(),
    val expenses: List<ExpenseItem> = emptyList(),
    val taxes: List<DebtTaxItem> = emptyList(),
    val debts: List<DebtTaxItem> = emptyList()
)

/** Income received before the fatura debits (e.g. dividends); counted as a fatura inflow. */
data class Proventos(
    val amount: Double = 0.0,
    /** Informational only. */
    val day: Int? = null
)

data class ExpenseItem(
    val name: String = "",
    val amount: Double = 0.0,
    val day: Int? = null,
    val category: String = "other",
    @SerializedName("is_fatura") val isFatura: Boolean = false,
    val inflow: Boolean = false
)

data class DebtTaxItem(
    val name: String = "",
    val amount: Double = 0.0,
    val day: Int? = null,
    @SerializedName("is_fatura") val isFatura: Boolean = false
)

enum class Category(val label: String) {
    HOUSING("Housing"),
    UTILITIES("Utilities"),
    FOOD("Food"),
    TRANSPORT("Transport"),
    HEALTH("Health"),
    ENTERTAINMENT("Entertainment"),
    DONATIONS("Donations"),
    SUBSCRIPTIONS("Subscriptions"),
    TAXES("Taxes"),
    DEBT("Debt"),
    OTHER("Other");

    companion object {
        fun fromString(s: String): Category =
            entries.find { it.name.equals(s, ignoreCase = true) } ?: OTHER

        /** Display label for a config category key such as "housing"; unknown keys are shown as is. */
        fun label(key: String): String =
            entries.find { it.name.equals(key, ignoreCase = true) }?.label ?: key
    }
}
