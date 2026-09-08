import importlib.util
import pathlib
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("manifest", pathlib.Path(__file__).resolve().parents[2] / "scripts/create-update-manifest.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
BADGING = """package: name='org.frogram.messenger' versionCode='1879302' versionName='0.28.11.1879-arm64-v8a'
sdkVersion:'24'
native-code: 'arm64-v8a'
"""


class ManifestTest(unittest.TestCase):
    def test_apk_data(self):
        result = module.parse_badging(BADGING)
        self.assertEqual(result["version_code"], 1879302)
        self.assertEqual(result["abis"], ["arm64-v8a"])
        self.assertEqual(result["min_sdk"], 24)

    def test_wrong_package(self):
        with self.assertRaises(ValueError):
            module.parse_badging(BADGING.replace("org.frogram.messenger", "org.thunderdog.challegram"))

    def test_old_version(self):
        with self.assertRaises(ValueError):
            module.parse_badging(BADGING.replace("1879302", "1796302"))

    def test_missing_abi(self):
        with self.assertRaises(ValueError):
            module.parse_badging(BADGING.replace("native-code:", "ignored:"))

    def test_no_apks(self):
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(ValueError):
            module.build_manifest(pathlib.Path(directory), pathlib.Path("aapt2"), 83, "commit")

    def test_digest_and_size_come_from_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory)
            (path / "app.apk").write_bytes(b"abc")
            with patch.object(module.subprocess, "check_output", return_value=BADGING):
                result = module.build_manifest(path, pathlib.Path("aapt2"), 83, "commit")
            artifact = result["artifacts"][0]
            self.assertEqual(artifact["size"], 3)
            self.assertEqual(artifact["sha256"], "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            self.assertEqual(artifact["version_code"], 1879302)


if __name__ == "__main__":
    unittest.main()
