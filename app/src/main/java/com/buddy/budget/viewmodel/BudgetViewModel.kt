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
import com.buddy.budget.model.DebtTaxItem
import com.buddy.budget.model.DefaultChange
import com.buddy.budget.model.DraftItem
import com.buddy.budget.model.ExpenseItem
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

    /**
     * Outflow expenses grouped by category in the CLI's category order. Fatura items stay listed (and
     * editable) while a lump sum is used, though the lump sum replaces them in the totals.
     */
    val expensesByCategory by derivedStateOf {
        draft.expenses.withIndex()
            .filter { !it.value.inflow }
            .groupBy { Category.fromString(it.value.category) }
            .toSortedMap(compareBy { it.ordinal })
    }

    /** Inflow expenses (recurring inflows and one-off bonuses), shown in their own group. */
    val inflows by derivedStateOf { draft.expenses.withIndex().filter { it.value.inflow } }

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

    /** The recurring item's default amount as text, or null when it is not in the defaults. */
    fun defaultAmount(section: Section, name: String): String? = when (section) {
        Section.EXPENSES -> config.expenses.find { it.name == name }?.amount
        Section.DEBTS -> config.debts.find { it.name == name }?.amount
        Section.TAXES -> config.taxes.find { it.name == name }?.amount
    }?.let(BudgetDraft::format)

    /**
     * Updates this month's item. With [defaultAmount] (from the edit dialog) a recurring item's defaults
     * (matched by its old name) also get its name, category, day, fatura flag and that amount.
     */
    fun updateItem(section: Section, index: Int, item: DraftItem, defaultAmount: String? = null) {
        val old = items(section).getOrNull(index) ?: return
        if (!old.oneOff && defaultAmount != null) {
            val amount = BudgetDraft.parseAmount(defaultAmount)
            updateConfig { config ->
                when (section) {
                    Section.EXPENSES -> config.copy(expenses = config.expenses.map {
                        if (it.name == old.name) it.copy(name = item.name, amount = amount, day = item.day, category = item.category, isFatura = item.isFatura) else it
                    })
                    Section.DEBTS -> config.copy(debts = config.debts.map {
                        if (it.name == old.name) it.copy(name = item.name, amount = amount, day = item.day, isFatura = item.isFatura) else it
                    })
                    Section.TAXES -> config.copy(taxes = config.taxes.map {
                        if (it.name == old.name) it.copy(name = item.name, amount = amount, day = item.day, isFatura = item.isFatura) else it
                    })
                }
            }
        }
        edit { current -> current.withSection(section) { list -> list.mapIndexed { position, existing -> if (position == index) item else existing } } }
    }

    /** True when another item of the section already has this name (names identify items in the defaults). */
    fun nameTaken(section: Section, name: String, exceptIndex: Int?): Boolean =
        items(section).withIndex().any { (index, item) -> index != exceptIndex && item.name.equals(name.trim(), ignoreCase = true) }

    /** True when any item of this month has this name. */
    fun nameTakenAnywhere(name: String): Boolean = Section.entries.any { nameTaken(it, name, exceptIndex = null) }

    /** Adds a recurring inflow or outflow to the defaults and to this month; Debt and Taxes outflows go to their own sections. */
    fun addRecurring(item: DraftItem) {
        val section = when {
            item.inflow -> Section.EXPENSES
            item.category == "debt" -> Section.DEBTS
            item.category == "taxes" -> Section.TAXES
            else -> Section.EXPENSES
        }
        val added = item.copy(name = item.name.trim(), oneOff = false)
        val amount = BudgetDraft.parseAmount(added.amount)
        updateConfig { config ->
            when (section) {
                Section.EXPENSES -> config.copy(expenses = config.expenses + ExpenseItem(added.name, amount, added.day, added.category, added.isFatura, added.inflow))
                Section.DEBTS -> config.copy(debts = config.debts + DebtTaxItem(added.name, amount, added.day, added.isFatura))
                Section.TAXES -> config.copy(taxes = config.taxes + DebtTaxItem(added.name, amount, added.day, added.isFatura))
            }
        }
        edit { current -> current.withSection(section) { it + added } }
        expandedCategories[if (added.inflow) INFLOWS else if (section == Section.EXPENSES) Category.fromString(added.category).name else section.name] = true
        message = "Added ${added.name} to your defaults."
    }

    /** Deletes a recurring item from the defaults and from this month. */
    fun removeRecurring(section: Section, index: Int) {
        val item = items(section).getOrNull(index) ?: return
        updateConfig { config ->
            when (section) {
                Section.EXPENSES -> config.copy(expenses = config.expenses.filter { it.name != item.name })
                Section.DEBTS -> config.copy(debts = config.debts.filter { it.name != item.name })
                Section.TAXES -> config.copy(taxes = config.taxes.filter { it.name != item.name })
            }
        }
        edit { current -> current.withSection(section) { list -> list.filterIndexed { position, _ -> position != index } } }
        message = "Deleted ${item.name} from your defaults."
    }

    private fun BudgetDraft.withSection(section: Section, transform: (List<DraftItem>) -> List<DraftItem>) = when (section) {
        Section.EXPENSES -> copy(expenses = transform(expenses))
        Section.DEBTS -> copy(debts = transform(debts))
        Section.TAXES -> copy(taxes = transform(taxes))
    }

    private fun updateConfig(transform: (BudgetConfig) -> BudgetConfig) {
        config = transform(config)
        configRepository.saveConfig(config)
    }

    /** Reads everything again from storage, e.g. after restoring a backup. */
    fun reload() {
        config = configRepository.loadConfig()
        history = store.loadHistory()
        draft = loadOrStartDraft()
    }

    /** Adds a one-off expense or bonus for this month, named like budget.py's "Expense 1" / "Bonus 1". */
    fun addOneOff(inflow: Boolean) = edit { current ->
        val prefix = if (inflow) "Bonus" else "Expense"
        val number = current.expenses.count { it.oneOff && it.inflow == inflow } + 1
        expandedCategories[if (inflow) INFLOWS else Category.OTHER.name] = true
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
        updateConfig { current -> chosen.fold(current) { updated, change -> change.apply(updated) } }
        message = "Updated ${chosen.size} default(s)."
    }

    fun dismissMessage() {
        message = null
    }

    fun toggleCategory(category: String) {
        expandedCategories[category] = !(expandedCategories[category] ?: false)
    }

    fun isCategoryExpanded(category: String): Boolean = expandedCategories[category] ?: false

    companion object {
        /** Expansion key of the Inflows group. */
        const val INFLOWS = "INFLOWS"
    }
}
