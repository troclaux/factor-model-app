package com.buddy.budget.portfolio.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.buddy.budget.i18n.tr

/** Brazilian risk-free rates from the Banco Central's public time series API (SGS). */
object BancoCentral {

    enum class Rate(val label: String, val series: Int) {
        /** CDI, annualized over 252 business days (% a.a.). */
        CDI("CDI", 4389),

        /** Effective Selic, annualized over 252 business days (% a.a.). */
        SELIC("Selic", 1178),
    }

    /** The rate named by a risk-free proxy such as "CDI" or "selic", or null for anything else. */
    fun rateNamed(name: String): Rate? = Rate.entries.find { it.name.equals(name.trim(), ignoreCase = true) }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /** The latest annual rate as a decimal (13.65% → 0.1365) in the 30 days before [end] (exclusive). */
    suspend fun fetchRate(rate: Rate, end: LocalDate): Double = withContext(Dispatchers.IO) {
        val url = URL(
            "https://api.bcb.gov.br/dados/serie/bcdata.sgs.${rate.series}/dados?formato=json" +
                "&dataInicial=${end.minusDays(30).format(dateFormat)}&dataFinal=${end.minusDays(1).format(dateFormat)}"
        )
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        val body = try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("${rate.label}: " + tr("Banco Central returned HTTP %d").format(connection.responseCode))
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        latestRate(body) ?: throw IllegalStateException("${rate.label}: " + tr("no rate published in the 30 days before %s").format(end))
    }

    /** Parses the SGS JSON ([{"data":"30/10/2026","valor":"13.65"}, ...]); null when it has no rows. */
    fun latestRate(json: String): Double? =
        JsonParser.parseString(json).asJsonArray.lastOrNull()?.asJsonObject?.get("valor")?.asString?.toDouble()?.div(100)
}
