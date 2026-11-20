package com.buddy.budget.portfolio.data

import android.content.Context
import com.buddy.budget.portfolio.model.PortfolioSettings
import com.google.gson.Gson

class SettingsRepository(context: Context) {

    private val gson = Gson()
    private val preferences = context.getSharedPreferences("portfolio", Context.MODE_PRIVATE)

    fun load(): PortfolioSettings {
        val json = preferences.getString(KEY, null) ?: return PortfolioSettings()
        return runCatching { gson.fromJson(json, PortfolioSettings::class.java) }.getOrNull() ?: PortfolioSettings()
    }

    fun save(settings: PortfolioSettings) {
        preferences.edit().putString(KEY, gson.toJson(settings)).apply()
    }

    private companion object {
        const val KEY = "settings"
    }
}
