package com.buddy.budget.portfolio.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Aligned adjusted closes: one row per date, one column per ticker. */
data class PriceTable(
    val tickers: List<String>,
    val dates: List<LocalDate>,
    val prices: Array<DoubleArray>,
)

object YahooFinance {

    /**
     * Fetches adjusted closes and aligns them by date.
     *
     * The window ends on the first day of the current month (exclusive), so the last row is the
     * last trading day of the previous month, as in the CLI.
     */
    suspend fun fetchPrices(tickers: List<String>, lookbackDays: Long, monthly: Boolean): PriceTable {
        val end = LocalDate.now().withDayOfMonth(1)
        val start = end.minusDays(lookbackDays)
        val series = coroutineScope {
            tickers.map { ticker -> async(Dispatchers.IO) { ticker to fetchSeries(ticker, start, end, monthly) } }.awaitAll()
        }.toMap()
        return align(tickers, series)
    }

    /**
     * Latest yield of a Yahoo yield ticker (e.g. ^IRX, ^TNX) as a decimal, from the 30 days before
     * the first of the current month. Yahoo quotes these in percent (4.25 = 4.25%).
     */
    suspend fun fetchRiskFreeRate(ticker: String): Double = withContext(Dispatchers.IO) {
        val end = LocalDate.now().withDayOfMonth(1)
        fetchSeries(ticker, end.minusDays(30), end, monthly = false).values.last() / 100.0
    }

    private fun fetchSeries(ticker: String, start: LocalDate, end: LocalDate, monthly: Boolean): Map<LocalDate, Double> {
        val period1 = start.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val period2 = end.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        val interval = if (monthly) "1mo" else "1d"
        val url = URL(
            "https://query2.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(ticker, "UTF-8")}" +
                "?period1=$period1&period2=$period2&interval=$interval&events=div,splits"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        val body = try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        } finally {
            connection.disconnect()
        }

        val chart = runCatching { JsonParser.parseString(body).asJsonObject.getAsJsonObject("chart") }.getOrNull()
            ?: throw IllegalStateException("$ticker: unexpected response from Yahoo Finance")
        val result = chart.get("result")?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()?.asJsonObject
        if (result == null) {
            val description = chart.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.get("description")?.asString
            throw IllegalStateException("$ticker: ${description ?: "no data returned"}")
        }
        val timestamps = result.getAsJsonArray("timestamp")
            ?: throw IllegalStateException("$ticker: no data in the selected window")
        val closes = result.getAsJsonObject("indicators").getAsJsonArray("adjclose")[0].asJsonObject.getAsJsonArray("adjclose")
        val offsetSeconds = result.getAsJsonObject("meta").get("gmtoffset")?.asInt ?: 0

        val series = sortedMapOf<LocalDate, Double>()
        for (index in 0 until timestamps.size()) {
            val close = closes[index]
            if (close.isJsonNull) continue
            val date = Instant.ofEpochSecond(timestamps[index].asLong + offsetSeconds).atZone(ZoneOffset.UTC).toLocalDate()
            if (date < end) series[date] = close.asDouble
        }
        if (series.isEmpty()) throw IllegalStateException("$ticker: no data in the selected window")
        return series
    }

    /** Outer-join on date, forward-fill single gaps, then drop rows still missing a price. */
    private fun align(tickers: List<String>, series: Map<String, Map<LocalDate, Double>>): PriceTable {
        val allDates = series.values.flatMap { it.keys }.toSortedSet().toList()
        val filled = tickers.map { ticker ->
            val values = series.getValue(ticker)
            val column = arrayOfNulls<Double>(allDates.size)
            allDates.forEachIndexed { row, date ->
                column[row] = values[date] ?: if (row > 0 && values.containsKey(allDates[row - 1])) column[row - 1] else null
            }
            column
        }
        val keptRows = allDates.indices.filter { row -> filled.all { it[row] != null } }
        if (keptRows.size < 3) {
            throw IllegalStateException("Not enough overlapping data: got ${keptRows.size} dates. Try a longer lookback.")
        }
        return PriceTable(
            tickers = tickers,
            dates = keptRows.map { allDates[it] },
            prices = Array(keptRows.size) { row -> DoubleArray(tickers.size) { col -> filled[col][keptRows[row]]!! } },
        )
    }
}
