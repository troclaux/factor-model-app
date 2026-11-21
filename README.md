# Buddy

Android app to plan how much to invest each month and where to put it. Two tabs:

- **Budget**: loads your recurring expenses, lets you adjust amounts, and instantly shows how much you have free to spend.
- **Portfolio**: CAPM / mean-variance analysis (port of the `capm` CLI). Finds the tangency portfolio for your tickers and shows where your own portfolio sits.

## Budget features

Port of the `budget.py` CLI. Three tabs: Plan, Report and History.

- Pre-populated expenses from config (same format as `budget.py`)
- Real-time balance calculation as you edit amounts: net salary + leftover + inflows − outflows, shown as "FREE TO SPEND" (green) or "DEFICIT" (red)
- Expenses grouped by category (Housing, Food, Health, etc.) with collapsible sections, plus Debts and Taxes
- Tap an item to set its day, whether it goes on the credit card (fatura) and whether it is an inflow
- Proventos (income received before the fatura debits), and the fatura as one total instead of itemized
- One-off extra expenses and bonuses for the month
- Report: payment timeline by day, fatura and non-fatura breakdowns, debts, taxes, outflows by category and changes vs last month; share it as a CLI-style `output.txt` or a styled PDF with a category pie and the history
- History: save the month (saving again replaces it), then see each month's categories with % of expenses, fatura/non-fatura split and averages over all or the last 3/6/12 months. Same file format as `budget_history.json`
- Remembered values: a new month starts with last month's balance as leftover and last month's fatura total
- Save as defaults: pick which of this month's changed values (salary, proventos, any item's amount/day/fatura/inflow) become the new defaults
- This month's edits are kept until the month changes

## Portfolio features

- Editable ticker list (B3 tickers need `.SA`) and market benchmark (default `^BVSP`)
- Risk-free rate, daily, weekly or monthly returns, short selling on/off
- Period as a lookback (days/weeks/months/years, ending on the last trading day of the previous month) or as start and end dates, with the end optionally set to the latest available close
- Prices from Yahoo Finance (daily adjusted closes; weekly and monthly use the last close of each week or month)
- Tangency portfolio: weights, expected return, volatility, Sharpe ratio, market beta and Bloomberg-adjusted beta
- Mean-variance diagram: efficient frontier, CML, assets, tangency point, your portfolio and a hypothetical one
- Your portfolio and a hypothetical one, entered as share counts or weights (or pasted in the CLI's `TICKER VALUE` file format)
- CML allocation for risk aversion A (1, 2, 5 and your own) and capital split into whole shares
- Per-asset betas (vs tangency and vs market) and a correlation heatmap
- Risk-free rate typed in, CDI or Selic from the Banco Central's public API, or a Yahoo yield ticker (e.g. `^IRX`, `^TNX`)
- Tangency verification: (μᵢ − rf) / Cov(rᵢ, r_T) must match across assets (when shorts are allowed)
- Intermediate values (prices, returns, means, covariance), like the CLI's `--verbose`
- Copy the full text report or share it as `output.txt`; share either chart as a PNG
- Share a styled A4 PDF report: the inputs used (risk-free rate and source, lookback, price window, frequency, etc.), summary, both charts and all tables
- Import "My portfolio" from Open Finance via [Pluggy](https://docs.pluggy.ai) (free for personal use through MeuPluggy): stocks, FIIs, BDRs and ETFs become share counts, missing tickers are added. Client ID, secret and item IDs are entered in the app and stay on the phone; the secret is encrypted with a key held in the Android Keystore
- Every setting is saved on the device
- The last analysis is saved too: reopening the app shows its results again without downloading anything
- A "What these numbers mean" glossary of every parameter and metric, at the end of the results and the PDF

The math lives in `app/src/main/java/com/buddy/budget/portfolio/model/PortfolioMath.kt` and is unit-tested against the Python CLI:

```bash
./gradlew testDebugUnitTest
```

## Prerequisites

- JDK 17 or 21 (check with `java -version`). Gradle 8.9 does not run on JDK 25; with mise: `mise install java@temurin-21` and `export JAVA_HOME=$(mise where java@temurin-21)`
- Android SDK with platform 35 and build-tools 35.0.0

### Installing Android SDK (if needed)

```bash
# Download command-line tools
mkdir -p ~/Android/Sdk/cmdline-tools
cd /tmp
curl -sL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o cmdtools.zip
unzip -qo cmdtools.zip
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# Set environment variable (add to your .bashrc or .zshrc)
export ANDROID_HOME="$HOME/Android/Sdk"

# Accept licenses and install required components
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0"
```

## Build

```bash
./build.sh            # runs the unit tests and builds the APK
./build.sh --install  # also installs it on the phone connected over USB
```

The script picks a JDK 17/21 (`JAVA_HOME`, or mise's Temurin 21) and defaults `ANDROID_HOME` to `~/Android/Sdk`. The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

## Install

```bash
# Connect your Android device via USB (with USB debugging enabled) or start an emulator
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Customizing expenses

Edit `app/src/main/assets/budget_config.json` to change the default expenses, salary, debts, and taxes. That file holds sample values; to keep your real numbers out of git, put them in `app/src/debug/assets/budget_config.json` (gitignored), which debug builds use instead. The format matches the Python `budget_config.json`:

```json
{
  "fatura_debit_day": 9,
  "income": 5000,
  "expenses": [
    { "name": "Rent", "amount": 500, "day": 26, "category": "housing", "is_fatura": false }
  ],
  "taxes": [
    { "name": "Income Tax", "amount": 300, "day": null, "is_fatura": false }
  ],
  "debts": [
    { "name": "Loan", "amount": 500, "day": 1, "is_fatura": false }
  ]
}
```

Rebuild after editing the config.
