# Baby Schedule

A simple Android app for tracking a newborn's day. It's built to be quick to use with one hand.

## Features

- **Log a session (main action).** One form covers a whole care session, filled in after the fact:
  - **Diaper:** pee, poop or both, with an amount and notes (e.g. colour)
  - **Feeding:** breast or bottle. For breast, enter the weight before, then each side with **start and end times** (e.g. Right 1:15–1:31, Left 1:31–1:47; **+ Add side** continues from the last one), then the weight after. The app shows how much was drunk.
  - **Sleep:** fell asleep and woke-up times, or "sleeping now", which starts a sleep timer
  - **Notes**

  The log shows each session as one entry. Tap it to edit or delete.
- **Live timers (secondary).** The feeding timer records side switches as they happen, and ending it opens the session form already filled in. "Fell asleep now" times a sleep; when the baby wakes, the app offers to add that sleep to the session just before it.
- **Long sleep alert.** A notification when a sleep goes on longer than a set time (2 hours by default). Set it in **Settings**.
- **Single entries.** Bottle, diaper, sleep or weight, logged on their own.
- **Today.** The last feed, the suggested next side, sleep status, today's totals and today's log.
- **History and breakdowns.** Go back day by day. Tap a total to see the feeds, breastfeeding (left/right and weighed intake), bottle, diapers or sleep for that day.
- **Stats** for 7, 14 or 30 days, and **Growth** in grams (including weights taken before feeds).

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
