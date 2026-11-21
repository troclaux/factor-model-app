package com.buddy.budget.portfolio.model

/** Plain-language explanations of every parameter and metric, shown at the end of the results and the PDF. */
object Glossary {

    data class Entry(val term: String, val explanation: String)

    val parameters = listOf(
        Entry(
            "Tickers",
            "The assets analysed. B3 tickers need the .SA suffix (PETR4.SA). Prices are Yahoo Finance closes adjusted " +
                "for dividends and splits, so returns include what you would have received.",
        ),
        Entry(
            "Market benchmark",
            "The index that stands in for \"the market\" when computing market betas, e.g. ^BVSP (Ibovespa) or SPY. " +
                "Leave it blank to skip market betas.",
        ),
        Entry(
            "Risk-free rate",
            "The annual return of an asset with no risk, such as Selic/CDI or a short-term Treasury bill. Every \"excess " +
                "return\" below is measured over it, and it is where the Capital Market Line starts. With CDI or Selic as " +
                "the proxy the app uses the Banco Central's latest annualized rate before the end of the window; with a " +
                "Yahoo yield ticker (e.g. ^IRX) that yield's latest close. Pick a maturity that matches your investment horizon.",
        ),
        Entry(
            "Period",
            "The price history used for every estimate. Lookback counts back from the last trading day of the previous " +
                "month; Dates uses your start and end (or the latest close). A longer period gives more data but mixes in " +
                "older market conditions that may no longer hold.",
        ),
        Entry(
            "Return frequency",
            "How often prices are sampled: daily (252 periods a year), weekly (52) or monthly (12). Weekly and monthly use " +
                "the last close of each week or month. Higher frequency gives more observations from the same period; " +
                "daily data is noisier and can understate correlation between thinly traded stocks.",
        ),
        Entry(
            "Short selling",
            "When allowed, weights can be negative: selling borrowed shares to fund larger positions elsewhere. When " +
                "forbidden, every weight is at least 0%, which is how most individual investors actually invest.",
        ),
        Entry(
            "Risk aversion (A)",
            "How much you dislike risk. The optimal share of your money in the tangency portfolio is " +
                "(E[r_T] − rf) / (A × σ_T²); the rest goes to the risk-free asset. A ≥ 5 is conservative, around 2 " +
                "moderate, 1 or less aggressive.",
        ),
        Entry(
            "Capital",
            "The amount you want to invest. It is split between the risk-free asset and the tangency portfolio (using " +
                "A if set, otherwise 100% in the tangency portfolio) and turned into whole shares at the last close.",
        ),
        Entry(
            "My portfolio and Hypothetical portfolio",
            "Portfolios to compare with the tangency portfolio. Share counts are turned into weights by market value " +
                "(shares × last price ÷ total), not by number of shares.",
        ),
    )

    val metrics = listOf(
        Entry(
            "Observations",
            "The number of returns in the window. Each return is the change from one price to the next: " +
                "P(t) / P(t−1) − 1. Fewer than about 60 makes every estimate unreliable.",
        ),
        Entry(
            "Expected return (E[r])",
            "The average return per period times the periods per year (e.g. average monthly return × 12). It is a " +
                "historical average, not a forecast, and is the noisiest input of all.",
        ),
        Entry(
            "Volatility (σ)",
            "The annualized standard deviation of returns: how much returns swing around their average. Periodic " +
                "standard deviation × √(periods per year). This is the measure of risk used throughout.",
        ),
        Entry(
            "Covariance and correlation",
            "How two assets move together. Correlation scales covariance to between −1 (always opposite) and +1 " +
                "(always together); 0 means unrelated. Low or negative correlations are what make diversification reduce risk.",
        ),
        Entry(
            "Sharpe ratio",
            "(E[r] − rf) / σ: the extra return over the risk-free rate per unit of volatility. Higher is better; it is " +
                "the slope of a line from the risk-free rate to the portfolio on the diagram.",
        ),
        Entry(
            "Tangency portfolio",
            "The combination of your assets with the highest possible Sharpe ratio. With short selling its weights are " +
                "Σ⁻¹(μ − rf), scaled to add up to 100%; without shorts the app finds the best long-only weights numerically.",
        ),
        Entry(
            "Efficient frontier",
            "The lowest volatility achievable for each level of expected return. The solid part, above the " +
                "minimum-variance point, is efficient; the dashed part below gives less return for the same risk.",
        ),
        Entry(
            "Capital Market Line (CML)",
            "The line E[r] = rf + Sharpe_T × σ from the risk-free rate through the tangency portfolio. Every point is a " +
                "mix of the two, and those mixes beat any other portfolio of the same risk. It assumes you can borrow at " +
                "the risk-free rate, which in practice costs more.",
        ),
        Entry(
            "Tangency share and risk-free share",
            "For a given A, the fraction of your money in the tangency portfolio and in the risk-free asset; they add up " +
                "to 100%. A tangency share above 100% means borrowing to invest more than you have.",
        ),
        Entry(
            "Verification",
            "At the tangency portfolio, (μᵢ − rf) / Cov(rᵢ, r_T) is the same for every asset: no shift between assets can " +
                "improve the Sharpe ratio. PASS means the computed weights satisfy this. It only applies with short selling.",
        ),
        Entry(
            "β tangency",
            "Cov(rᵢ, r_T) / Var(r_T): how strongly an asset moves with the tangency portfolio. Above 1 it amplifies the " +
                "portfolio's moves; below 1 it dampens them.",
        ),
        Entry(
            "Market beta",
            "Cov(rᵢ, r_m) / Var(r_m) against the market benchmark: a beta of 1.2 means the asset tended to move 1.2% for " +
                "each 1% move of the market. A portfolio's beta is the value-weighted average Σ wᵢ βᵢ.",
        ),
        Entry(
            "Bloomberg adjusted beta",
            "0.66 × β + 0.34. It pulls historical betas toward 1 because extreme estimates are partly noise and betas " +
                "drift toward the market average over time.",
        ),
        Entry(
            "Sharpe gap vs tangency",
            "The tangency portfolio's Sharpe ratio minus a portfolio's: how much risk-adjusted return that portfolio " +
                "leaves on the table, by these historical estimates.",
        ),
        Entry(
            "Capital allocation",
            "Target: money for each asset (capital × tangency share × weight). Shares: target ÷ last price, rounded down. " +
                "Cost: shares × price. Leftover: cash not spent because of rounding.",
        ),
    )
}
