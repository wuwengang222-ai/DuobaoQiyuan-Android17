#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
(
  cd "$tmp"
  javac -encoding UTF-8 -classpath "$tmp" -sourcepath "$root/app/src/main/java" -d "$tmp" \
    "$root/app/src/main/java/com/duobao/duology/DuologyStateMachine.java" \
    "$root/app/src/main/java/com/duobao/duology/StoryEndingDetector.java" \
    "$root/tests/TestHandoff.java" \
    "$root/tests/TestStoryEndingDetector.java" \
    "$root/tests/TestIntegratedHandoff.java" \
    "$root/tests/TestStoryEndingOCR.java" \
    "$root/tests/TestSaveRetry.java"
  for test in TestHandoff TestStoryEndingDetector TestIntegratedHandoff TestStoryEndingOCR TestSaveRetry; do
    java -cp "$tmp" "$test"
  done
)
cd "$root"
node --check app/src/main/assets/duobao_bridge.js
node tests/test_bridge.js
node tests/test_runtime_failclosed.js
python3 analysis/verify_ending_in_mrp.py

python3 tests/test_runtime_packager.py

python3 tests/test_fallback_source.py
