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

## Getting the APK

Every push runs the **Build APK** GitHub Actions workflow. To download the app:

1. Open the latest workflow run under **Actions**.
2. Download the `baby-schedule-apk` artifact.
3. Install `app-release.apk` on the phone. You may need to allow "Install unknown apps".

## Building locally

You need Android Studio, or JDK 17 plus the Android SDK. Then run:

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

## Tech

Kotlin, Jetpack Compose (Material 3), Room. Minimum Android 8.0 (API 26).
