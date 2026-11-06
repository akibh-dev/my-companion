#!/usr/bin/env python3
"""Fetch pinned IndicXlit ONNX graphs. The app itself never downloads models."""

import argparse
import hashlib
from pathlib import Path
import urllib.request


REVISION = "235fe2976d3e5c3c7d883af8cdfac4a83463d4d9"
BASE_URL = f"https://raw.githubusercontent.com/cloudrumbles/indic-transliterate/{REVISION}/models"
DESTINATION = Path(__file__).resolve().parents[2] / "ai-models/src/main/assets/indicxlit"
FILES = {
    "indicxlit_encoder.onnx": "468cfd76d779ec3bebcc7aa10f852b7f66de2c5133feba3537d18496db58c6c0",
    "indicxlit_decoder_v2.onnx": "b811bcad8521849f17c7a02997465093639d7bbc76fae49c1b137b8f72df8f83",
    "vocab.json": "f711ffc2b2ab97e85486798beb8b344ff80b48692aef46557f40c600df1dc697",
}


def digest(path):
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            checksum.update(block)
    return checksum.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify local files without network access")
    arguments = parser.parse_args()
    for name, expected in FILES.items():
        target = DESTINATION / name
        if target.exists() and digest(target) == expected:
            print(f"Verified {name}")
            continue
        if arguments.check:
            raise SystemExit(f"Missing or invalid {name}; run python3 tools/banglish/fetch_models.py")
        DESTINATION.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(target.suffix + ".download")
        try:
            print(f"Downloading {name} at {REVISION}")
            with urllib.request.urlopen(f"{BASE_URL}/{name}", timeout=120) as response:
                with temporary.open("wb") as output:
                    while block := response.read(1024 * 1024):
                        output.write(block)
            if digest(temporary) != expected:
                raise SystemExit(f"Checksum mismatch: {name}; existing file was not replaced")
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
