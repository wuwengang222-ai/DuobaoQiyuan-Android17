#!/usr/bin/env bash
# Reliable application install and launch in CI emulators, with bounded retries.
# Usage: bash scripts/ci_install_and_launch.sh /path/to/app.apk
# Neither this script nor APK installation constitutes proof of gameplay.
set -euo pipefail
apk="${1:?APK path is required}"
pkg="com.duobao.duology"
activity="$pkg/.MainActivity"
test -s "$apk"
mkdir -p build/smoke
echo "Emulator API: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Current PackageManager state:"
adb shell cmd package list packages -3 | head -35 || true
adb shell df -h /data || true
# This avoids an early success response for streamed install while Android 17's
# PackageManager / activity service is still restarting.
ready=0
for i in $(seq 1 45); do
  if adb shell cmd package list packages -3 >/dev/null 2>&1 &&
     adb shell cmd activity get-uid-state 1000 >/dev/null 2>&1; then
    ready=$((ready+1))
    if [ "$ready" -ge 3 ]; then break; fi
  else
    ready=0
  fi
  sleep 2
done
if [ "$ready" -lt 3 ]; then
  echo "ERROR: Android PackageManager/ActivityManager services are unstable" >&2
  adb logcat -d -t 200 -s AndroidRuntime:E system_server:E PackageManager:E ActivityManager:E > build/smoke/boot_services_error.log || true
  exit 20
fi
installed=0
for trial in 1 2 3; do
  echo "Install attempt $trial"
  if adb install --no-streaming -r "$apk"; then
    for i in $(seq 1 12); do
      if adb shell pm path "$pkg" | grep -q '^package:'; then
        installed=1
        break
      fi
      sleep 2
    done
  fi
  [ "$installed" -eq 1 ] && break
  sleep 4
done
if [ "$installed" -ne 1 ]; then
  echo "ERROR: APK installation could not be verified by pm path" >&2
  adb shell pm path "$pkg" || true
  adb logcat -d -t 250 -s PackageManager:E AndroidRuntime:E ActivityManager:E > build/smoke/pm_install_error.log || true
  exit 21
fi
echo "Verified APK package path:"
adb shell pm path "$pkg"
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$pkg" || true
adb logcat -c
launched=0
for trial in 1 2 3 4 5; do
  echo "Launch attempt $trial"
  if adb shell am start -W -n "$activity"; then
    launched=1
    break
  fi
  echo "Activity manager didn't resolve launcher; wait/retry" >&2
  adb shell pm path "$pkg" || true
  sleep 4
done
if [ "$launched" -ne 1 ]; then
  echo "ERROR: main Activity did not launch; installed APK isn't yet proof of Android compatibility" >&2
  adb shell dumpsys package "$pkg" > build/smoke/package_diagnostics.txt || true
  adb logcat -d -t 300 -s PackageManager:E AndroidRuntime:E ActivityTaskManager:E ActivityManager:E > build/smoke/activity_launch_error.log || true
  exit 22
fi
echo "PASS: installed APK and dispatched launcher activity (game still unverified)."
