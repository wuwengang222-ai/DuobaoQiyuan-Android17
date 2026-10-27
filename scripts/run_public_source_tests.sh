#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -d "$tmp" \
  app/src/main/java/com/duobao/duology/DuologyStateMachine.java \
  app/src/main/java/com/duobao/duology/StoryEndingDetector.java \
  tests/TestHandoff.java tests/TestStoryEndingDetector.java \
  tests/TestIntegratedHandoff.java tests/TestStoryEndingOCR.java tests/TestSaveRetry.java
for t in TestHandoff TestStoryEndingDetector TestIntegratedHandoff TestStoryEndingOCR TestSaveRetry; do java -cp "$tmp" "$t"; done
node --check app/src/main/assets/duobao_bridge.js
node --check app/src/main/assets/duobao_phone_ui.js
node tests/test_phone_layout.js
node tests/test_bridge.js
node tests/test_runtime_failclosed.js
python3 tests/test_fallback_source.py
python3 -m py_compile scripts/prepare_runtime.py
# Requires proprietary MRP games, intentionally omitted from public source:
if [ -s app/src/main/assets/games/part1.mrp ] && [ -s app/src/main/assets/games/part2.mrp ]; then
 python3 scripts/verify_game_assets.py
 python3 tests/test_runtime_packager.py
 python3 analysis/verify_ending_in_mrp.py
fi
