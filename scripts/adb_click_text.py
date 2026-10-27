#!/usr/bin/env python3
"""Tap visible Android-native UI control by its accessibility text.

Used ONLY in an actual Android emulator CI job. Unlike the local JS mocks,
this interacts with a real installed APK and reads its UI tree.
"""
import argparse
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('label')
parser.add_argument('--timeout', type=float, default=25)
args = parser.parse_args()

def adb(*cmd, check=True):
    return subprocess.run(['adb', *cmd], capture_output=True, text=True,
                          errors='replace', check=check).stdout

def dump():
    adb('shell', 'uiautomator', 'dump', '/sdcard/duobao_ui.xml', check=False)
    output = adb('exec-out', 'cat', '/sdcard/duobao_ui.xml', check=False)
    pos = output.find('<?xml')
    if pos < 0:
        raise RuntimeError('No UI tree received')
    return ET.fromstring(output[pos:])

deadline = time.monotonic() + args.timeout
while time.monotonic() < deadline:
    try:
        root = dump()
        candidates = []
        for node in root.iter('node'):
            text_value = node.attrib.get('text', '').strip()
            content_desc = node.attrib.get('content-desc', '').strip()
            label = text_value or content_desc
            if not label or args.label not in label:
                continue
            bounds = node.attrib.get('bounds', '')
            found = re.match(r'\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]', bounds)
            if not found:
                continue
            x1, y1, x2, y2 = map(int, found.groups())
            is_clickable = node.attrib.get('clickable', 'false') == 'true'
            exact = label == args.label
            # Never tap dialog explanation text: it can contain the
            # action name but is not a clickable button.
            if not is_clickable:
                continue
            candidates.append((not exact, y2-y1, x2-x1, label, (x1+x2)//2, (y1+y2)//2))
        if candidates:
            candidates.sort()
            _, _, _, chosen_label, x, y = candidates[0]
            adb('shell', 'input', 'tap', str(x), str(y))
            print(f'TAPPED {args.label!r} via clickable {chosen_label!r} ({x},{y})')
            raise SystemExit(0)
    except (OSError, ET.ParseError, RuntimeError, subprocess.CalledProcessError):
        pass
    time.sleep(1.0)
try:
    snapshot = dump()
    rows = []
    for node in snapshot.iter('node'):
        label = node.attrib.get('text', '') + ' ' + node.attrib.get('content-desc', '')
        if label.strip():
            rows.append((label.strip(), node.attrib.get('bounds', ''), node.attrib.get('clickable', '')))
    print('UI labels currently visible: ' + repr(rows[:100]))
    Path('build/smoke').mkdir(parents=True, exist_ok=True)
    Path('build/smoke/failed_native_ui_labels.txt').write_text(
        repr(rows) + '\\nexpected: ' + args.label, encoding='utf-8')
except Exception as e:
    print('Unable to collect UI tree: ' + repr(e))
try:
    diag = subprocess.run(['adb','logcat','-d','-v','brief','-s','DuobaoRuntime:I','AndroidRuntime:E','ActivityTaskManager:I'],
                          capture_output=True, text=True, errors='replace').stdout
    Path('build/smoke').mkdir(parents=True, exist_ok=True)
    Path('build/smoke/ui_failure_logcat.txt').write_text(diag, encoding='utf-8')
    print('Recent runtime events:\\n' + '\\n'.join(diag.splitlines()[-35:]))
except Exception as e:
    print('Could not collect runtime logcat: ' + repr(e))
raise SystemExit(f'Unable to tap {args.label!r} using Android native UI hierarchy')
