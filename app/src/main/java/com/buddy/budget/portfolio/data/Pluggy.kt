package com.buddy.budget.portfolio.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Reads stock holdings from Open Finance through Pluggy (https://docs.pluggy.ai).
 *
 * For personal use, connect your broker in MeuPluggy and link it to the Pluggy dashboard's demo
 * application: its clientId/clientSecret plus the item id of that connection are all this needs.
 */
object Pluggy {

    private const val BASE_URL = "https://api.pluggy.ai"

    /** Kept: listed stocks, FIIs, BDRs and ETFs. Options and derivatives are not portfolio assets here. */
    private val EXCLUDED_SUBTYPES = setOf("DERIVATIVES", "OPTION")

    /** Total shares per Yahoo ticker across all the given items. */
    suspend fun fetchHoldings(clientId: String, clientSecret: String, itemIds: List<String>): Map<String, Double> =
        withContext(Dispatchers.IO) {
            val auth = JsonObject().apply {
                addProperty("clientId", clientId)
                addProperty("clientSecret", clientSecret)
            }
            val apiKey = request("POST", "/auth", apiKey = null, body = auth).get("apiKey").asString
            val investments = itemIds.flatMap { itemId ->
                val results = mutableListOf<JsonObject>()
                var page = 1
                do {
                    val response = request("GET", "/investments?itemId=${URLEncoder.encode(itemId, "UTF-8")}&page=$page", apiKey)
                    response.getAsJsonArray("results").forEach { results += it.asJsonObject }
                    val totalPages = response.get("totalPages")?.asInt ?: 1
                    page++
                } while (page <= totalPages)
                results
            }
            holdingsFrom(investments)
        }

    /** Sums open listed positions by ticker. B3 codes (e.g. PETR4) get the .SA suffix Yahoo needs. */
    fun holdingsFrom(investments: List<JsonObject>): Map<String, Double> {
        val holdings = linkedMapOf<String, Double>()
        for (investment in investments) {
            val type = investment.string("type")
            val subtype = investment.string("subtype")
            val status = investment.string("status")
            val code = investment.string("code")?.trim()?.uppercase()
            val quantity = investment.get("quantity")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0
            if (type != "EQUITY" && type != "ETF") continue
            if (subtype in EXCLUDED_SUBTYPES) continue
            if (status != null && status != "ACTIVE") continue
            if (code.isNullOrEmpty() || quantity <= 0.0) continue
            val ticker = if ("." in code) code else "$code.SA"
            holdings[ticker] = (holdings[ticker] ?: 0.0) + quantity
        }
        return holdings
    }

    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { !it.isJsonNull }?.asString

    private fun request(method: String, path: String, apiKey: String?, body: JsonObject? = null): JsonObject {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        apiKey?.let { connection.setRequestProperty("X-API-KEY", it) }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        try {
            val success = connection.responseCode in 200..299
            val text = (if (success) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            if (!success) {
                val message = json?.get("message")?.takeIf { !it.isJsonNull }?.asString ?: "HTTP ${connection.responseCode}"
                throw IllegalStateException("Pluggy: $message")
            }
            return json ?: throw IllegalStateException("Pluggy: unexpected response")
        } finally {
            connection.disconnect()
        }
    }
}
