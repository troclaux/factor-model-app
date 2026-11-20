# Buddy

Android app to plan how much to invest each month and where to put it. Two tabs:

- **Budget**: loads your recurring expenses, lets you adjust amounts, and instantly shows how much you have free to spend.
- **Portfolio**: CAPM / mean-variance analysis (port of the `capm` CLI). Finds the tangency portfolio for your tickers and shows where your own portfolio sits.

## Budget features

- Pre-populated expenses from config (same format as `budget.py`)
- Real-time balance calculation as you edit amounts
- Expenses grouped by category (Housing, Food, Health, etc.) with collapsible sections
- Separate sections for Debts and Taxes
- Shows "FREE TO SPEND" (green) or "DEFICIT" (red)

## Portfolio features

- Editable ticker list (B3 tickers need `.SA`) and market benchmark (default `^BVSP`)
- Risk-free rate, lookback (days/weeks/months/years), daily or monthly returns, short selling on/off
- Prices from Yahoo Finance (adjusted closes, window ends on the last trading day of the previous month)
- Tangency portfolio: weights, expected return, volatility, Sharpe ratio, market beta and Bloomberg-adjusted beta
- Mean-variance diagram: efficient frontier, CML, assets, tangency point, your portfolio and a hypothetical one
- Your portfolio and a hypothetical one, entered as share counts or weights (or pasted in the CLI's `TICKER VALUE` file format)
- CML allocation for risk aversion A (1, 2, 5 and your own) and capital split into whole shares
- Per-asset betas (vs tangency and vs market) and a correlation heatmap
- Risk-free rate typed in or fetched from a yield ticker (e.g. `^IRX`, `^TNX`)
- Every setting is saved on the device

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
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

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
