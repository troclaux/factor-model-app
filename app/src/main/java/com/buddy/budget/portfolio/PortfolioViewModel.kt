package com.buddy.budget.portfolio

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.budget.portfolio.data.PriceTable
import com.buddy.budget.portfolio.data.SettingsRepository
import com.buddy.budget.portfolio.data.YahooFinance
import com.buddy.budget.portfolio.model.Analysis
import com.buddy.budget.portfolio.model.AnalysisResult
import com.buddy.budget.portfolio.model.Frequency
import com.buddy.budget.portfolio.model.PortfolioSettings
import com.buddy.budget.portfolio.model.parseNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    var settings by mutableStateOf(repository.load())
        private set
    var result by mutableStateOf<AnalysisResult?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var selectedTab by mutableStateOf(0)

    /** Prices are only re-downloaded when the tickers, window or frequency change. */
    private var cachedKey: String? = null
    private var cachedPrices: PriceTable? = null

    fun update(transform: (PortfolioSettings) -> PortfolioSettings) {
        settings = transform(settings)
        repository.save(settings)
    }

    /** Accepts one or many tickers separated by spaces, commas or new lines. */
    fun addTickers(text: String) = update { current ->
        val added = text.split(Regex("[\\s,;]+")).map { it.trim().uppercase() }.filter { it.isNotEmpty() }
        current.copy(tickers = (current.tickers + added).distinct())
    }

    fun removeTicker(ticker: String) = update { it.copy(tickers = it.tickers - ticker) }

    fun dismissError() {
        error = null
    }

    fun analyze() {
        val current = settings
        val tickers = current.tickers
        val riskFreeRate = parseNumber(current.riskFreePercent)?.div(100)
        val lookbackDays = parseNumber(current.lookbackAmount)?.times(current.lookbackUnit.days)?.roundToLong()
        val validation = when {
            tickers.size < 2 -> "Add at least two tickers."
            riskFreeRate == null -> "Enter a valid risk-free rate."
            lookbackDays == null || lookbackDays <= 0 -> "Enter a positive lookback."
            else -> null
        }
        if (validation != null) {
            error = validation
            return
        }

        val marketProxy = current.marketProxy.trim().uppercase().ifEmpty { null }
        val fetchTickers = (tickers + listOfNotNull(marketProxy)).distinct()
        val key = listOf(fetchTickers, lookbackDays, current.frequency).toString()

        loading = true
        error = null
        viewModelScope.launch {
            try {
                val prices = cachedPrices.takeIf { cachedKey == key }
                    ?: YahooFinance.fetchPrices(fetchTickers, lookbackDays!!, current.frequency == Frequency.MONTHLY)
                cachedPrices = prices
                cachedKey = key
                result = withContext(Dispatchers.Default) {
                    Analysis.run(
                        dates = prices.dates,
                        prices = prices.prices,
                        tickers = tickers,
                        marketProxy = marketProxy,
                        riskFreeRate = riskFreeRate!!,
                        allowShort = current.allowShort,
                        frequency = current.frequency,
                        current = current.current,
                        hypothetical = current.hypothetical,
                    )
                }
                selectedTab = 1
            } catch (exception: Exception) {
                error = exception.message ?: "Analysis failed"
            } finally {
                loading = false
            }
        }
    }
}
