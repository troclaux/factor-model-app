package com.buddy.budget.data

import android.content.Context
import com.buddy.budget.model.BudgetConfig
import com.google.gson.Gson
import java.io.File

class ConfigRepository(private val context: Context) {

    private val gson = Gson()
    private val userConfigFile = File(context.filesDir, "budget_config.json")

    fun loadConfig(): BudgetConfig {
        val json = if (userConfigFile.exists()) {
            userConfigFile.readText()
        } else {
            context.assets.open("budget_config.json").bufferedReader().use { it.readText() }
        }
        return gson.fromJson(json, BudgetConfig::class.java)
    }

    fun saveConfig(config: BudgetConfig) {
        userConfigFile.writeText(gson.toJson(config))
    }
}
