package com.buddy.budget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buddy.budget.model.BudgetHistory
import com.buddy.budget.model.BudgetText
import com.buddy.budget.model.Category
import com.buddy.budget.model.DefaultChange
import com.buddy.budget.model.DraftItem
import com.buddy.budget.portfolio.ui.copyToClipboard
import com.buddy.budget.portfolio.ui.shareFile
import com.buddy.budget.portfolio.ui.shareText
import com.buddy.budget.portfolio.ui.sharedFile
import com.buddy.budget.ui.components.BalanceSummary
import com.buddy.budget.ui.components.CategoryGroup
import com.buddy.budget.ui.components.ExpenseRow
import com.buddy.budget.ui.components.ItemDialogMode
import com.buddy.budget.ui.components.ItemEditDialog
import com.buddy.budget.ui.components.SalarySection
import com.buddy.budget.viewmodel.BudgetViewModel
import com.buddy.budget.viewmodel.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(viewModel: BudgetViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Budget · ${viewModel.draft.monthKey}") },
                    actions = { BackupMenu(tint = MaterialTheme.colorScheme.onPrimary) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                PrimaryTabRow(selectedTabIndex = viewModel.selectedTab) {
                    listOf("Plan", "Report", "History").forEachIndexed { index, title ->
                        Tab(selected = viewModel.selectedTab == index, onClick = { viewModel.selectedTab = index }, text = { Text(title) })
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (viewModel.selectedTab == 0) BalanceSummary(report = viewModel.report) },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (viewModel.selectedTab) {
            0 -> PlanContent(viewModel, modifier)
            1 -> ReportContent(viewModel, modifier)
            else -> HistoryContent(viewModel, modifier)
        }
    }
}

// ---------- Plan ----------

@Composable
private fun PlanContent(viewModel: BudgetViewModel, modifier: Modifier) {
    val draft = viewModel.draft
    var editing by remember { mutableStateOf<Pair<Section, Int>?>(null) }
    var creating by remember { mutableStateOf<Section?>(null) }
    var deleting by remember { mutableStateOf<Pair<Section, Int>?>(null) }
    var defaultChanges by remember { mutableStateOf<List<DefaultChange>?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }
    val previous = viewModel.previous
    val leftoverHint = if (previous != null && BudgetHistory.defaultLeftover(previous, java.time.LocalDate.now()) != 0.0) {
        "Balance of ${previous.monthKey}"
    } else {
        "Money left from last month"
    }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SalarySection(
                draft = draft,
                leftoverHint = leftoverHint,
                onSalaryChange = viewModel::updateSalary,
                onLeftoverChange = viewModel::updateLeftover,
                onProventosChange = viewModel::updateProventos,
                onUseFaturaLumpChange = viewModel::updateUseFaturaLump,
                onFaturaLumpChange = viewModel::updateFaturaLump,
            )
        }

        viewModel.expensesByCategory.forEach { (category, items) ->
            item(key = "cat_${category.name}") {
                CategoryGroup(
                    title = category.label,
                    total = items.filter { !it.value.inflow }.sumOf { amountOf(it.value) },
                    expanded = viewModel.isCategoryExpanded(category.name),
                    onToggle = { viewModel.toggleCategory(category.name) }
                ) {
                    items.forEach { (index, item) ->
                        ExpenseRow(
                            name = item.name,
                            amount = item.amount,
                            tag = tagFor(item),
                            onAmountChange = { viewModel.updateItem(Section.EXPENSES, index, item.copy(amount = it)) },
                            onEdit = { editing = Section.EXPENSES to index },
                        )
                    }
                }
            }
        }

        listOf(Section.DEBTS to "Debts", Section.TAXES to "Taxes").forEach { (section, title) ->
            val items = viewModel.items(section)
            if (items.isNotEmpty()) {
                item(key = section.name) {
                    CategoryGroup(
                        title = title,
                        total = items.sumOf { amountOf(it) },
                        expanded = viewModel.isCategoryExpanded(section.name),
                        onToggle = { viewModel.toggleCategory(section.name) }
                    ) {
                        items.forEachIndexed { index, item ->
                            ExpenseRow(
                                name = item.name,
                                amount = item.amount,
                                tag = tagFor(item),
                                onAmountChange = { viewModel.updateItem(section, index, item.copy(amount = it)) },
                                onEdit = { editing = section to index },
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recurring items", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add an item to your defaults and this month. Tap an item's name to rename, recategorize or delete it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Section.EXPENSES to "Expense", Section.DEBTS to "Debt", Section.TAXES to "Tax").forEach { (section, label) ->
                            OutlinedButton(onClick = { creating = section }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                                Text(label, Modifier.padding(start = 4.dp), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("One-off items", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Extra expenses or income for this month only. They go under Other and are never saved as defaults.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.addOneOff(inflow = false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                            Text("Expense", Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = { viewModel.addOneOff(inflow = true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                            Text("Bonus", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This month", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Edits are kept until the month changes. Save the month to add it to History (saving again replaces it).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = viewModel::saveMonth, modifier = Modifier.fillMaxWidth()) { Text("Save month to history") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { defaultChanges = viewModel.defaultChanges() }, modifier = Modifier.weight(1f)) { Text("Save as defaults…") }
                        OutlinedButton(onClick = { confirmingReset = true }, modifier = Modifier.weight(1f)) { Text("Reset month") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    editing?.let { (section, index) ->
        val item = viewModel.items(section).getOrNull(index)
        if (item == null) {
            editing = null
        } else {
            ItemEditDialog(
                item = item,
                title = item.name,
                mode = ItemDialogMode.EDIT,
                allowInflow = section == Section.EXPENSES,
                allowCategory = section == Section.EXPENSES,
                isNameTaken = { name -> viewModel.nameTaken(section, name, exceptIndex = index) },
                onDismiss = { editing = null },
                onSave = { updated ->
                    viewModel.updateItem(section, index, updated)
                    editing = null
                },
                onRemove = if (item.oneOff) {
                    {
                        viewModel.removeOneOff(index)
                        editing = null
                    }
                } else {
                    {
                        deleting = section to index
                        editing = null
                    }
                },
                removeLabel = if (item.oneOff) "Remove" else "Delete",
            )
        }
    }

    creating?.let { section ->
        val noun = when (section) {
            Section.EXPENSES -> "expense"
            Section.DEBTS -> "debt"
            Section.TAXES -> "tax"
        }
        ItemEditDialog(
            item = DraftItem(name = "", amount = ""),
            title = "New recurring $noun",
            mode = ItemDialogMode.CREATE,
            allowInflow = section == Section.EXPENSES,
            allowCategory = section == Section.EXPENSES,
            isNameTaken = { name -> viewModel.nameTaken(section, name, exceptIndex = null) },
            onDismiss = { creating = null },
            onSave = { item ->
                viewModel.addRecurring(section, item)
                creating = null
            },
            onRemove = null,
        )
    }

    deleting?.let { (section, index) ->
        val name = viewModel.items(section).getOrNull(index)?.name
        if (name == null) {
            deleting = null
        } else {
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Delete $name?") },
                text = { Text("It is removed from your defaults and from this month. Saved months in History keep it.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeRecurring(section, index)
                        deleting = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            )
        }
    }

    defaultChanges?.let { changes -> DefaultsDialog(changes, onDismiss = { defaultChanges = null }) { chosen ->
        viewModel.saveDefaults(chosen)
        defaultChanges = null
    } }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Reset this month?") },
            text = { Text("All edits and one-off items for this month are replaced by the defaults and last month's leftover and fatura total.") },
            confirmButton = { TextButton(onClick = { viewModel.resetMonth(); confirmingReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text("Cancel") } },
        )
    }
}

/** Lists every value that differs from the defaults; the ticked ones are written to the config. */
@Composable
private fun DefaultsDialog(changes: List<DefaultChange>, onDismiss: () -> Unit, onSave: (List<DefaultChange>) -> Unit) {
    var chosen by remember { mutableStateOf(setOf<Int>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as new defaults") },
        text = {
            if (changes.isEmpty()) {
                Text("Nothing differs from the defaults.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = chosen.size == changes.size, onCheckedChange = { all -> chosen = if (all) changes.indices.toSet() else emptySet() })
                            Text("All", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    changes.forEachIndexed { index, change ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = index in chosen, onCheckedChange = { checked -> chosen = if (checked) chosen + index else chosen - index })
                                Text(change.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = { onSave(chosen.sorted().map { changes[it] }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun amountOf(item: DraftItem) = item.amount.trim().replace(",", ".").toDoubleOrNull() ?: 0.0

private fun tagFor(item: DraftItem) = listOfNotNull(
    if (item.isFatura) "fatura" else item.day?.let { "d$it" },
    if (item.inflow) "inflow" else null,
    if (item.oneOff) "one-off" else null,
).joinToString(" · ")

// ---------- Report ----------

@Composable
private fun ReportContent(viewModel: BudgetViewModel, modifier: Modifier) {
    val report = viewModel.report
    val month = report.month
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    val previousMonth = viewModel.previous?.monthKey
    fun text() = BudgetText.report(report, viewModel.changesVsLast, previousMonth)

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !exporting,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        exporting = true
                        scope.launch {
                            try {
                                val file = sharedFile(context, "budget_${viewModel.draft.monthKey}.pdf")
                                withContext(Dispatchers.Default) {
                                    BudgetPdf(context, report, viewModel.draft.monthKey, viewModel.changesVsLast, previousMonth, viewModel.history).writeTo(file)
                                }
                                shareFile(context, file, "application/pdf", "Share budget PDF")
                            } finally {
                                exporting = false
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, Modifier.size(18.dp))
                    Text(if (exporting) "Creating PDF…" else "Share PDF report", Modifier.padding(start = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copyToClipboard(context, text()) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                        Text("Copy report", Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = { shareText(context, text(), "output.txt") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                        Text("Share .txt", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            ReportCard("Summary") {
                LineRow("Net salary", money(month.salary))
                if (month.leftover != 0.0) LineRow("Leftover", "+ ${money(month.leftover)}")
                if (report.totalInflow != 0.0) LineRow("Inflows", "+ ${money(report.totalInflow)}")
                LineRow("Outflows", "− ${money(report.totalOutflow)}")
                HorizontalDivider()
                val free = report.balance >= 0
                LineRow(
                    if (free) "Free to spend" else "Deficit",
                    money(abs(report.balance)),
                    emphasize = true,
                    color = if (free) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }

        item {
            ReportCard("Payment timeline") {
                report.timeline.forEach { entry ->
                    LineRow("${BudgetText.dayLabel(entry.day)}  ${entry.name}", signed(entry.amount, entry.inflow), emphasize = entry.isFatura)
                }
            }
        }

        item {
            ReportCard("Fatura · day ${month.faturaDay}") {
                month.faturaLump?.let { LineRow("Lump sum", signed(it, inflow = false)) }
                report.faturaItems.filter { it.amount > 0 }.forEach { LineRow("${it.name} · ${Category.label(it.category)}", signed(it.amount, it.inflow)) }
                report.faturaDebts.filter { it.amount > 0 }.forEach { LineRow("${it.name} · Debt", signed(it.amount, inflow = false)) }
                report.faturaTaxes.filter { it.amount > 0 }.forEach { LineRow("${it.name} · Taxes", signed(it.amount, inflow = false)) }
                if (month.proventos > 0) LineRow("Proventos", signed(month.proventos, inflow = true))
                HorizontalDivider()
                LineRow("Total (net)", money(report.totalFatura), emphasize = true)
            }
        }

        item {
            ReportCard("Non-fatura") {
                report.nonFaturaItems.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.forEach {
                    LineRow("${BudgetText.dayLabel(it.day)}  ${it.name} · ${Category.label(it.category)}", signed(it.amount, it.inflow))
                }
                HorizontalDivider()
                LineRow("Total (net)", money(report.totalNonFatura), emphasize = true)
            }
        }

        listOf("Debts" to month.debts, "Taxes" to month.taxes).forEach { (title, items) ->
            if (items.any { it.amount > 0 }) {
                item {
                    ReportCard(title) {
                        items.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.forEach {
                            LineRow("${BudgetText.dayLabel(it.day)}  ${it.name}${if (it.isFatura) " · fatura" else ""}", money(it.amount))
                        }
                        HorizontalDivider()
                        LineRow("Total", money(items.sumOf { it.amount }), emphasize = true)
                    }
                }
            }
        }

        item {
            ReportCard("By category (outflows)") {
                val total = report.byCategory.sumOf { it.second }
                report.byCategory.forEach { (category, amount) ->
                    LineRow("${Category.label(category)} · ${"%.1f%%".format(if (total != 0.0) amount / total * 100 else 0.0)}", money(amount))
                }
                HorizontalDivider()
                LineRow("Total outflow", money(report.totalOutflow), emphasize = true)
                if (report.totalInflow != 0.0) LineRow("Total inflow", money(report.totalInflow))
            }
        }

        if (viewModel.changesVsLast.isNotEmpty() && previousMonth != null) {
            item {
                ReportCard("Changes vs $previousMonth") {
                    viewModel.changesVsLast.forEach { LineRow(it.name, "${money(it.old)} → ${money(it.new)}  ${it.percent}") }
                }
            }
        }
    }
}

// ---------- History ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(viewModel: BudgetViewModel, modifier: Modifier) {
    var last by rememberSaveable { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val entries = viewModel.history.let { history -> last?.let { history.takeLast(it) } ?: history }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(null to "All", 3 to "Last 3", 6 to "Last 6", 12 to "Last 12").forEach { (count, label) ->
                    FilterChip(selected = last == count, onClick = { last = count }, label = { Text(label) })
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "No months saved yet. Use \"Save month to history\" on the Plan tab.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            return@LazyColumn
        }
        item {
            OutlinedButton(onClick = { shareText(context, BudgetText.history(entries), "history.txt") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Share, contentDescription = null, Modifier.size(18.dp))
                Text("Share history .txt", Modifier.padding(start = 8.dp))
            }
        }
        if (entries.size > 1) {
            item {
                val averages = BudgetHistory.averages(entries)
                ReportCard("Averages over ${entries.size} months") {
                    LineRow("Salary", money(averages.salary))
                    LineRow("Expenses", money(averages.expenses))
                    LineRow("Balance (salary − expenses)", money(averages.balance), emphasize = true)
                    HorizontalDivider()
                    averages.categories.forEach { (category, average) -> LineRow(Category.label(category), money(average)) }
                }
            }
        }
        entries.reversed().forEach { entry ->
            item(key = entry.monthKey) {
                ReportCard(entry.monthKey) {
                    LineRow("Salary", money(entry.salary))
                    LineRow("Balance", money(entry.effectiveBalance), emphasize = true)
                    HorizontalDivider()
                    entry.categories.filterValues { it > 0 }.toList().sortedByDescending { it.second }.forEach { (category, amount) ->
                        val share = if (entry.total != 0.0) amount / entry.total * 100 else 0.0
                        LineRow("${Category.label(category)} · ${"%.1f%%".format(share)}", money(amount))
                    }
                    HorizontalDivider()
                    LineRow("Total expenses", money(entry.total), emphasize = true)
                    LineRow("Fatura", money(entry.fatura))
                    LineRow("Non-fatura", money(entry.nonFatura))
                }
            }
        }
    }
}

// ---------- shared pieces ----------

@Composable
private fun ReportCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LineRow(
    label: String,
    value: String,
    emphasize: Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val weight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = weight, color = color)
    }
}

private fun money(value: Double): String = currency.format(value)

private fun signed(amount: Double, inflow: Boolean) = (if (inflow) "+ " else "− ") + money(amount)
