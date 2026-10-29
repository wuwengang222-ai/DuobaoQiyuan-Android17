#!/usr/bin/env python3
"""Fail CI if an APK violates Android 11+ resources and native-library ZIP rules.

This is a structural check, NOT proof of installation on a real device.
Run: python3 scripts/check_apk_install.py app/build/outputs/apk/debug/app-debug.apk
"""
import argparse
from pathlib import Path
import struct
import sys
import zipfile


def data_offset(apk, entry):
    """Read the LOCAL header: its extra length may differ from the central entry."""
    apk.seek(entry.header_offset)
    header = apk.read(30)
    if len(header) != 30 or header[:4] != b"PK\\x03\\x04":
        raise ValueError(f"invalid ZIP local header: {entry.filename}")
    name_len, extra_len = struct.unpack_from("<HH", header, 26)
    return entry.header_offset + 30 + name_len + extra_len


def verify(path):
    errors = []
    with path.open("rb") as raw, zipfile.ZipFile(raw) as archive:
        resource = archive.getinfo("resources.arsc")
        resource_offset = data_offset(raw, resource)
        if resource.compress_type != zipfile.ZIP_STORED:
            errors.append("resources.arsc is compressed; Android targetSdk>=30 cannot install")
        if resource_offset % 4:
            errors.append(f"resources.arsc offset={resource_offset} is not 4-byte aligned")
        for entry in archive.infolist():
            if not (entry.filename.startswith("lib/") and entry.filename.endswith(".so")):
                continue
            offset = data_offset(raw, entry)
            if entry.compress_type != zipfile.ZIP_STORED:
                errors.append(f"{entry.filename}: compressed .so with extractNativeLibs=false")
            if offset % 16384:
                errors.append(f"{entry.filename}: offset={offset} not 16-KiB aligned")
        raw.seek(0, 2)
        size = raw.tell()
        # This project has no ZIP comments or ZIP64; a minimal v2 presence check.
        if size < 22:
            errors.append("APK too small")
        else:
            raw.seek(-22, 2)
            eocd = raw.read(22)
            if eocd[:4] != b"PK\\x05\\x06":
                errors.append("unrecognized ZIP EOCD: check APK signature separately")
            else:
                cd_offset = struct.unpack_from("<I", eocd, 16)[0]
                if cd_offset < 24:
                    errors.append("APK signing block missing")
                else:
                    raw.seek(cd_offset - 16)
                    if raw.read(16) != b"APK Sig Block 42":
                        errors.append("APK v2/v3 signing block missing (run apksigner verify)")
    if errors:
        for error in errors:
            print("FAIL:", error, file=sys.stderr)
        raise SystemExit(1)
    print(f"PASS: {path}: resources.arsc stored/4-byte aligned, .so stored/16KiB aligned, signing block present")
    print("For final validation: apksigner verify --verbose APK && adb install -r APK")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    verify(parser.parse_args().apk)
