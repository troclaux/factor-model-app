package com.buddy.budget.portfolio.data

import android.content.Context
import com.buddy.budget.portfolio.model.PortfolioSettings
import com.google.gson.Gson

/** Portfolio settings in shared preferences; the Pluggy client secret is kept encrypted in [SecretStore]. */
class SettingsRepository(context: Context) {

    private val gson = Gson()
    private val preferences = context.getSharedPreferences("portfolio", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)
    private var savedSecret: String? = null

    fun load(): PortfolioSettings {
        val json = preferences.getString(KEY, null) ?: return PortfolioSettings(pluggyClientSecret = secrets.load(PLUGGY_SECRET))
        val stored = runCatching { gson.fromJson(json, PortfolioSettings::class.java) }.getOrNull() ?: PortfolioSettings()
        // Earlier versions saved the secret in plain text with the settings: move it to the encrypted store.
        if (stored.pluggyClientSecret.isNotEmpty()) {
            save(stored)
            return stored
        }
        return stored.copy(pluggyClientSecret = secrets.load(PLUGGY_SECRET).also { savedSecret = it })
    }

    fun save(settings: PortfolioSettings) {
        preferences.edit().putString(KEY, gson.toJson(settings.copy(pluggyClientSecret = ""))).apply()
        // Settings are saved on every keystroke; only re-encrypt when the secret itself changed.
        if (settings.pluggyClientSecret != savedSecret) {
            secrets.save(PLUGGY_SECRET, settings.pluggyClientSecret)
            savedSecret = settings.pluggyClientSecret
        }
    }

    private companion object {
        const val KEY = "settings"
        const val PLUGGY_SECRET = "pluggy_client_secret"
    }
}
