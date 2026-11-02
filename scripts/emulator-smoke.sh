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

# Breastfeeding session: start on left, switch, end, review, save
to_top
tap "Start breastfeeding"
shot 03-choose-side
tap "Left"
sleep 4
shot 04-feeding-left
tap "Switch to Right"
sleep 3
shot 05-feeding-right
tap "End feeding"
tap "Wet"
shot 06-review-feeding
tap "Save feeding"
sleep 1
shot 07-feeding-saved

# Bottle, diaper and weight via "Add entry"
tap "Add bottle, diaper, sleep or weight"
shot 08-add-menu
tap "Bottle feed" && tap "Amount" && type_text "90"
tap "Save"
tap "Add bottle, diaper, sleep or weight"
tap "Diaper" && tap "Wet + dirty"
tap "Save"
tap "Add bottle, diaper, sleep or weight"
tap "Weight / growth" && tap "Weight" && type_text "3.55"
tap "Save"

# Sleep (may trigger the notification permission prompt on Android 13+)
tap "Fell asleep"
if xy=$(find_xy "Allow"); then adb shell input tap $xy; sleep 1; fi
sleep 3
shot 09-sleeping
tap "Woke up"

# Settings
tap "Settings"
shot 10-settings
tap "Cancel"

to_top
shot 11-today-top
scroll_down
shot 12-today-middle
scroll_down
scroll_down
shot 13-today-bottom

tap "History"
sleep 1
shot 14-history
tap "Stats"
sleep 1
shot 15-stats-top
scroll_down
shot 16-stats-charts
tap "Growth"
sleep 1
shot 17-growth

# Edit an entry from History
tap "History"
tap "Diaper · Wet"
shot 18-edit-diaper
tap "Cancel"

# A running feed survives the app being killed
tap "Today"
to_top
tap "Start breastfeeding"
tap "Right"
adb shell am force-stop $PKG
sleep 2
adb shell am start -W -n $PKG/.MainActivity
sleep 4
to_top
shot 19-feed-in-progress-after-restart

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
