# Baby Schedule

A simple Android app for tracking a newborn's day. It's built to be quick to use with one hand.

## Features

- **Breastfeeding timer (left / right).** Tap **Left** or **Right** to start. Tap the other side to switch, or tap the running side to pause. Press **Finish** to save. The app suggests which side to start on next. The timer keeps running if the app is closed.
- **Bottle feeds.** Log the amount in ml and whether it was formula or breast milk.
- **Diapers.** One tap for Wet, Dirty or Both. Every quick action has **Undo**.
- **Sleep.** Tap "Fell asleep" and then "Woke up", or add a past sleep.
- **Weight and growth.** Log weight, length and head size. You see the change since birth weight and the weekly gain, plus a weight chart.
- **Today.** Shows the time since the last feed, diaper and sleep, today's totals and today's log.
- **History.** Go back day by day. Tap any entry to edit or delete it.
- **Stats** for 7, 14 or 30 days:
  - average feeds per day and time between feeds
  - left/right balance
  - diapers per day and sleep per day
  - bar charts for each of these

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
