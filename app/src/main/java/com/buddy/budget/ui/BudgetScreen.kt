package com.buddy.budget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buddy.budget.model.Category
import com.buddy.budget.ui.components.BalanceSummary
import com.buddy.budget.ui.components.CategoryGroup
import com.buddy.budget.ui.components.ExpenseRow
import com.buddy.budget.ui.components.SalarySection
import com.buddy.budget.viewmodel.BudgetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    val result = viewModel.result

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buddy") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            BalanceSummary(result = result)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Salary section
            item {
                SalarySection(
                    salary = viewModel.salary.value,
                    leftover = viewModel.leftover.value,
                    onSalaryChange = viewModel::updateSalary,
                    onLeftoverChange = viewModel::updateLeftover
                )
            }

            // Expense categories
            val categories = viewModel.expensesByCategory
            categories.forEach { (category, items) ->
                item(key = "cat_${category.name}") {
                    CategoryGroup(
                        title = category.label,
                        total = viewModel.categoryTotal(category),
                        expanded = viewModel.isCategoryExpanded(category.name),
                        onToggle = { viewModel.toggleCategory(category.name) }
                    ) {
                        items.forEach { editableItem ->
                            val globalIndex = viewModel.expenses.indexOf(editableItem)
                            val tag = if (editableItem.original.isFatura) {
                                "fatura"
                            } else {
                                editableItem.original.day?.let { "d$it" } ?: ""
                            }
                            ExpenseRow(
                                name = editableItem.original.name,
                                amount = editableItem.amountText,
                                tag = tag,
                                onAmountChange = { viewModel.updateExpenseAmount(globalIndex, it) }
                            )
                        }
                    }
                }
            }

            // Debts section
            if (viewModel.debts.isNotEmpty()) {
                item(key = "debts") {
                    val debtTotal = viewModel.debts.sumOf {
                        BudgetViewModel.parseAmount(it.amountText)
                    }
                    CategoryGroup(
                        title = "Debts",
                        total = debtTotal,
                        expanded = viewModel.isCategoryExpanded("_debts"),
                        onToggle = { viewModel.toggleCategory("_debts") }
                    ) {
                        viewModel.debts.forEachIndexed { index, item ->
                            val tag = item.original.day?.let { "d$it" } ?: ""
                            ExpenseRow(
                                name = item.original.name,
                                amount = item.amountText,
                                tag = tag,
                                onAmountChange = { viewModel.updateDebtAmount(index, it) }
                            )
                        }
                    }
                }
            }

            // Taxes section
            if (viewModel.taxes.isNotEmpty()) {
                item(key = "taxes") {
                    val taxTotal = viewModel.taxes.sumOf {
                        BudgetViewModel.parseAmount(it.amountText)
                    }
                    CategoryGroup(
                        title = "Taxes",
                        total = taxTotal,
                        expanded = viewModel.isCategoryExpanded("_taxes"),
                        onToggle = { viewModel.toggleCategory("_taxes") }
                    ) {
                        viewModel.taxes.forEachIndexed { index, item ->
                            val tag = item.original.day?.let { "d$it" } ?: ""
                            ExpenseRow(
                                name = item.original.name,
                                amount = item.amountText,
                                tag = tag,
                                onAmountChange = { viewModel.updateTaxAmount(index, it) }
                            )
                        }
                    }
                }
            }

            // Bottom spacing so content isn't hidden behind the balance bar
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}
