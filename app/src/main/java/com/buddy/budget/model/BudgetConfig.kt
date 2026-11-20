package com.buddy.budget.model

import com.google.gson.annotations.SerializedName

data class BudgetConfig(
    @SerializedName("fatura_debit_day") val faturaDebitDay: Int = 9,
    val income: Double = 0.0,
    val expenses: List<ExpenseItem> = emptyList(),
    val taxes: List<DebtTaxItem> = emptyList(),
    val debts: List<DebtTaxItem> = emptyList()
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

data class BudgetResult(
    val grossSalary: Double,
    val leftover: Double,
    val totalTaxes: Double,
    val netSalary: Double,
    val totalFaturaOut: Double,
    val totalNonFaturaOut: Double,
    val totalDebt: Double,
    val totalOutflow: Double,
    val totalInflow: Double,
    val balance: Double,
    val categoryTotals: Map<String, Double>
)

enum class Category(val label: String) {
    HOUSING("Housing"),
    UTILITIES("Utilities"),
    FOOD("Food"),
    TRANSPORT("Transport"),
    HEALTH("Health"),
    ENTERTAINMENT("Entertainment"),
    DONATIONS("Donations"),
    TAXES("Taxes"),
    DEBT("Debt"),
    OTHER("Other");

    companion object {
        fun fromString(s: String): Category =
            entries.find { it.name.equals(s, ignoreCase = true) } ?: OTHER
    }
}
