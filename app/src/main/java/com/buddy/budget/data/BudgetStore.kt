package com.buddy.budget.data

import com.buddy.budget.model.BudgetDraft
import com.buddy.budget.model.HistoryEntry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Saved months (budget_history.json, same format as budget.py) and the month being edited.
 * Both live in the app's private files directory.
 */
class BudgetStore(directory: File) {

    private val historyFile = File(directory, "budget_history.json")
    private val draftFile = File(directory, "budget_draft.json")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun loadHistory(): List<HistoryEntry> = runCatching {
        gson.fromJson<List<HistoryEntry>>(historyFile.readText(), object : TypeToken<List<HistoryEntry>>() {}.type)
    }.getOrNull() ?: emptyList()

    fun saveHistory(history: List<HistoryEntry>) = historyFile.writeText(gson.toJson(history))

    /** Null when there is no draft or it cannot be read. */
    fun loadDraft(): BudgetDraft? = runCatching { gson.fromJson(draftFile.readText(), BudgetDraft::class.java) }.getOrNull()

    fun saveDraft(draft: BudgetDraft) = draftFile.writeText(gson.toJson(draft))
}
