package com.buddy.budget.data

import com.buddy.budget.model.BudgetDraft
import com.buddy.budget.model.DraftItem
import com.buddy.budget.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class BudgetStoreTest {

    private val directory = Files.createTempDirectory("budget").toFile().apply { deleteOnExit() }
    private val store = BudgetStore(directory)

    @Test
    fun readsTheCliHistoryFormatIncludingOlderEntries() {
        // Same shape as budget.py's budget_history.json; the first entry predates the month/balance fields.
        directory.resolve("budget_history.json").writeText(
            """
            [
              {"date": "2026-03-25", "salary": 5000.0, "leftover": 0, "fatura_lump": 1200.5, "total": 4000.0,
               "fatura": 1200.5, "non_fatura": 1500.0, "debt": 800.0, "taxes": 499.5,
               "categories": {"housing": 1000.0, "utilities": 500.0},
               "expenses": [{"name": "Aluguel", "amount": 1000, "is_fatura": false, "day": 26, "inflow": false}],
               "debts": [{"name": "Emprestimo", "amount": 800, "day": 1, "is_fatura": false}],
               "taxes_list": [{"name": "DARF", "amount": 499.5, "day": null, "is_fatura": false}]},
              {"month": "2026-04", "date": "2026-04-20", "salary": 5100, "leftover": 1000, "proventos": 50,
               "fatura_lump": null, "total": 4200, "inflow": 0, "balance": 1950.0, "fatura": 900, "non_fatura": 1600,
               "debt": 800, "taxes": 900, "categories": {"food": 900}, "expenses": [], "debts": [], "taxes_list": []}
            ]
            """.trimIndent(),
        )
        val history = store.loadHistory()
        assertEquals(listOf("2026-03", "2026-04"), history.map { it.monthKey })
        assertEquals(1000.0, history[0].effectiveBalance, 1e-9) // 5000 + 0 − 4000
        assertEquals(1200.5, history[0].faturaLump!!, 1e-9)
        assertEquals("DARF", history[0].taxesList.single().name)
        assertNull(history[0].taxesList.single().day)
        assertNull(history[1].faturaLump)
        assertEquals(1950.0, history[1].effectiveBalance, 1e-9)

        store.saveHistory(history + HistoryEntry(month = "2026-05", date = "2026-05-02"))
        assertEquals(listOf("2026-03", "2026-04", "2026-05"), store.loadHistory().map { it.monthKey })
    }

    @Test
    fun draftRoundTripsAndMissingFilesLoadNothing() {
        assertEquals(emptyList<HistoryEntry>(), store.loadHistory())
        assertNull(store.loadDraft())
        val draft = BudgetDraft(
            "2026-11", "5000", "120,5", "0", useFaturaLump = true, faturaLump = "1500",
            expenses = listOf(DraftItem("Bonus 1", "200", inflow = true, oneOff = true)),
            debts = listOf(DraftItem("Emprestimo", "500", 1, category = "debt")),
            taxes = emptyList(),
        )
        store.saveDraft(draft)
        assertEquals(draft, store.loadDraft())
    }
}
