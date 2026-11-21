package com.buddy.budget.data

import android.content.Context
import com.buddy.budget.portfolio.data.SettingsRepository
import java.time.LocalDateTime

/** Collects every store into a [Backup] and writes a backup back into them. */
class BackupManager(context: Context) {

    private val configRepository = ConfigRepository(context)
    private val budgetStore = BudgetStore(context.filesDir)
    private val settingsRepository = SettingsRepository(context)

    fun create(): Backup = Backup(
        exportedAt = LocalDateTime.now().withNano(0).toString(),
        budgetConfig = configRepository.loadConfig(),
        budgetHistory = budgetStore.loadHistory(),
        budgetDraft = budgetStore.loadDraft(),
        portfolioSettings = settingsRepository.load(),
    )

    /** Replaces the stored data with the backup's. The Pluggy secret already on this phone is kept. */
    fun restore(backup: Backup) {
        backup.budgetConfig?.let { configRepository.saveConfig(it) }
        budgetStore.saveHistory(backup.budgetHistory)
        backup.budgetDraft?.let { budgetStore.saveDraft(it) }
        backup.portfolioSettings?.let { settings ->
            settingsRepository.save(settings.copy(pluggyClientSecret = settingsRepository.load().pluggyClientSecret))
        }
    }
}
