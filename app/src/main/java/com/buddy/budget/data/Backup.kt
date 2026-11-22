package com.buddy.budget.data

import com.buddy.budget.model.BudgetConfig
import com.buddy.budget.model.BudgetDraft
import com.buddy.budget.model.HistoryEntry
import com.buddy.budget.portfolio.model.PortfolioSettings
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.buddy.budget.i18n.tr

/**
 * Everything worth keeping, in one JSON file: budget defaults, saved months, this month's plan and the
 * portfolio settings. The Pluggy client secret is never included.
 */
data class Backup(
    val app: String = APP,
    val version: Int = 1,
    @SerializedName("exported_at") val exportedAt: String = "",
    @SerializedName("budget_config") val budgetConfig: BudgetConfig? = null,
    @SerializedName("budget_history") val budgetHistory: List<HistoryEntry> = emptyList(),
    @SerializedName("budget_draft") val budgetDraft: BudgetDraft? = null,
    @SerializedName("portfolio_settings") val portfolioSettings: PortfolioSettings? = null,
) {
    fun toJson(): String = gson.toJson(copy(portfolioSettings = portfolioSettings?.copy(pluggyClientSecret = "")))

    companion object {
        const val APP = "buddy"
        private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        /** Throws when the file is not a Buddy backup. */
        fun fromJson(json: String): Backup {
            val backup = runCatching { gson.fromJson(json, Backup::class.java) }.getOrNull()
            require(backup != null && backup.app == APP) { tr("This file is not a Buddy backup.") }
            return backup
        }
    }
}
