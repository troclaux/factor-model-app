package com.buddy.budget.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import com.buddy.budget.data.ConfigRepository
import com.buddy.budget.model.BudgetConfig
import com.buddy.budget.model.BudgetResult
import com.buddy.budget.model.Category
import com.buddy.budget.model.DebtTaxItem
import com.buddy.budget.model.ExpenseItem
import com.buddy.budget.model.calculateBudget

data class EditableItem<T>(
    val original: T,
    val amountText: String
)

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConfigRepository(application)
    private var config: BudgetConfig = repository.loadConfig()

    var salary = mutableStateOf(config.income.toBigDecimal().stripTrailingZeros().toPlainString())
        private set
    var leftover = mutableStateOf("0")
        private set

    val expenses = mutableStateListOf<EditableItem<ExpenseItem>>()
    val debts = mutableStateListOf<EditableItem<DebtTaxItem>>()
    val taxes = mutableStateListOf<EditableItem<DebtTaxItem>>()

    val expandedCategories = mutableStateMapOf<String, Boolean>()
    var faturaDebitDay = config.faturaDebitDay
        private set

    init {
        config.expenses.forEach { item ->
            expenses.add(EditableItem(item, formatDefault(item.amount)))
        }
        config.debts.forEach { item ->
            debts.add(EditableItem(item, formatDefault(item.amount)))
        }
        config.taxes.forEach { item ->
            taxes.add(EditableItem(item, formatDefault(item.amount)))
        }
    }

    val result by derivedStateOf {
        calculateBudget(
            salary = parseAmount(salary.value),
            leftover = parseAmount(leftover.value),
            expenses = expenses.map { it.original to parseAmount(it.amountText) },
            debts = debts.map { it.original to parseAmount(it.amountText) },
            taxes = taxes.map { it.original to parseAmount(it.amountText) }
        )
    }

    val expensesByCategory by derivedStateOf {
        expenses.groupBy { Category.fromString(it.original.category) }
            .toSortedMap(compareBy { it.ordinal })
    }

    fun updateSalary(value: String) {
        salary.value = value
    }

    fun updateLeftover(value: String) {
        leftover.value = value
    }

    fun updateExpenseAmount(index: Int, value: String) {
        if (index in expenses.indices) {
            expenses[index] = expenses[index].copy(amountText = value)
        }
    }

    fun updateDebtAmount(index: Int, value: String) {
        if (index in debts.indices) {
            debts[index] = debts[index].copy(amountText = value)
        }
    }

    fun updateTaxAmount(index: Int, value: String) {
        if (index in taxes.indices) {
            taxes[index] = taxes[index].copy(amountText = value)
        }
    }

    fun toggleCategory(category: String) {
        expandedCategories[category] = !(expandedCategories[category] ?: false)
    }

    fun isCategoryExpanded(category: String): Boolean {
        return expandedCategories[category] ?: false
    }

    fun categoryTotal(category: Category): Double {
        return expenses
            .filter { Category.fromString(it.original.category) == category && !it.original.inflow }
            .sumOf { parseAmount(it.amountText) }
    }

    private fun formatDefault(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            amount.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }

    companion object {
        fun parseAmount(text: String): Double {
            if (text.isBlank()) return 0.0
            return text.replace(",", ".").toDoubleOrNull() ?: 0.0
        }
    }
}
