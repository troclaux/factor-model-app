package com.buddy.budget.portfolio.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

typealias Vector = DoubleArray
typealias Matrix = Array<DoubleArray>

data class PortfolioStats(
    val expectedReturn: Double,
    val volatility: Double,
    val sharpeRatio: Double,
)

data class CmlAllocation(
    val riskAversion: Double,
    val tangencyWeight: Double,
    val riskFreeWeight: Double,
    val expectedReturn: Double,
    val volatility: Double,
)

data class CapitalAllocation(
    val targets: Vector,
    val shares: Vector,
    val costs: Vector,
    val riskFreeAmount: Double,
    val leftover: Double,
)

data class Frontier(val volatilities: Vector, val returns: Vector)

/** Pure math for tangency-portfolio calculations. No I/O. */
object PortfolioMath {

    /** Simple returns (P_t - P_{t-1}) / P_{t-1}. Rows are dates, columns are assets. */
    fun simpleReturns(prices: Matrix): Matrix =
        Array(prices.size - 1) { row ->
            DoubleArray(prices[row].size) { col -> prices[row + 1][col] / prices[row][col] - 1.0 }
        }

    fun column(matrix: Matrix, col: Int): Vector = DoubleArray(matrix.size) { row -> matrix[row][col] }

    fun mean(values: Vector): Double = values.average()

    /** Sample covariance (ddof = 1), matching pandas. */
    fun covariance(first: Vector, second: Vector): Double {
        val firstMean = mean(first)
        val secondMean = mean(second)
        var sum = 0.0
        for (index in first.indices) sum += (first[index] - firstMean) * (second[index] - secondMean)
        return sum / (first.size - 1)
    }

    /** Annualized expected returns and covariance matrix from periodic returns. */
    fun estimateParameters(returns: Matrix, periodsPerYear: Int): Pair<Vector, Matrix> {
        val assetCount = returns[0].size
        val columns = Array(assetCount) { column(returns, it) }
        val expected = DoubleArray(assetCount) { mean(columns[it]) * periodsPerYear }
        val cov = Array(assetCount) { row ->
            DoubleArray(assetCount) { col -> covariance(columns[row], columns[col]) * periodsPerYear }
        }
        return expected to cov
    }

    fun correlationMatrix(returns: Matrix): Matrix {
        val assetCount = returns[0].size
        val columns = Array(assetCount) { column(returns, it) }
        return Array(assetCount) { row ->
            DoubleArray(assetCount) { col ->
                covariance(columns[row], columns[col]) /
                    sqrt(covariance(columns[row], columns[row]) * covariance(columns[col], columns[col]))
            }
        }
    }

    fun dot(first: Vector, second: Vector): Double {
        var sum = 0.0
        for (index in first.indices) sum += first[index] * second[index]
        return sum
    }

    fun multiply(matrix: Matrix, vector: Vector): Vector = DoubleArray(matrix.size) { dot(matrix[it], vector) }

    fun quadraticForm(matrix: Matrix, vector: Vector): Double = dot(vector, multiply(matrix, vector))

    /** Gauss-Jordan inverse with partial pivoting. */
    fun invert(matrix: Matrix): Matrix {
        val size = matrix.size
        val work = Array(size) { row -> DoubleArray(2 * size) { col -> if (col < size) matrix[row][col] else if (col - size == row) 1.0 else 0.0 } }
        for (pivotCol in 0 until size) {
            val pivotRow = (pivotCol until size).maxBy { abs(work[it][pivotCol]) }
            if (abs(work[pivotRow][pivotCol]) < 1e-14) {
                throw IllegalArgumentException("Covariance matrix is singular and cannot be inverted")
            }
            work[pivotCol] = work[pivotRow].also { work[pivotRow] = work[pivotCol] }
            val pivot = work[pivotCol][pivotCol]
            for (col in 0 until 2 * size) work[pivotCol][col] /= pivot
            for (row in 0 until size) {
                if (row == pivotCol) continue
                val factor = work[row][pivotCol]
                if (factor == 0.0) continue
                for (col in 0 until 2 * size) work[row][col] -= factor * work[pivotCol][col]
            }
        }
        return Array(size) { row -> work[row].copyOfRange(size, 2 * size) }
    }

    /** w = Σ⁻¹(μ - rf) / 1ᵀΣ⁻¹(μ - rf). Short positions allowed. */
    fun tangencyWeights(expected: Vector, cov: Matrix, riskFreeRate: Double): Vector {
        val premiums = DoubleArray(expected.size) { expected[it] - riskFreeRate }
        val raw = multiply(invert(cov), premiums)
        val total = raw.sum()
        return DoubleArray(raw.size) { raw[it] / total }
    }

