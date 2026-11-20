package com.buddy.budget.portfolio

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.buddy.budget.portfolio.data.Pluggy
import com.buddy.budget.portfolio.data.PriceTable
import com.buddy.budget.portfolio.data.SettingsRepository
import com.buddy.budget.portfolio.data.YahooFinance
import com.buddy.budget.portfolio.model.Analysis
import com.buddy.budget.portfolio.model.AnalysisResult
import com.buddy.budget.portfolio.model.InputMode
import com.buddy.budget.portfolio.model.PortfolioInput
import com.buddy.budget.portfolio.model.PortfolioSettings
import com.buddy.budget.portfolio.model.parseNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToLong

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    var settings by mutableStateOf(repository.load())
        private set
    var result by mutableStateOf<AnalysisResult?>(null)
        private set
    /** The settings the current result was computed with, for reporting the inputs. */
    var analyzedSettings by mutableStateOf<PortfolioSettings?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var importing by mutableStateOf(false)
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

    fun dismissMessage() {
        message = null
    }

    /** Replaces "My portfolio" with the share counts reported through Open Finance and adds any new tickers. */
    fun importHoldings() {
        val current = settings
        val itemIds = current.pluggyItemIds.split(Regex("[\\s,;]+")).filter { it.isNotEmpty() }
        val validation = when {
            current.pluggyClientId.isBlank() || current.pluggyClientSecret.isBlank() -> "Enter your Pluggy client ID and secret."
            itemIds.isEmpty() -> "Enter at least one Pluggy item ID."
            else -> null
        }
        if (validation != null) {
            error = validation
            return
        }

        importing = true
        error = null
        viewModelScope.launch {
            try {
                val holdings = Pluggy.fetchHoldings(current.pluggyClientId.trim(), current.pluggyClientSecret.trim(), itemIds)
                if (holdings.isEmpty()) {
                    error = "No stock, FII, BDR or ETF positions found in these connections."
                } else {
                    val added = holdings.keys - current.tickers.toSet()
                    update {
                        it.copy(
                            tickers = (it.tickers + holdings.keys).distinct(),
                            current = PortfolioInput(InputMode.SHARES, holdings.mapValues { (_, quantity) -> quantity.toBigDecimal().stripTrailingZeros().toPlainString() }),
                        )
                    }
                    message = "Imported ${holdings.size} positions" + if (added.isEmpty()) "." else ", added ${added.joinToString()}."
                }
            } catch (exception: Exception) {
                error = exception.message ?: "Import failed"
            } finally {
                importing = false
            }
        }
    }

    fun analyze() {
        val current = settings
        val tickers = current.tickers
        val riskFreeProxy = current.riskFreeProxy.trim().uppercase().ifEmpty { null }
        val typedRiskFreeRate = parseNumber(current.riskFreePercent)?.div(100)
        val lookbackDays = parseNumber(current.lookbackAmount)?.times(current.lookbackUnit.days)?.roundToLong()
        val validation = when {
            tickers.size < 2 -> "Add at least two tickers."
            riskFreeProxy == null && typedRiskFreeRate == null -> "Enter a valid risk-free rate."
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
                val riskFreeRate = riskFreeProxy?.let { YahooFinance.fetchRiskFreeRate(it) } ?: typedRiskFreeRate!!
                // Ends on the last trading day of the previous month, as in the CLI.
                val end = LocalDate.now().withDayOfMonth(1)
                val prices = cachedPrices.takeIf { cachedKey == key }
                    ?: YahooFinance.fetchPrices(fetchTickers, end.minusDays(lookbackDays!!), end, current.frequency)
                cachedPrices = prices
                cachedKey = key
                result = withContext(Dispatchers.Default) {
                    Analysis.run(
                        dates = prices.dates,
                        prices = prices.prices,
                        tickers = tickers,
                        marketProxy = marketProxy,
                        riskFreeRate = riskFreeRate,
                        riskFreeSource = riskFreeProxy?.let { "fetched from $it" } ?: "entered",
                        allowShort = current.allowShort,
                        frequency = current.frequency,
                        current = current.current,
                        hypothetical = current.hypothetical,
                    )
                }
                analyzedSettings = current
                selectedTab = 1
            } catch (exception: Exception) {
                error = exception.message ?: "Analysis failed"
            } finally {
                loading = false
            }
        }
    }
}
