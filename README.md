# Buddy

Android app to plan how much to invest each month.

- **Budget**: loads your recurring expenses, lets you adjust amounts, and instantly shows how much you have free to spend.

## Budget features

- Pre-populated expenses from config (same format as `budget.py`)
- Real-time balance calculation as you edit amounts
- Expenses grouped by category (Housing, Food, Health, etc.) with collapsible sections
- Separate sections for Debts and Taxes
- Shows "FREE TO SPEND" (green) or "DEFICIT" (red)

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
