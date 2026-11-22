package com.buddy.budget.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.IconButton
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
import com.buddy.budget.model.BudgetDraft
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
import com.buddy.budget.ui.theme.Green500
import com.buddy.budget.ui.theme.Red500
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
                    listOf("Plan", "Items", "Report", "History").forEachIndexed { index, title ->
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
            1 -> ItemsContent(viewModel, modifier)
            2 -> ReportContent(viewModel, modifier)
            else -> HistoryContent(viewModel, modifier)
        }
    }
}

// ---------- Plan ----------

@Composable
private fun PlanContent(viewModel: BudgetViewModel, modifier: Modifier) {
    val draft = viewModel.draft
    var editing by remember { mutableStateOf<Pair<Section, Int>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Section, Int>?>(null) }
    var defaultChanges by remember { mutableStateOf<List<DefaultChange>?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }
    var confirmingSave by remember { mutableStateOf(false) }
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
                    total = items.filter { counted(it.value, draft) }.sumOf { amountOf(it.value) },
                    expanded = viewModel.isCategoryExpanded(category.name),
                    onToggle = { viewModel.toggleCategory(category.name) }
                ) {
                    items.forEach { (index, item) ->
                        ExpenseRow(
                            name = item.name,
                            amount = item.amount,
                            tag = tagFor(item, draft.useFaturaLump),
                            onAmountChange = { viewModel.updateItem(Section.EXPENSES, index, item.copy(amount = it)) },
                            onEdit = { editing = Section.EXPENSES to index },
                            onDelete = { if (item.oneOff) viewModel.removeOneOff(index) else deleting = Section.EXPENSES to index },
                        )
                    }
                }
            }
        }

        if (viewModel.inflows.isNotEmpty()) {
            item(key = BudgetViewModel.INFLOWS) {
                CategoryGroup(
                    title = "Inflows",
                    total = viewModel.inflows.filter { counted(it.value, draft) }.sumOf { amountOf(it.value) },
                    expanded = viewModel.isCategoryExpanded(BudgetViewModel.INFLOWS),
                    onToggle = { viewModel.toggleCategory(BudgetViewModel.INFLOWS) }
                ) {
                    viewModel.inflows.forEach { (index, item) ->
                        ExpenseRow(
                            name = item.name,
                            amount = item.amount,
                            tag = tagFor(item, draft.useFaturaLump),
                            onAmountChange = { viewModel.updateItem(Section.EXPENSES, index, item.copy(amount = it)) },
                            onEdit = { editing = Section.EXPENSES to index },
                            onDelete = { if (item.oneOff) viewModel.removeOneOff(index) else deleting = Section.EXPENSES to index },
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
                                tag = tagFor(item, faturaLump = false),
                                onAmountChange = { viewModel.updateItem(section, index, item.copy(amount = it)) },
                                onEdit = { editing = section to index },
                                onDelete = { deleting = section to index },
                            )
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
                    Button(onClick = { confirmingSave = true }, modifier = Modifier.fillMaxWidth()) { Text("Save month to history") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { defaultChanges = viewModel.defaultChanges() }, modifier = Modifier.weight(1f)) { Text("Save as defaults…") }
                        OutlinedButton(onClick = { confirmingReset = true }, modifier = Modifier.weight(1f)) { Text("Reset month") }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }

    ItemDialogs(viewModel, editing, { editing = it }, deleting, { deleting = it })

    defaultChanges?.let { changes -> DefaultsDialog(changes, onDismiss = { defaultChanges = null }) { chosen ->
        viewModel.saveDefaults(chosen)
        defaultChanges = null
    } }

    if (confirmingSave) {
        val monthKey = draft.monthKey
        val replaces = viewModel.history.any { it.monthKey == monthKey }
        AlertDialog(
            onDismissRequest = { confirmingSave = false },
            title = { Text("Save $monthKey to history?") },
            text = {
                Text(
                    "Balance: ${money(viewModel.report.balance)}." +
                        if (replaces) " This replaces the $monthKey entry already in History." else ""
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.saveMonth(); confirmingSave = false }) { Text(if (replaces) "Replace" else "Save") } },
            dismissButton = { TextButton(onClick = { confirmingSave = false }) { Text("Cancel") } },
        )
    }

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

/** The edit dialog for [editing] and the delete confirmation for [deleting], shared by the Plan and Report tabs. */
@Composable
private fun ItemDialogs(
    viewModel: BudgetViewModel,
    editing: Pair<Section, Int>?,
    onEditingChange: (Pair<Section, Int>?) -> Unit,
    deleting: Pair<Section, Int>?,
    onDeletingChange: (Pair<Section, Int>?) -> Unit,
) {
    editing?.let { (section, index) ->
        val item = viewModel.items(section).getOrNull(index)
        if (item == null) {
            onEditingChange(null)
        } else {
            ItemEditDialog(
                item = item,
                title = item.name,
                mode = ItemDialogMode.EDIT,
                categories = if (section == Section.EXPENSES && !item.inflow) expenseCategories else null,
                defaultAmount = if (item.oneOff) null else viewModel.defaultAmount(section, item.name),
                isNameTaken = { name -> viewModel.nameTaken(section, name, exceptIndex = index) },
                onDismiss = { onEditingChange(null) },
                onSave = { updated, defaultAmount ->
                    viewModel.updateItem(section, index, updated, defaultAmount)
                    onEditingChange(null)
                },
                onRemove = if (item.oneOff) {
                    {
                        viewModel.removeOneOff(index)
                        onEditingChange(null)
                    }
                } else {
                    {
                        onDeletingChange(section to index)
                        onEditingChange(null)
                    }
                },
                removeLabel = if (item.oneOff) "Remove" else "Delete",
            )
        }
    }

    deleting?.let { (section, index) ->
        val name = viewModel.items(section).getOrNull(index)?.name
        if (name == null) {
            onDeletingChange(null)
        } else {
            AlertDialog(
                onDismissRequest = { onDeletingChange(null) },
                title = { Text("Delete $name?") },
                text = { Text("It is removed from your defaults and from this month. Saved months in History keep it.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeRecurring(section, index)
                        onDeletingChange(null)
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { onDeletingChange(null) }) { Text("Cancel") } },
            )
        }
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

/** Categories an expense can be edited to; Debt and Taxes are separate sections. */
private val expenseCategories = Category.entries.filter { it != Category.DEBT && it != Category.TAXES }

private fun amountOf(item: DraftItem) = item.amount.trim().replace(",", ".").toDoubleOrNull() ?: 0.0

/** Whether an expense row counts in its group's total: with a fatura lump sum, fatura items do not. */
private fun counted(item: DraftItem, draft: BudgetDraft) = !(draft.useFaturaLump && item.isFatura)

/** With a fatura lump sum, fatura items are marked as part of that total. */
private fun tagFor(item: DraftItem, faturaLump: Boolean) = listOfNotNull(
    if (item.isFatura) (if (faturaLump) "fatura (in total)" else "fatura") else item.day?.let { "d$it" },
    if (item.inflow) "inflow" else null,
    if (item.oneOff) "one-off" else null,
).joinToString(" · ")

// ---------- Items ----------

/** Every inflow and outflow of this month in one list, to add, edit or delete them. */
@Composable
private fun ItemsContent(viewModel: BudgetViewModel, modifier: Modifier) {
    var editing by remember { mutableStateOf<Pair<Section, Int>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Section, Int>?>(null) }
    // True while adding a recurring inflow, false for an outflow.
    var creatingInflow by remember { mutableStateOf<Boolean?>(null) }
    // Every item with its section and index, outflows before inflows.
    val allItems = (listOf(Section.EXPENSES, Section.DEBTS, Section.TAXES)
        .flatMap { section -> viewModel.items(section).withIndex().map { section to it } })
        .sortedBy { it.second.value.inflow }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recurring items", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Added to your defaults and to this month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false to "Outflow", true to "Inflow").forEach { (inflow, label) ->
                            OutlinedButton(onClick = { creatingInflow = inflow }, modifier = Modifier.weight(1f)) {
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
                        "Extra expenses or income for this month only. Expenses go under Other, bonuses under Inflows; they are never saved as defaults.",
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

        listOf("Fatura" to true, "Non-fatura" to false).forEach { (title, fatura) ->
            val rows = allItems.filter { it.second.value.isFatura == fatura }
            item(key = title) {
                ReportCard("$title (${rows.size})") {
                    if (rows.isEmpty()) Text("None yet.", style = MaterialTheme.typography.bodySmall)
                    rows.forEach { (section, indexed) ->
                        val (index, item) = indexed
                        ManagedItemRow(
                            item = item,
                            kind = when (section) {
                                Section.EXPENSES -> if (item.inflow) null else Category.label(item.category)
                                Section.DEBTS -> "Debt"
                                Section.TAXES -> "Taxes"
                            },
                            defaultAmount = if (item.oneOff) null else viewModel.defaultAmount(section, item.name),
                            onEdit = { editing = section to index },
                            onDelete = { if (item.oneOff) viewModel.removeOneOff(index) else deleting = section to index },
                        )
                    }
                }
            }
        }
    }

    ItemDialogs(viewModel, editing, { editing = it }, deleting, { deleting = it })

    creatingInflow?.let { inflow ->
        ItemEditDialog(
            item = DraftItem(name = "", amount = "", inflow = inflow),
            title = if (inflow) "New recurring inflow" else "New recurring outflow",
            mode = ItemDialogMode.CREATE,
            // Debt and Taxes outflows go to their own sections.
            categories = if (inflow) null else Category.entries,
            defaultAmount = null,
            isNameTaken = viewModel::nameTakenAnywhere,
            onDismiss = { creatingInflow = null },
            onSave = { item, _ ->
                viewModel.addRecurring(item)
                creatingInflow = null
            },
            onRemove = null,
        )
    }

}

/** Name with category, day and default on a second line, and this month's signed amount; tap to edit. */
@Composable
private fun ManagedItemRow(item: DraftItem, kind: String?, defaultAmount: String?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val details = listOfNotNull(
        kind,
        if (item.isFatura) null else item.day?.let { "day $it" } ?: "no day",
        defaultAmount?.let { "default ${money(BudgetDraft.parseAmount(it))}" },
        if (item.oneOff) "one-off" else null,
    ).joinToString(" · ")
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Inflows in green, outflows in red.
        Text(
            signed(amountOf(item), item.inflow),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (item.inflow) Green500 else Red500,
        )
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit ${item.name}") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${item.name}", tint = MaterialTheme.colorScheme.error) }
    }
}

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
    var editing by remember { mutableStateOf<Pair<Section, Int>?>(null) }
    var deleting by remember { mutableStateOf<Pair<Section, Int>?>(null) }
    // Opens the editor for this month's item with that name.
    fun editor(section: Section, name: String): () -> Unit = {
        viewModel.items(section).indexOfFirst { it.name == name }.takeIf { it >= 0 }?.let { editing = section to it }
    }

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
                report.faturaItems.filter { it.amount > 0 }.forEach { LineRow("${it.name} · ${Category.label(it.category)}", signed(it.amount, it.inflow), onEdit = editor(Section.EXPENSES, it.name)) }
                report.faturaDebts.filter { it.amount > 0 }.forEach { LineRow("${it.name} · Debt", signed(it.amount, inflow = false), onEdit = editor(Section.DEBTS, it.name)) }
                report.faturaTaxes.filter { it.amount > 0 }.forEach { LineRow("${it.name} · Taxes", signed(it.amount, inflow = false), onEdit = editor(Section.TAXES, it.name)) }
                HorizontalDivider()
                LineRow("Total", money(report.totalFatura), emphasize = true)
            }
        }

        item {
            ReportCard("Non-fatura") {
                report.nonFaturaItems.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.forEach {
                    LineRow("${BudgetText.dayLabel(it.day)}  ${it.name} · ${Category.label(it.category)}", signed(it.amount, it.inflow), onEdit = editor(Section.EXPENSES, it.name))
                }
                HorizontalDivider()
                LineRow("Total (net)", money(report.totalNonFatura), emphasize = true)
            }
        }

        listOf(Triple("Debts", month.debts, Section.DEBTS), Triple("Taxes", month.taxes, Section.TAXES)).forEach { (title, items, section) ->
            if (items.any { it.amount > 0 }) {
                item {
                    ReportCard(title) {
                        items.filter { it.amount > 0 }.sortedBy { it.day ?: 32 }.forEach {
                            LineRow("${BudgetText.dayLabel(it.day)}  ${it.name}${if (it.isFatura) " · fatura" else ""}", money(it.amount), onEdit = editor(section, it.name))
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

    ItemDialogs(viewModel, editing, { editing = it }, deleting, { deleting = it })
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
    /** When set, the row is tappable and shows a pencil. */
    onEdit: (() -> Unit)? = null,
) {
    val weight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
    val rowModifier = if (onEdit != null) Modifier.fillMaxWidth().clickable(onClick = onEdit) else Modifier.fillMaxWidth()
    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = weight, color = color)
        if (onEdit != null) Icon(Icons.Default.Edit, contentDescription = "Edit", Modifier.padding(start = 8.dp).size(16.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

private fun money(value: Double): String = currency.format(value)

private fun signed(amount: Double, inflow: Boolean) = (if (inflow) "+ " else "− ") + money(amount)
