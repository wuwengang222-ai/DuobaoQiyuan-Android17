#!/usr/bin/env bash
# REAL EMULATOR TEST: requires API 37 emulator + a compiled APK.
# Tests runtime initialization and manual chapter switch; DOES NOT assert
# actual RPG gameplay, ending recognition, or a complete playthrough.
set -euo pipefail
mkdir -p build/smoke
APK=app/build/outputs/apk/debug/app-debug.apk
test -s "$APK"
stable=0
for attempt in $(seq 1 45); do
  if adb shell cmd package list packages -3 >/dev/null 2>&1; then
    stable=$((stable+1))
    if (( stable >= 3 )); then break; fi
  else
    stable=0
  fi
  sleep 2
done
if (( stable < 3 )); then
  echo "EMULATOR FAILURE: PackageManager not stable after boot" >&2
  adb logcat -d -v time -t 300 -s AndroidRuntime:E SystemServer:E PackageManager:E | tail -120 || true
  exit 1
fi
adb install -r "$APK"
adb logcat -c
adb shell am start -n com.duobao.duology/.MainActivity

wait_for() {
  local phrase="$1" max=${2:-90}
  for ((i=0; i<max; i+=2)); do
    if adb logcat -d -s DuobaoRuntime:I | grep -Fq "$phrase"; then
      echo "CHECK: $phrase"
      return 0
    fi
    sleep 2
  done
  echo "EMULATOR FAILURE: Did not see: $phrase" >&2
  adb logcat -d -v time > build/smoke/full_logcat_on_failure.txt
  return 1
}

adb shell getprop ro.build.version.sdk > build/smoke/api_level.txt
EXPECTED_API="${EXPECTED_API:-37}"
EXPECTED_API="${EXPECTED_API%%.*}"
if ! grep -qx "$EXPECTED_API" build/smoke/api_level.txt; then
  echo "EMULATOR FAILURE: expected API $EXPECTED_API, got $(cat build/smoke/api_level.txt)" >&2; exit 1
fi
wait_for 'VMRP WASM postRun reached, chapter=1' 90
wait_for 'Canvas changed after engine startup; chapter=1' 45
if adb logcat -d -s DuobaoRuntime:E | grep -q 'VMRP runtime failure'; then
  echo 'EMULATOR FAILURE: chapter 1 runtime missing required files' >&2; exit 1
fi
adb exec-out screencap -p > build/smoke/chapter1.png

# Verify both games use a fresh VMRP instance, rather than just a menu screen.
python3 scripts/adb_click_text.py '章节 1' --timeout 25
python3 scripts/adb_click_text.py '手动进入第二部' --timeout 25
python3 scripts/adb_click_text.py '已通关，进入第二部' --timeout 25
wait_for 'VMRP WASM postRun reached, chapter=2' 90
wait_for 'Canvas changed after engine startup; chapter=2' 45
if adb logcat -d -s DuobaoRuntime:E | grep -q 'VMRP runtime failure'; then
  echo 'EMULATOR FAILURE: runtime load error' >&2; exit 1
fi
adb exec-out screencap -p > build/smoke/chapter2.png
adb logcat -d -v time -s DuobaoRuntime:I DuobaoWebView:I DuobaoEnding:I AndroidRuntime:E chromium:E > build/smoke/logcat.txt
if grep -q 'FATAL EXCEPTION' build/smoke/logcat.txt; then
  echo 'EMULATOR FAILURE: Android process crash' >&2; exit 1
fi
python3 - <<'PY'
from PIL import Image, ImageChops
from pathlib import Path
for chapter in (1,2):
    p=Path(f'build/smoke/chapter{chapter}.png')
    im=Image.open(p).convert('RGB')
    colors=im.resize((100,100)).getcolors(10001) or []
    if len(colors) < 2: raise SystemExit(f'FAIL: uniform screenshot from chapter {chapter}')
    print(f'Chapter {chapter}: emulator screenshot {im.size}, {len(colors)} preview colors')
PY
# Even a nonuniform screenshot cannot prove gameplay. The CI artifact must
# still be labeled an emulator smoke test, not a verified complete game.
echo "PASS: Android API $EXPECTED_API launched VMRP and manual transition loaded second VMRP fixture."
echo 'NOT PROVEN: both games playable, OCR accuracy, automatic plot handoff, OnePlus 13.'
