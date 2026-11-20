package com.buddy.budget.portfolio.data

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class PluggyTest {

    private fun investments(json: String) = JsonParser.parseString(json).asJsonArray.map { it.asJsonObject }

    @Test
    fun keepsOpenListedPositionsAndSumsAcrossItems() {
        val holdings = Pluggy.holdingsFrom(
            investments(
                """
                [
                  {"type":"EQUITY","subtype":"STOCK","code":"PETR4","quantity":100,"status":"ACTIVE"},
                  {"type":"EQUITY","subtype":"STOCK","code":"petr4","quantity":50,"status":"ACTIVE"},
                  {"type":"EQUITY","subtype":"REAL_ESTATE_FUND","code":"GGRC11","quantity":1,"status":"ACTIVE"},
                  {"type":"ETF","subtype":"ETF","code":"BOVA11","quantity":3.5,"status":null},
                  {"type":"EQUITY","subtype":"BDR","code":"AAPL34","quantity":7}
                ]
                """
            )
        )
        assertEquals(mapOf("PETR4.SA" to 150.0, "GGRC11.SA" to 1.0, "BOVA11.SA" to 3.5, "AAPL34.SA" to 7.0), holdings)
    }

    @Test
    fun dropsOptionsClosedPositionsAndNonListedAssets() {
        val holdings = Pluggy.holdingsFrom(
            investments(
                """
                [
                  {"type":"EQUITY","subtype":"OPTION","code":"PETRA350","quantity":100,"status":"ACTIVE"},
                  {"type":"EQUITY","subtype":"DERIVATIVES","code":"WINZ26","quantity":1,"status":"ACTIVE"},
                  {"type":"EQUITY","subtype":"STOCK","code":"VALE3","quantity":10,"status":"TOTAL_WITHDRAWAL"},
                  {"type":"EQUITY","subtype":"STOCK","code":"ITUB4","quantity":0,"status":"ACTIVE"},
                  {"type":"EQUITY","subtype":"STOCK","code":null,"quantity":5,"status":"ACTIVE"},
                  {"type":"FIXED_INCOME","subtype":"CDB","code":null,"quantity":1,"status":"ACTIVE"},
                  {"type":"MUTUAL_FUND","subtype":"STOCK_FUND","code":"12.345.678/0001-90","quantity":200,"status":"ACTIVE"}
                ]
                """
            )
        )
        assertEquals(emptyMap<String, Double>(), holdings)
    }
}
