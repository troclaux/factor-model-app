package com.buddy.budget.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetDraftTest {

    private val market = DraftItem("Mercado", "800", isFatura = true)
    private val refund = DraftItem("Reembolso", "50", isFatura = true, inflow = true)
    private val rent = DraftItem("Aluguel", "1200", day = 5)
    private val draft = BudgetDraft(
        monthKey = "2026-11", salary = "5000", leftover = "0", proventos = "0",
        useFaturaLump = true, faturaLump = "1000",
        expenses = listOf(market, refund, rent), debts = emptyList(), taxes = emptyList(),
    )

    @Test
    fun editingFaturaOutflowMovesLump() {
        val updated = draft.withExpenses(listOf(market.copy(amount = "850.5"), refund, rent))
        assertEquals("1050.5", updated.faturaLump)
    }

    @Test
    fun faturaInflowLowersLumpAndRemovingItRaisesIt() {
        assertEquals("980", draft.withExpenses(listOf(market, refund.copy(amount = "70"), rent)).faturaLump)
        assertEquals("1050", draft.withExpenses(listOf(market, rent)).faturaLump)
    }

    @Test
    fun addingAndMovingItemsInAndOutOfFatura() {
        assertEquals("1100", draft.withExpenses(draft.expenses + DraftItem("IA", "100", isFatura = true)).faturaLump)
        assertEquals("200", draft.withExpenses(listOf(market.copy(isFatura = false), refund, rent)).faturaLump)
    }

    @Test
    fun nonFaturaEditsAndItemizedDraftsKeepLump() {
        assertEquals("1000", draft.withExpenses(listOf(market, refund, rent.copy(amount = "1300"))).faturaLump)
        val itemized = draft.copy(useFaturaLump = false)
        assertEquals("1000", itemized.withExpenses(listOf(market.copy(amount = "900"), refund, rent)).faturaLump)
    }
}
