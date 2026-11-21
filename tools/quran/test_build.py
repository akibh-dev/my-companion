"""python3 -m pytest tools/quran (or python3 tools/quran/test_build.py)."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_quran_db as b  # noqa: E402


def cases():
    for line in (Path(__file__).resolve().parent / "normalizer_cases.tsv").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            source, expected = line.split("\t")
            yield source, expected


def test_normalizer_matches_kotlin_cases():
    for source, expected in cases():
        assert b.normalize(source) == expected, (source, b.normalize(source))


if __name__ == "__main__":
    test_normalizer_matches_kotlin_cases()
    print("ok")
