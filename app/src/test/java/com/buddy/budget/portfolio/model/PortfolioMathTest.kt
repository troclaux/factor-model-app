package com.buddy.budget.portfolio.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reference values come from the original Python CLI (capm/calc.py) on the same prices. */
class PortfolioMathTest {

    private val riskFreeRate = 0.03
    private val returns = PortfolioMath.simpleReturns(PRICES)
    private val assetReturns = Array(returns.size) { row -> returns[row].copyOfRange(0, 4) }
    private val marketReturns = PortfolioMath.column(returns, 4)
    private val estimates = PortfolioMath.estimateParameters(assetReturns, 12)
    private val expected = estimates.first
    private val cov = estimates.second

    private fun flat(matrix: Matrix) = matrix.flatMap { it.toList() }.toDoubleArray()

    @Test
    fun estimatesMatchPandas() {
        assertArrayEquals(MU, expected, 1e-12)
        assertArrayEquals(COV_FLAT, flat(cov), 1e-12)
        assertArrayEquals(CORR_FLAT, flat(PortfolioMath.correlationMatrix(assetReturns)), 1e-12)
    }

    @Test
    fun tangencyWithShortsMatchesClosedForm() {
        val weights = PortfolioMath.tangencyWeights(expected, cov, riskFreeRate)
        assertArrayEquals(TANGENCY, weights, 1e-10)
        val stats = PortfolioMath.statistics(weights, expected, cov, riskFreeRate)
        assertArrayEquals(STATS, doubleArrayOf(stats.expectedReturn, stats.volatility, stats.sharpeRatio), 1e-10)
        assertArrayEquals(PORT_BETAS, PortfolioMath.betasToPortfolio(cov, weights), 1e-10)
        val allocation = PortfolioMath.cmlAllocation(2.0, riskFreeRate, stats)
        assertArrayEquals(
            CML_A2,
            doubleArrayOf(allocation.tangencyWeight, allocation.riskFreeWeight, allocation.expectedReturn, allocation.volatility),
            1e-10,
        )
    }

    @Test
    fun tangencyRatiosAreConstantOnlyForTangency() {
        val weights = PortfolioMath.tangencyWeights(expected, cov, riskFreeRate)
        val stats = PortfolioMath.statistics(weights, expected, cov, riskFreeRate)
        val ratios = PortfolioMath.tangencyRatios(weights, expected, cov, riskFreeRate)
        // For the tangency portfolio every ratio equals (E[r_T] − rf) / σ_T².
        val reference = (stats.expectedReturn - riskFreeRate) / (stats.volatility * stats.volatility)
        ratios.forEach { assertEquals(reference, it, 1e-8) }
        val equalWeights = DoubleArray(4) { 0.25 }
        val otherRatios = PortfolioMath.tangencyRatios(equalWeights, expected, cov, riskFreeRate)
        assertTrue(otherRatios.max() - otherRatios.min() > 1e-8)
    }

    @Test
    fun longOnlyTangencyMatchesScipyAndIsNotBeaten() {
        val weights = PortfolioMath.tangencyWeightsLongOnly(expected, cov, riskFreeRate)
        // Exact optimum: the shorts-allowed tangency of the two assets left in the solution (A, B).
        assertArrayEquals(doubleArrayOf(0.030860965201595755, 0.9691390347984042, 0.0, 0.0), weights, 1e-10)
        // scipy's SLSQP stops slightly short of the optimum.
        assertArrayEquals(TANGENCY_LONG, weights, 1e-3)
        val sharpe = PortfolioMath.statistics(weights, expected, cov, riskFreeRate).sharpeRatio
        val scipySharpe = PortfolioMath.statistics(TANGENCY_LONG, expected, cov, riskFreeRate).sharpeRatio
        assertTrue(sharpe >= scipySharpe - 1e-12)
    }

    @Test
    fun minVarianceReturnAndBetas() {
        assertEquals(MV_RETURN, PortfolioMath.minVarianceReturn(expected, cov), 1e-12)
        val betas = PortfolioMath.marketBetas(assetReturns, marketReturns)
        assertArrayEquals(MARKET_BETAS, betas, 1e-12)
        assertArrayEquals(ADJ_BETAS, PortfolioMath.bloombergAdjust(betas), 1e-12)
    }

    @Test
    fun frontierWithShortsMatchesScipy() {
        val frontier = PortfolioMath.frontier(expected, cov, allowShort = true, points = 5)
        assertArrayEquals(FRONTIER_SHORT_RET, frontier.returns, 1e-12)
        assertArrayEquals(FRONTIER_SHORT_VOL, frontier.volatilities, 1e-6)
    }

