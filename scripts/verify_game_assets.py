#!/usr/bin/env python3
"""Only accept the user's original two MRP files; no synthetic stand-ins."""
from pathlib import Path
import hashlib
root=Path(__file__).resolve().parents[1]/'app/src/main/assets/games'
expected={'part1.mrp':'69f48fd49dea033229ce789e94b396cdf7452ec65e40b963ccbae97717982c2d','part2.mrp':'80299713a7a9dc616b79af1db4dfabd39c6e7b56682012ecad48b81112e8b959'}
for name,want in expected.items():
 p=root/name
 if not p.is_file(): raise SystemExit(f'MISSING GAME {name} — build cannot continue. Public GitHub source deliberately omits games.')
 got=hashlib.sha256(p.read_bytes()).hexdigest()
 if got!=want: raise SystemExit(f'WRONG GAME HASH {name} {got}')
 print('verified original MRP file:',name,p.stat().st_size,got)
