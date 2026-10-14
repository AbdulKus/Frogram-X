#!/usr/bin/env python3
"""Reject release APKs with a missing, empty or oversized compiled ART profile."""
import argparse
from pathlib import Path
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as apk:
        profile = apk.read("assets/dexopt/baseline.prof")
        metadata = apk.read("assets/dexopt/baseline.profm")
    if profile[:4] != b"pro\x00" or metadata[:4] != b"prm\x00":
        raise SystemExit("Invalid ART profile headers")
    if not 8 < len(profile) < 1_500_000 or len(metadata) <= 8:
        raise SystemExit("Empty or oversized ART profile")
    print(f"Compiled ART profile verified: {len(profile)} bytes, metadata {len(metadata)} bytes")


if __name__ == "__main__":
    main()
