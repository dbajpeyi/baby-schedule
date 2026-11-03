# Baby Schedule

A simple Android app for tracking a newborn's day. It's built to be quick to use with one hand.

## Features

- **Breastfeeding sessions.** Tap **Start breastfeeding** and pick the starting side (the suggested side is highlighted). Use **Switch side** and **Pause** as needed, then **End feeding**. Check the summary (left and right minutes, plus an optional diaper change) and save it as one feed. The timer keeps running if you leave the screen or close the app.
- **Sleep.** Tap **Fell asleep**, then **Woke up**.
- **Long sleep alert.** Get a notification when a sleep goes on longer than a set time (2 hours by default). Change it in **Settings** (gear icon).
- **Add entry.** Bottle feeds, diapers, past feeds or sleeps, and weight. Each opens a short form with **Save**, so nothing is logged by accident.
- **Today.** Shows the last feed, the suggested next side, sleep status, today's totals and today's log.
- **History.** Go back day by day. Tap any entry to edit or delete it.
- **Stats** for 7, 14 or 30 days: feeds per day, time between feeds, left/right balance, diapers and sleep, with simple bar charts.
- **Growth.** Weight, length and head size. Shows the change since birth and the weekly gain, with a weight chart.

Everything is stored on the phone (Room database). Android's automatic backup includes it.

## Download

Download the latest APK (no GitHub sign-in needed):

**https://github.com/dbajpeyi/baby-schedule/releases/latest/download/baby-schedule.apk**

Open it on your Android phone and allow "Install unknown apps" if asked. Every push to the main branch publishes a new release. Each release installs over the previous one and keeps your data.

### One-time setup: release signing key

Releases are only published once a signing key is stored in GitHub secrets. Using one fixed key means every new version installs over the old one and keeps your data. Create the key on your own computer. It never goes into the repo.

```bash
keytool -genkeypair -keystore baby-schedule.keystore -alias babyschedule -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Baby Schedule"
```

Then add two repository secrets under **Settings → Secrets and variables → Actions**:

- `KEYSTORE_BASE64`: the output of `base64 -i baby-schedule.keystore` (macOS) or `base64 -w0 baby-schedule.keystore` (Linux)
- `KEYSTORE_PASSWORD`: the password you chose

Keep `baby-schedule.keystore` somewhere safe. If you lose it, future builds can't update the installed app.

## Building locally

You need Android Studio, or JDK 17 plus the Android SDK. Then run:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Tech

Kotlin, Jetpack Compose (Material 3), Room. Minimum Android 8.0 (API 26).
