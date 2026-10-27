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
        for node in root.iter('node'):
            label = node.attrib.get('text', '') + ' ' + node.attrib.get('content-desc', '')
            if args.label not in label:
                continue
            b = node.attrib.get('bounds', '')
            found = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
            if not found:
                continue
            x1, y1, x2, y2 = map(int, found.groups())
            adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            print(f'TAPPED {args.label!r} ({x1+x2>>1},{y1+y2>>1})')
            raise SystemExit(0)
    except (OSError, ET.ParseError, RuntimeError, subprocess.CalledProcessError):
        pass
    time.sleep(1.0)
raise SystemExit(f'Unable to tap {args.label!r} using Android native UI hierarchy')
