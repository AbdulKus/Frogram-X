#!/usr/bin/env python3
"""Publish metadata from the actual APKs, never inferred from their filenames."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path


def parse_badging(text):
    package = re.search(r"^package: (.+)$", text, re.MULTILINE)
    sdk = re.search(r"^sdkVersion:'(\d+)'", text, re.MULTILINE)
    native = re.search(r"^native-code: (.+)$", text, re.MULTILINE)
    if not package or not sdk or not native:
        raise ValueError("APK must declare a package, minimum SDK and native ABIs")
    fields = dict(re.findall(r"(\w+)='([^']*)'", package[1]))
    if fields.get("name") != "org.frogram.messenger":
        raise ValueError("Unexpected APK package")
    code = int(fields["versionCode"])
    if not 1796302 < code <= 2100000000:
        raise ValueError("APK versionCode must advance beyond existing Frogram X releases")
    abis = re.findall(r"'([^']+)'", native[1])
    if not abis or any(abi not in {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"} for abi in abis):
        raise ValueError("Unsupported APK ABI")
    return {"version_code": code, "version_name": fields["versionName"], "min_sdk": int(sdk[1]), "abis": abis}


def build_manifest(artifacts, aapt, build_number, commit):
    result = {"schema": 1, "package_name": "org.frogram.messenger", "build_number": build_number,
              "commit": commit, "artifacts": []}
    for apk in sorted(artifacts.glob("*.apk")):
        badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
        metadata = parse_badging(badging)
        with apk.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        result["artifacts"].append({"name": apk.name, "size": apk.stat().st_size, "sha256": digest, **metadata})
    if not result["artifacts"]:
        raise ValueError("No APKs to publish")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--artifacts", type=Path, default=Path("artifacts"))
    parser.add_argument("--aapt", type=Path)
    parser.add_argument("--build-number", type=int, required=True)
    parser.add_argument("--commit", required=True)
    args = parser.parse_args()
    if not args.aapt:
        properties = dict(line.split("=", 1) for line in Path("local.properties").read_text().splitlines()
                          if "=" in line and not line.startswith("#"))
        versions = dict(line.split("=", 1) for line in Path("version.properties").read_text().splitlines()
                        if "=" in line and not line.startswith("#"))
        args.aapt = Path(properties["sdk.dir"]) / "build-tools" / versions["version.build_tools"] / "aapt2"
    manifest = build_manifest(args.artifacts, args.aapt, args.build_number, args.commit)
    (args.artifacts / "update.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