    /**
     * Maximum-Sharpe weights with w ≥ 0 and Σw = 1.
     *
     * Solves min ½zᵀΣz − (μ − rf)ᵀz subject to z ≥ 0 by coordinate descent, then
     * normalizes: the direction of z is the long-only tangency portfolio.
     */
    fun tangencyWeightsLongOnly(expected: Vector, cov: Matrix, riskFreeRate: Double): Vector {
        val size = expected.size
        val premiums = DoubleArray(size) { expected[it] - riskFreeRate }
        if (premiums.all { it <= 0.0 }) {
            throw IllegalArgumentException("No asset has an expected return above the risk-free rate")
        }
        val scaled = DoubleArray(size)
        for (sweep in 0 until 200_000) {
            var largestChange = 0.0
            for (index in 0 until size) {
                var crossTerm = 0.0
                for (other in 0 until size) if (other != index) crossTerm += cov[index][other] * scaled[other]
                val updated = max(0.0, (premiums[index] - crossTerm) / cov[index][index])
                largestChange = max(largestChange, abs(updated - scaled[index]))
                scaled[index] = updated
            }
            if (largestChange < 1e-15 * max(1.0, scaled.max())) break
        }
        val total = scaled.sum()
        return DoubleArray(size) { scaled[it] / total }
    }

    fun statistics(weights: Vector, expected: Vector, cov: Matrix, riskFreeRate: Double): PortfolioStats {
        val expectedReturn = dot(weights, expected)
        val volatility = sqrt(quadraticForm(cov, weights))
        return PortfolioStats(expectedReturn, volatility, (expectedReturn - riskFreeRate) / volatility)
    }

    /** (μᵢ − rf) / Cov(rᵢ, r_p) per asset; constant across assets exactly when the weights are the tangency portfolio. */
    fun tangencyRatios(weights: Vector, expected: Vector, cov: Matrix, riskFreeRate: Double): Vector {
        val covWithPortfolio = multiply(cov, weights)
        return DoubleArray(weights.size) { (expected[it] - riskFreeRate) / covWithPortfolio[it] }
    }

    /** Expected return of the global minimum-variance portfolio (shorts allowed). */
    fun minVarianceReturn(expected: Vector, cov: Matrix): Double {
        val raw = multiply(invert(cov), DoubleArray(expected.size) { 1.0 })
        val total = raw.sum()
        return dot(DoubleArray(raw.size) { raw[it] / total }, expected)
    }

    /** βᵢ = Cov(rᵢ, r_p) / Var(r_p). */
    fun betasToPortfolio(cov: Matrix, weights: Vector): Vector {
        val covWithPortfolio = multiply(cov, weights)
        val variance = dot(weights, covWithPortfolio)
        return DoubleArray(weights.size) { covWithPortfolio[it] / variance }
    }

    /** βᵢ = Cov(rᵢ, r_m) / Var(r_m). The annualization factor cancels out. */
    fun marketBetas(returns: Matrix, marketReturns: Vector): Vector {
        val marketVariance = covariance(marketReturns, marketReturns)
        return DoubleArray(returns[0].size) { covariance(column(returns, it), marketReturns) / marketVariance }
    }

    /** Bloomberg adjustment: 0.66 · β + 0.34. */
    fun bloombergAdjust(betas: Vector, weight: Double = 0.66): Vector =
        DoubleArray(betas.size) { weight * betas[it] + (1.0 - weight) }

    /** w_T = (E[r_T] − rf) / (A · σ_T²). */
    fun cmlAllocation(riskAversion: Double, riskFreeRate: Double, tangency: PortfolioStats): CmlAllocation {
        val tangencyWeight =
            (tangency.expectedReturn - riskFreeRate) / (riskAversion * tangency.volatility * tangency.volatility)
        val riskFreeWeight = 1.0 - tangencyWeight
        return CmlAllocation(
            riskAversion = riskAversion,
            tangencyWeight = tangencyWeight,
            riskFreeWeight = riskFreeWeight,
            expectedReturn = riskFreeWeight * riskFreeRate + tangencyWeight * tangency.expectedReturn,
            volatility = abs(tangencyWeight) * tangency.volatility,
        )
    }