    @Test
    fun longOnlyFrontierIsAtLeastAsGoodAsScipy() {
        val frontier = PortfolioMath.frontier(expected, cov, allowShort = false)
        for (index in FRONTIER_LONG_RET.indices) {
            // Interpolate our curve at scipy's target return on the efficient (upper) and inefficient branches.
            val target = FRONTIER_LONG_RET[index]
            val nearest = frontier.returns.indices.minBy { kotlin.math.abs(frontier.returns[it] - target) }
            assertEquals("return $target", target, frontier.returns[nearest], 0.01)
            assertEquals("vol at $target", FRONTIER_LONG_VOL[index], frontier.volatilities[nearest], 0.01)
        }
    }

    @Test
    fun capitalAllocationRoundsSharesDown() {
        val allocation = PortfolioMath.capitalAllocation(10_000.0, 0.8, TANGENCY_LONG, PRICES.last().copyOfRange(0, 4))
        assertArrayEquals(CAPITAL_SHARES, allocation.shares, 0.0)
        assertEquals(CAPITAL_LEFTOVER, allocation.leftover, 1e-6)
        assertEquals(2_000.0, allocation.riskFreeAmount, 1e-9)
    }

    @Test
    fun portfolioBetaIsWeightedSumOfAssetBetas() {
        val weights = doubleArrayOf(0.5, 0.3, 0.2, 0.0)
        val assetBetas = PortfolioMath.marketBetas(assetReturns, marketReturns)
        val portfolioSeries = Array(assetReturns.size) { row -> doubleArrayOf(PortfolioMath.dot(assetReturns[row], weights)) }
        val directBeta = PortfolioMath.marketBetas(portfolioSeries, marketReturns)[0]
        assertEquals(directBeta, PortfolioMath.dot(weights, assetBetas), 1e-12)
    }

    @Test
    fun simplexProjection() {
        assertArrayEquals(doubleArrayOf(0.5, 0.5, 0.0), PortfolioMath.projectOntoSimplex(doubleArrayOf(1.0, 1.0, -1.0)), 1e-12)
        assertArrayEquals(doubleArrayOf(0.2, 0.3, 0.5), PortfolioMath.projectOntoSimplex(doubleArrayOf(0.2, 0.3, 0.5)), 1e-12)
    }

