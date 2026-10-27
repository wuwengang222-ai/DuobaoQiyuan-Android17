#!/usr/bin/env python3
"""Source-release safety check, not a license ruling."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
for path in root.rglob('*'):
 if path.is_file() and path.suffix.lower() in {'.mrp','.apk','.wasm'}:
  raise SystemExit('PUBLIC REPOSITORY CANNOT CONTAIN PRIVATE GAME BINARY/BUILT WASM: '+str(path))
print('PASS: no game ROM, APK or WASM in source checkout')
