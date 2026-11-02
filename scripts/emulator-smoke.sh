#!/usr/bin/env bash
# Drives the installed app on a running emulator through the main flows,
# saving screenshots to $OUT and failing if a step can't be found or the app crashes.
set -u
OUT=${OUT:-screenshots}
PKG=com.babyschedule
mkdir -p "$OUT"
FAILED=0

shot() {
  sleep 1
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "screenshot: $1"
}

# Prints the centre "x y" of the first on-screen node whose text/description is exactly $1.
find_xy() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || return 1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1 || return 1
  python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
target = sys.argv[1]
try:
    root = ET.parse('/tmp/ui.xml').getroot()
except Exception:
    sys.exit(1)
for n in root.iter('node'):
    if n.get('text') == target or n.get('content-desc') == target:
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        if y2 > y1:
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
}

scroll_down() { adb shell input swipe 540 1700 540 900 300; sleep 1; }
scroll_up() { adb shell input swipe 540 700 540 1800 300; sleep 1; }

# Taps the element with text $1, scrolling down then up to find it.
tap() {
  for dir in none down down down down up up up up up up up up; do
    case $dir in down) scroll_down ;; up) scroll_up ;; esac
    if xy=$(find_xy "$1"); then
      adb shell input tap $xy
      echo "tapped: $1"
      sleep 1.5
      return 0
    fi
  done
  echo "::error::Could not find '$1' on screen"
  adb exec-out screencap -p > "$OUT/missing-$(echo "$1" | tr -c 'A-Za-z0-9' '_').png"
  FAILED=1
  return 1
}

type_text() { adb shell input text "$1"; sleep 0.5; adb shell input keyevent 4; sleep 1; }
to_top() { for _ in 1 2 3 4 5; do scroll_up; done; }

adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 5
shot 01-first-launch

# Baby profile
tap "Set up profile"
tap "Name" && type_text "Mia"
tap "Choose date" && tap "OK"
tap "Birth weight" && type_text "3.4"
shot 02-profile-dialog
tap "Save"

# Breastfeeding timer: left, switch to right, finish
to_top
tap "Left"
sleep 4
shot 03-timer-left-running
tap "Right"
sleep 3
shot 04-timer-switched-right
tap "Finish"
sleep 1
shot 05-feeding-saved

# Diapers and bottle
tap "Wet"
tap "Both"
tap "Bottle" && tap "Amount" && type_text "90"
tap "Save"

# Weight
tap "Weight" && tap "Weight" && type_text "3.55"
tap "Save"

# Sleep
tap "Fell asleep"
sleep 3
shot 06-sleeping
tap "Woke up"

to_top
shot 07-today-top
scroll_down
shot 08-today-middle
scroll_down
scroll_down
shot 09-today-bottom

tap "History"
sleep 1
shot 10-history
tap "Stats"
sleep 1
shot 11-stats-top
scroll_down
shot 12-stats-charts
tap "Growth"
sleep 1
shot 13-growth

# Edit an entry from History
tap "History"
tap "Diaper · Wet"
shot 14-edit-diaper
tap "Cancel"

# Survives being killed and restarted (timer + data persist)
tap "Today"
tap "Left"
adb shell am force-stop $PKG
sleep 2
adb shell am start -W -n $PKG/.MainActivity
sleep 4
to_top
shot 15-timer-after-restart

adb logcat -d -b crash > "$OUT/crash-log.txt" || true
adb logcat -d > "$OUT/logcat.txt" || true
if grep -q "FATAL EXCEPTION" "$OUT/crash-log.txt"; then
  echo "::error::App crashed"
  cat "$OUT/crash-log.txt"
  FAILED=1
fi
if ! adb shell pidof $PKG >/dev/null; then
  echo "::error::App is not running at the end of the test"
  FAILED=1
fi
echo "Done, failed=$FAILED"
exit $FAILED
