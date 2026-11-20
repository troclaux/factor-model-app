package com.buddy.budget.model

fun calculateBudget(
    salary: Double,
    leftover: Double,
    expenses: List<Pair<ExpenseItem, Double>>,
    debts: List<Pair<DebtTaxItem, Double>>,
    taxes: List<Pair<DebtTaxItem, Double>>
): BudgetResult {
    val faturaOut = expenses
        .filter { (item, _) -> item.isFatura && !item.inflow }
        .sumOf { (_, amount) -> amount }
    val faturaIn = expenses
        .filter { (item, _) -> item.isFatura && item.inflow }
        .sumOf { (_, amount) -> amount }
    val nonFaturaOut = expenses
        .filter { (item, _) -> !item.isFatura && !item.inflow }
        .sumOf { (_, amount) -> amount }
    val nonFaturaIn = expenses
        .filter { (item, _) -> !item.isFatura && item.inflow }
        .sumOf { (_, amount) -> amount }
    val totalDebt = debts.sumOf { (_, amount) -> amount }
    val totalTaxes = taxes.sumOf { (_, amount) -> amount }

    val totalOutflow = faturaOut + nonFaturaOut + totalDebt + totalTaxes
    val totalInflow = faturaIn + nonFaturaIn
    val balance = salary + leftover + totalInflow - totalOutflow

    val categoryTotals = mutableMapOf<String, Double>()
    expenses.filter { (item, _) -> !item.inflow }.forEach { (item, amount) ->
        val cat = item.category
        categoryTotals[cat] = (categoryTotals[cat] ?: 0.0) + amount
    }
    if (totalDebt > 0) categoryTotals["debt"] = totalDebt
    if (totalTaxes > 0) categoryTotals["taxes"] = (categoryTotals["taxes"] ?: 0.0) + totalTaxes

    return BudgetResult(
        grossSalary = salary,
        leftover = leftover,
        totalTaxes = totalTaxes,
        netSalary = salary - totalTaxes,
        totalFaturaOut = faturaOut,
        totalNonFaturaOut = nonFaturaOut,
        totalDebt = totalDebt,
        totalOutflow = totalOutflow,
        totalInflow = totalInflow,
        balance = balance,
        categoryTotals = categoryTotals
    )
}