    /** Split capital between risk-free and stocks; shares are rounded down. */
    fun capitalAllocation(capital: Double, tangencyWeight: Double, weights: Vector, prices: Vector): CapitalAllocation {
        val stockBudget = capital * tangencyWeight
        val targets = DoubleArray(weights.size) { stockBudget * weights[it] }
        val shares = DoubleArray(weights.size) { floor(max(targets[it], 0.0) / prices[it]) }
        val costs = DoubleArray(weights.size) { shares[it] * prices[it] }
        return CapitalAllocation(targets, shares, costs, capital - stockBudget, stockBudget - costs.sum())
    }

    /** Converts share quantities into weights using market value at the given prices. */
    fun weightsFromShares(quantities: Vector, prices: Vector): Vector {
        val values = DoubleArray(quantities.size) { quantities[it] * prices[it] }
        val total = values.sum()
        return DoubleArray(values.size) { values[it] / total }
    }

    /**
     * Minimum-variance boundary.
     *
     * With shorts: closed form σ²(r) = (A·r² − 2B·r + C) / D over the same return range as the CLI.
     * Long-only: sweep the trade-off min wᵀΣw − t·wᵀμ over the simplex with projected gradient.
     */
    fun frontier(expected: Vector, cov: Matrix, allowShort: Boolean, points: Int = 120): Frontier =
        if (allowShort) frontierWithShorts(expected, cov, points) else frontierLongOnly(expected, cov, points)

    private fun frontierWithShorts(expected: Vector, cov: Matrix, points: Int): Frontier {
        val inverse = invert(cov)
        val ones = DoubleArray(expected.size) { 1.0 }
        val inverseOnes = multiply(inverse, ones)
        val inverseExpected = multiply(inverse, expected)
        val termA = dot(ones, inverseOnes)
        val termB = dot(ones, inverseExpected)
        val termC = dot(expected, inverseExpected)
        val determinant = termA * termC - termB * termB
        val lowest = expected.min()
        val highest = expected.max()
        val margin = (highest - lowest) * 0.5
        val returns = DoubleArray(points) { lowest - margin + (highest - lowest + 2 * margin) * it / (points - 1) }
        val vols = DoubleArray(points) { sqrt((termA * returns[it] * returns[it] - 2 * termB * returns[it] + termC) / determinant) }
        return Frontier(vols, returns)
    }

    private fun frontierLongOnly(expected: Vector, cov: Matrix, points: Int): Frontier {
        val size = expected.size
        val lipschitz = 2.0 * cov.maxOf { row -> row.sumOf { abs(it) } }
        val spread = max(expected.max() - expected.min(), 1e-12)
        val scale = lipschitz / spread
        var weights = DoubleArray(size) { 1.0 / size }
        val results = mutableListOf<Pair<Double, Double>>()
        for (step in 0 until points) {
            // tan maps an even grid onto (−∞, ∞), concentrating points near the minimum-variance end.
            val tradeOff = scale * tan(-1.55 + 3.1 * step / (points - 1))
            weights = minimizeOnSimplex(cov, expected, tradeOff, weights, lipschitz)
            results += sqrt(quadraticForm(cov, weights)) to dot(weights, expected)
        }
        // Exact endpoints: 100% in the lowest- and highest-return assets.
        for (index in listOf(expected.indices.minBy { expected[it] }, expected.indices.maxBy { expected[it] })) {
            results += sqrt(cov[index][index]) to expected[index]
        }
        val sorted = results.sortedBy { it.second }
        return Frontier(sorted.map { it.first }.toDoubleArray(), sorted.map { it.second }.toDoubleArray())
    }

    /** Projected gradient for min wᵀΣw − t·wᵀμ over the simplex. */
    private fun minimizeOnSimplex(cov: Matrix, expected: Vector, tradeOff: Double, start: Vector, lipschitz: Double): Vector {
        var weights = start
        repeat(5_000) {
            val gradient = multiply(cov, weights)
            val candidate = DoubleArray(weights.size) { weights[it] - (2 * gradient[it] - tradeOff * expected[it]) / lipschitz }
            val projected = projectOntoSimplex(candidate)
            val change = projected.indices.maxOf { abs(projected[it] - weights[it]) }
            weights = projected
            if (change < 1e-12) return weights
        }
        return weights
    }

    /** Euclidean projection onto {w ≥ 0, Σw = 1}. */
    fun projectOntoSimplex(vector: Vector): Vector {
        val sorted = vector.sortedDescending()
        var cumulative = 0.0
        var threshold = 0.0
        for (index in sorted.indices) {
            cumulative += sorted[index]
            val candidate = (cumulative - 1.0) / (index + 1)
            if (sorted[index] - candidate > 0) threshold = candidate
        }
        return DoubleArray(vector.size) { max(vector[it] - threshold, 0.0) }
    }
}
