package com.buddy.budget.portfolio.model

import java.time.LocalDate

enum class LookbackUnit(val label: String, val days: Double) {
    DAYS("Days", 1.0),
    WEEKS("Weeks", 7.0),
    MONTHS("Months", 365.0 / 12),
    YEARS("Years", 365.0),
}

enum class Frequency(val label: String, val periodsPerYear: Int) {
    DAILY("Daily", 252),
    WEEKLY("Weekly", 52),
    MONTHLY("Monthly", 12),
}

/** How the price window is chosen: a lookback ending at the previous month's end, or explicit dates. */
enum class PeriodMode(val label: String) {
    LOOKBACK("Lookback"),
    DATES("Dates"),
}

enum class InputMode(val label: String) {
    SHARES("Shares"),
    WEIGHTS("Weights %"),
}

/** A user portfolio: one text value per ticker, read as share counts or weight percentages. */
data class PortfolioInput(
    val mode: InputMode = InputMode.SHARES,
    val values: Map<String, String> = emptyMap(),
)

/** Everything the user can set. Numbers are kept as typed so the fields round-trip exactly. */
data class PortfolioSettings(
    val tickers: List<String> = listOf(
        "EGIE3.SA", "FRAS3.SA", "ITUB3.SA", "LEVE3.SA", "PETR3.SA", "PNVL3.SA", "PSSA3.SA", "RADL3.SA",
        "RANI3.SA", "SAPR3.SA", "SAUD3.SA", "SBSP3.SA", "TOTS3.SA", "UGPA3.SA", "VALE3.SA", "WEGE3.SA",
    ),
    val marketProxy: String = "^BVSP",
    val riskFreePercent: String = "9",
    /** Yield ticker (e.g. ^IRX) to fetch the risk-free rate from; overrides riskFreePercent when set. */
    val riskFreeProxy: String = "",
    val periodMode: PeriodMode = PeriodMode.LOOKBACK,
    val lookbackAmount: String = "53",
    val lookbackUnit: LookbackUnit = LookbackUnit.WEEKS,
    /** ISO dates (yyyy-MM-dd) used when periodMode is DATES. */
    val startDate: String = "",
    val endDate: String = "",
    /** In DATES mode, ignore endDate and use the most recent close. */
    val endLatest: Boolean = true,
    val frequency: Frequency = Frequency.DAILY,
    val allowShort: Boolean = false,
    val riskAversion: String = "",
    val capital: String = "",
    val current: PortfolioInput = PortfolioInput(),
    val hypothetical: PortfolioInput = PortfolioInput(),
    /** Pluggy (Open Finance) credentials and the item ids of your broker connections. Stored on the device only. */
    val pluggyClientId: String = "",
    val pluggyClientSecret: String = "",
    val pluggyItemIds: String = "",
)

fun parseNumber(text: String): Double? = text.trim().replace(",", ".").toDoubleOrNull()

fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim()) }.getOrNull()
