#!/usr/bin/env bash
# Runs ONLY against the upstream VMRP demo fixture.
# No claim this proves gameplay for Duobao 1/2.
set -euo pipefail
EXPECTED_API="${EXPECTED_API:-36}"
EXPECTED_API="${EXPECTED_API%%.*}"
apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk"
echo "Expected API: $EXPECTED_API"
actual_api="$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Actual API: $actual_api"
test "$actual_api" = "$EXPECTED_API" || {
  echo "SDK mismatch: expected $EXPECTED_API, got $actual_api" >&2; exit 2;
}
bash scripts/ci_install_and_launch.sh "$apk"
for i in $(seq 1 75); do
  if adb logcat -d -s DuobaoRuntime:I | grep -Fq 'VMRP WASM postRun reached, chapter=1'; then
    echo "VMRP postRun signal confirmed on Android API $EXPECTED_API"
    echo 'This confirms engine initialization with upstream fixture only.'
    exit 0
  fi
  sleep 2
done
echo 'Engine did not reach postRun before timeout.' >&2
adb logcat -d -v brief -s DuobaoRuntime:I DuobaoRuntime:E DuobaoWebView:I AndroidRuntime:E chromium:E | tail -180 || true
exit 4
