#!/usr/bin/env python3
"""Download the Quran sources into tools/quran/sources/ (git-ignored). The app never downloads them.

Sources (all published for reuse; see Docs/21-quran-plan.md):
- Tanzil Uthmani + simple-clean text and metadata: cross-check and search (CC BY 3.0, verbatim only).
- KFGQPC Uthmanic Hafs v22: the Madani ayah text with its matching font.
- KFGQPC QPC V2 per-page fonts (1421H Madani print): exact mushaf pages, one glyph per word.
- quran.com API v4: for every Madani page, each word's V2 glyph and line, plus juz/hizb/ruku/sajdah.
- quran.com qdc API, mushaf 6: the Indo-Pak 15-line (Hafezi) layout, 610 pages, with its Nastaleeq
  text and quran.com's matching indopak-nastaleeq font.

Every file is pinned in sources.lock.json (SHA-256). `--check` only verifies, offline.
"""

import argparse
import hashlib
import json
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCES = HERE / "sources"
LOCK = HERE / "sources.lock.json"

QPC = "https://raw.githubusercontent.com/nuqayah/qpc-fonts/8a4f39d563ea69c994416a1692827e38156c548d"
TANZIL = "https://tanzil.net/pub/download/index.php?" + urllib.parse.urlencode(
    {"quranType": "uthmani", "outType": "txt-2", "agree": "true", "marks": "true", "sajdah": "true", "tatweel": "true"}
)
# Standard (imla'i) spelling, so a search typed the everyday way (الصلاة) finds the Uthmani ٱلصَّلَوٰةَ.
TANZIL_SIMPLE = "https://tanzil.net/pub/download/index.php?" + urllib.parse.urlencode(
    {"quranType": "simple-clean", "outType": "txt-2", "agree": "true"}
)
API = "https://api.quran.com/api/v4"
# quran.com's own API, which also serves the Indo-Pak 15-line (Hafezi) layout: mushaf 6, 610 pages.
QDC = "https://api.qurancdn.com/api/qdc"
QCOM = "https://raw.githubusercontent.com/quran/quran.com-frontend-next/aff1a035b09b66f28047b3216edcae4c5c949a49"
PAGES = 604
INDOPAK_PAGES = 610


def files():
    yield "tanzil/quran-uthmani.txt", TANZIL
    yield "tanzil/quran-simple-clean.txt", TANZIL_SIMPLE
    yield "tanzil/quran-data.xml", "https://tanzil.net/res/text/metadata/quran-data.xml"
    yield "kfgqpc/hafs_v22.json", f"{QPC}/text-mushafs/UthmanicHafs_V22/UthmanicHafs%20v22.json"
    yield "kfgqpc/uthmanic_hafs_v22.ttf", f"{QPC}/text-mushafs/UthmanicHafs_V22/uthmanic_hafs_v22.ttf"
    yield "kfgqpc/v2/QCF2BSML.ttf", f"{QPC}/mushaf-v2/QCF2BSML.ttf"
    for page in range(1, PAGES + 1):
        yield f"kfgqpc/v2/QCF2{page:03d}.ttf", f"{QPC}/mushaf-v2/QCF2{page:03d}.ttf"
    yield "qurancom/chapters.json", f"{API}/chapters"
    for page in range(1, PAGES + 1):
        query = urllib.parse.urlencode(
            {"words": "true", "word_fields": "code_v2,line_number,page_number,char_type_name", "per_page": 50}
        )
        yield f"qurancom/pages/{page:03d}.json", f"{API}/verses/by_page/{page}?{query}"
    yield "qurancom/indopak-nastaleeq.ttf", f"{QCOM}/public/fonts/quran/hafs/nastaleeq/indopak/indopak-nastaleeq-waqf-lazim-v4.2.1.ttf"
    for page in range(1, INDOPAK_PAGES + 1):
        query = urllib.parse.urlencode(
            {"mushaf": 6, "words": "true", "word_fields": "text_indopak_nastaleeq,line_number,page_number,char_type_name",
             "per_page": 50}
        )
        yield f"qurancom/indopak/{page:03d}.json", f"{QDC}/verses/by_page/{page}?{query}"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def download(url, target):
    request = urllib.request.Request(url, headers={"User-Agent": "quran-sources-fetcher"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                body = response.read()
            break
        except OSError as error:
            if attempt == 3:
                raise SystemExit(f"Download failed: {url}: {error}")
            time.sleep(2 ** attempt)
    if target.suffix == ".json" and ("qurancom/pages" in str(target) or "qurancom/indopak" in str(target)):
        data = json.loads(body)
        if data["pagination"]["total_pages"] != 1:
            raise SystemExit(f"{url}: more than one result page; raise per_page")
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".download")
    temporary.write_bytes(body)
    temporary.replace(target)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="Verify local files against the lock, offline")
    parser.add_argument("--update-lock", action="store_true", help="Record the current files' hashes")
    arguments = parser.parse_args()

    lock = json.loads(LOCK.read_text()) if LOCK.exists() else {}
    problems = []
    for name, url in files():
        target = SOURCES / name
        expected = lock.get(name)
        if target.exists() and (expected is None or digest(target) == expected):
            continue
        if arguments.check:
            problems.append(name)
            continue
        print(f"Downloading {name}", flush=True)
        download(url, target)
        if url.startswith((API, QDC)):
            time.sleep(0.15)  # be polite to the public API
        if expected and digest(target) != expected and not arguments.update_lock:
            problems.append(f"{name} (checksum changed upstream)")
    if problems:
        shown = "\n  ".join(problems[:20])
        raise SystemExit(f"{len(problems)} missing or changed source files:\n  {shown}\nRun python3 tools/quran/fetch_sources.py")
    if arguments.update_lock or not lock:
        lock = {name: digest(SOURCES / name) for name, _ in files()}
        LOCK.write_text(json.dumps(lock, indent=1, sort_keys=True) + "\n")
        print(f"Locked {len(lock)} files in {LOCK.name}")
    print("Quran sources verified")


if __name__ == "__main__":
    sys.exit(main())