    companion object {
        val PRICES = arrayOf(doubleArrayOf(100.0, 100.0, 100.0, 100.0, 100.0), doubleArrayOf(103.2978, 101.1669, 103.531, 98.9631, 101.2049), doubleArrayOf(108.1578, 103.3214, 104.7737, 103.3635, 103.6288), doubleArrayOf(105.8425, 105.4573, 106.3453, 108.6718, 103.736), doubleArrayOf(104.3155, 105.0787, 95.5887, 101.9769, 101.2853), doubleArrayOf(109.1893, 105.6401, 91.5607, 101.4608, 100.6587), doubleArrayOf(101.723, 102.906, 92.7732, 97.2609, 97.8739), doubleArrayOf(107.6747, 105.1096, 85.9578, 94.4419, 99.2838), doubleArrayOf(118.7513, 109.6812, 96.3912, 104.0955, 105.7977), doubleArrayOf(116.9417, 113.1591, 89.2366, 103.9747, 104.9843), doubleArrayOf(123.6481, 115.2379, 93.3683, 103.5632, 103.6385), doubleArrayOf(133.2851, 118.8648, 93.9434, 105.6535, 106.9128), doubleArrayOf(134.9085, 123.8729, 101.7029, 110.9939, 109.722), doubleArrayOf(139.9429, 125.8465, 105.8872, 112.066, 111.5013), doubleArrayOf(139.7352, 128.2264, 97.465, 109.3733, 108.6894), doubleArrayOf(142.8675, 130.7128, 106.4518, 107.9148, 109.8665), doubleArrayOf(155.1888, 137.0595, 120.0687, 109.5671, 114.2405), doubleArrayOf(148.6251, 131.8678, 117.5614, 109.7994, 109.4689), doubleArrayOf(152.4628, 133.9976, 117.6355, 108.2459, 108.7787), doubleArrayOf(148.4824, 126.0658, 112.2669, 102.4328, 101.8116), doubleArrayOf(139.9111, 119.579, 105.039, 98.6885, 97.7817), doubleArrayOf(131.5058, 115.9808, 107.0796, 91.4773, 91.7516), doubleArrayOf(131.9629, 115.5817, 106.5648, 91.0763, 91.9898), doubleArrayOf(127.6755, 114.7798, 104.7119, 89.6496, 88.43), doubleArrayOf(128.7084, 122.9987, 104.8062, 91.8083, 90.4507), doubleArrayOf(131.3305, 123.9, 105.3448, 93.9708, 92.1032), doubleArrayOf(133.1683, 124.3008, 101.1504, 95.2527, 92.5197), doubleArrayOf(123.2727, 119.8142, 101.459, 90.086, 84.316), doubleArrayOf(127.7037, 121.8669, 101.8346, 91.6997, 83.511), doubleArrayOf(126.0911, 123.7568, 109.7116, 94.5005, 84.3511), doubleArrayOf(127.5604, 126.0835, 113.6113, 95.3773, 85.7456), doubleArrayOf(123.6622, 125.7715, 106.9076, 87.0237, 81.5265), doubleArrayOf(116.8588, 128.1778, 106.6154, 89.5182, 80.9468), doubleArrayOf(112.7479, 125.1069, 103.157, 90.1573, 78.7498), doubleArrayOf(111.0141, 125.0074, 103.3716, 88.4794, 77.147), doubleArrayOf(124.6108, 130.8036, 108.5638, 95.9237, 81.3466), doubleArrayOf(125.6438, 128.1522, 107.1235, 96.7978, 79.6951), doubleArrayOf(127.917, 131.3068, 102.9136, 101.1251, 80.5478), doubleArrayOf(136.1716, 134.4931, 105.2081, 109.9993, 84.3638), doubleArrayOf(135.0478, 137.9409, 114.9391, 108.0031, 83.4068), doubleArrayOf(144.4333, 140.8892, 114.1595, 114.6706, 84.035))
        val MU = doubleArrayOf(0.12385769023993509, 0.10783528275730263, 0.05674387715046439, 0.05163286364099501)
        val COV_FLAT = doubleArrayOf(0.027223015143128727, 0.010794688397866698, 0.011980933965796586, 0.015447243450678806, 0.010794688397866698, 0.00932708523644076, 0.007855296676597397, 0.008802479320451247, 0.011980933965796586, 0.007855296676597397, 0.035251101352421696, 0.012504956192640658, 0.015447243450678806, 0.008802479320451247, 0.012504956192640658, 0.021658446159390988)
        val TANGENCY = doubleArrayOf(0.25634313649689977, 1.4136937792111617, -0.10153805082876009, -0.5684988648793012)
        val TANGENCY_LONG = doubleArrayOf(0.031060076107524683, 0.9689399238924757, 0.0, 0.0)
        val STATS = doubleArrayOf(0.1490812501561436, 0.12461988088838086, 0.955555801427879)
        val MV_RETURN = 0.09918517906457049
        val PORT_BETAS = doubleArrayOf(0.7881819355848674, 0.6536317233421903, 0.22458512247223508, 0.1816647340587139)
        val MARKET_BETAS = doubleArrayOf(1.1915454476931853, 0.6922896099851388, 0.8288481785979004, 1.0454394525438384)
        val ADJ_BETAS = doubleArrayOf(1.1264199954775023, 0.7969111425901916, 0.8870397978746143, 1.0299900386789334)
        val CML_A2 = doubleArrayOf(3.8338818598444506, -2.8338818598444506, 0.48654344482123807, 0.4777779007139395)
        val FRONTIER_SHORT_RET = doubleArrayOf(0.015520450341524966, 0.05163286364099501, 0.08774527694046505, 0.12385769023993509, 0.15997010353940513)
        val FRONTIER_SHORT_VOL = doubleArrayOf(0.16528340090949828, 0.12220125599047987, 0.09677256010189147, 0.10302398401355663, 0.1366754730318164)
        val FRONTIER_LONG_RET = doubleArrayOf(0.05163286364099501, 0.06968907029073003, 0.08774527694046505, 0.10580148359020007, 0.12385769023993509)
        val FRONTIER_LONG_VOL = doubleArrayOf(0.14716808811336632, 0.11704628539619075, 0.10131640945741895, 0.09620630813818959, 0.16499398396873918)
        val CORR_FLAT = doubleArrayOf(1.0, 0.6774371749030966, 0.38675524937163425, 0.6361642076392192, 0.6774371749030966, 1.0, 0.4332147890050305, 0.6193246882723147, 0.38675524937163425, 0.4332147890050305, 1.0, 0.4525662689384613, 0.6361642076392192, 0.6193246882723147, 0.4525662689384613, 1.0)
        val CAPITAL_SHARES = doubleArrayOf(1.0, 55.0, 0.0, 0.0)
        val CAPITAL_LEFTOVER = 106.66070000000127
    }
}
