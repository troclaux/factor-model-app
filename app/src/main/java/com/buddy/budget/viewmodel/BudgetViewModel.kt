package com.buddy.budget.viewmodel

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.buddy.budget.data.BudgetStore
import com.buddy.budget.data.ConfigRepository
import com.buddy.budget.model.BudgetConfig
import com.buddy.budget.model.BudgetDraft
import com.buddy.budget.model.BudgetHistory
import com.buddy.budget.model.Category
import com.buddy.budget.model.DefaultChange
import com.buddy.budget.model.DraftItem
import com.buddy.budget.model.HistoryEntry
import com.buddy.budget.model.buildBudgetReport
import java.time.LocalDate

/** Which list of the draft an item belongs to. */
enum class Section { EXPENSES, DEBTS, TAXES }

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val configRepository = ConfigRepository(application)
    private val store = BudgetStore(application.filesDir)

    var config: BudgetConfig by mutableStateOf(configRepository.loadConfig())
        private set
    var history: List<HistoryEntry> by mutableStateOf(store.loadHistory())
        private set
    var draft: BudgetDraft by mutableStateOf(loadOrStartDraft())
        private set
    var selectedTab by mutableStateOf(0)
    var message by mutableStateOf<String?>(null)
        private set

    val expandedCategories = mutableStateMapOf<String, Boolean>()

    val month by derivedStateOf { draft.toMonth(config.faturaDebitDay) }
    val report by derivedStateOf { buildBudgetReport(month) }
    val previous by derivedStateOf { BudgetHistory.previousEntry(history, LocalDate.now()) }
    val changesVsLast by derivedStateOf { BudgetHistory.changesVsLast(previous, month) }

    /** Expenses grouped by category in the CLI's category order; fatura items are hidden while a lump sum is used. */
    val expensesByCategory by derivedStateOf {
        draft.expenses.withIndex()
            .filter { !(draft.useFaturaLump && it.value.isFatura) }
            .groupBy { Category.fromString(it.value.category) }
            .toSortedMap(compareBy { it.ordinal })
    }

    /** The saved draft if it is for the current month, otherwise a new month from the config. */
    private fun loadOrStartDraft(): BudgetDraft {
        val today = LocalDate.now()
        val saved = store.loadDraft()?.takeIf { draft ->
            draft.monthKey == BudgetHistory.monthKey(today) && runCatching { draft.toMonth(config.faturaDebitDay) }.isSuccess
        }
        return saved ?: BudgetDraft.fromConfig(config, BudgetHistory.previousEntry(history, today), today)
    }

    private fun edit(transform: (BudgetDraft) -> BudgetDraft) {
        draft = transform(draft)
        store.saveDraft(draft)
    }

    fun updateSalary(value: String) = edit { it.copy(salary = value) }
    fun updateLeftover(value: String) = edit { it.copy(leftover = value) }
    fun updateProventos(value: String) = edit { it.copy(proventos = value) }
    fun updateUseFaturaLump(value: Boolean) = edit { it.copy(useFaturaLump = value) }
    fun updateFaturaLump(value: String) = edit { it.copy(faturaLump = value) }

    fun items(section: Section): List<DraftItem> = when (section) {
        Section.EXPENSES -> draft.expenses
        Section.DEBTS -> draft.debts
        Section.TAXES -> draft.taxes
    }

    fun updateItem(section: Section, index: Int, item: DraftItem) = edit { current ->
        fun List<DraftItem>.replaced() = mapIndexed { position, existing -> if (position == index) item else existing }
        when (section) {
            Section.EXPENSES -> current.copy(expenses = current.expenses.replaced())
            Section.DEBTS -> current.copy(debts = current.debts.replaced())
            Section.TAXES -> current.copy(taxes = current.taxes.replaced())
        }
    }

    /** Adds a one-off expense or bonus for this month, named like budget.py's "Expense 1" / "Bonus 1". */
    fun addOneOff(inflow: Boolean) = edit { current ->
        val prefix = if (inflow) "Bonus" else "Expense"
        val number = current.expenses.count { it.oneOff && it.inflow == inflow } + 1
        expandedCategories[Category.OTHER.name] = true
        current.copy(expenses = current.expenses + DraftItem("$prefix $number", "", inflow = inflow, oneOff = true))
    }

    fun removeOneOff(index: Int) = edit { current ->
        current.copy(expenses = current.expenses.filterIndexed { position, item -> position != index || !item.oneOff })
    }

    /** Starts the month over from the config defaults and last month's remembered values. */
    fun resetMonth() {
        val today = LocalDate.now()
        edit { BudgetDraft.fromConfig(config, BudgetHistory.previousEntry(history, today), today) }
        message = "Month reset to the defaults."
    }

    /** Saves this month to history, replacing any earlier save for the same month. */
    fun saveMonth() {
        val entry = BudgetHistory.entryFor(report, LocalDate.now())
        val (updated, replaced) = BudgetHistory.upsert(history, entry)
        store.saveHistory(updated)
        history = updated
        message = "${if (replaced) "Replaced" else "Saved"} history for ${entry.monthKey}."
    }

    fun defaultChanges(): List<DefaultChange> = BudgetHistory.findDefaultChanges(config, month)

    fun saveDefaults(chosen: List<DefaultChange>) {
        if (chosen.isEmpty()) return
        val updated = chosen.fold(config) { current, change -> change.apply(current) }
        configRepository.saveConfig(updated)
        config = updated
        message = "Updated ${chosen.size} default(s)."
    }

    fun dismissMessage() {
        message = null
    }

    fun toggleCategory(category: String) {
        expandedCategories[category] = !(expandedCategories[category] ?: false)
    }

    fun isCategoryExpanded(category: String): Boolean = expandedCategories[category] ?: false
}
