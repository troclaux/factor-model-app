package com.buddy.budget.portfolio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BancoCentralTest {

    @Test
    fun takesTheLastPublishedRateAsADecimal() {
        val json = """[{"data":"29/10/2026","valor":"13.40"},{"data":"30/10/2026","valor":"13.65"}]"""
        assertEquals(0.1365, BancoCentral.latestRate(json)!!, 1e-12)
        assertNull(BancoCentral.latestRate("[]"))
    }

    @Test
    fun recognizesCdiAndSelicProxies() {
        assertEquals(BancoCentral.Rate.CDI, BancoCentral.rateNamed(" cdi "))
        assertEquals(BancoCentral.Rate.SELIC, BancoCentral.rateNamed("SELIC"))
        assertNull(BancoCentral.rateNamed("^IRX"))
    }
}
