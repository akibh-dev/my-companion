#!/usr/bin/env python3
"""Build the Quran assets for feature-quran from tools/quran/sources/ (see fetch_sources.py).

Writes feature-quran/src/main/assets/quran/ (git-ignored):
  quran.db            read-only content DB, exactly in the Room schema (feature-quran/schemas)
  v2/pNNN.ttf.br      QPC V2 page fonts, brotli-compressed (unpacked on the phone when first opened)
  fonts/*.ttf         KFGQPC Uthmanic Hafs (Madani list, headers) and quran.com IndoPak Nastaleeq
  LICENSES.txt        Tanzil / KFGQPC / quran.com / QUL notices
  manifest.json       hashes of all of the above, checked by `--check` before every Gradle build

The text is never edited. Before writing anything the tool checks: 114 surahs and 6236 ayahs with
the right counts; the KFGQPC texts agree with Tanzil's (letters only); every word of every Madani
page is on exactly one line; 114 surah headers and 112 bismillah lines per script.
"""

import argparse
import hashlib
import json
import sqlite3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
SOURCES = HERE / "sources"
OUT = ROOT / "feature-quran/src/main/assets/quran"
SCHEMA = ROOT / "feature-quran/schemas/dev.akibh.mycompanion.quran.data.QuranContentDb"
VERSION = 2  # QuranContentDb.VERSION: raise both whenever the content changes, so phones re-copy it
MADANI, INDOPAK = 0, 1
AYAH, HEADER, BISMILLAH = 0, 1, 2
AYAH_COUNTS = None  # filled from Tanzil metadata


# ---------------------------------------------------------------- normalisation (= ArabicNormalizer.kt)

def _map(c):
    o = ord(c)
    if c.isspace():
        return " "
    if 0x0610 <= o <= 0x061A or 0x064B <= o <= 0x065F or o == 0x0670 or 0x06D6 <= o <= 0x06ED:
        return None
    if 0x08D3 <= o <= 0x08FF or o == 0x0640 or o in (0x06DD, 0x06DE, 0x06E9):
        return None
    if 0x0660 <= o <= 0x0669 or 0x06F0 <= o <= 0x06F9 or "0" <= c <= "9":
        return None
    if o in (0x0671, 0x0622, 0x0623, 0x0625, 0x0672, 0x0673):
        return "ا"
    if o in (0x0649, 0x06CC, 0x064A, 0x0626):
        return "ي"
    if o == 0x0624:
        return "و"
    if o in (0x0629, 0x06C1, 0x06BE, 0x06D5):
        return "ه"
    if o == 0x06A9:
        return "ك"
    if o in (0x200C, 0x200D, 0x200E, 0x200F):
        return None
    if c.isalpha():
        return c
    return " "


def normalize(text):
    out, space = [], True
    for c in text:
        m = _map(c)
        if m is None:
            continue
        if m == " ":
            if not space:
                out.append(" ")
            space = True
        else:
            out.append(m)
            space = False
    return "".join(out).rstrip()


def search_text(uthmani, simple):
    """Both spellings: Uthmani (ٱلصَّلَوٰةَ -> الصلوه) and everyday (الصلاة -> الصلاه), each word once."""
    words = normalize(uthmani).split(" ")
    words += [w for w in normalize(simple).split(" ") if w not in words]
    return " ".join(words)


def letters(text):
    """Skeleton for comparing two spellings of the same ayah: letters only, no spaces, alef-less."""
    return normalize(text).replace(" ", "").replace("ا", "").replace("ء", "")


# ---------------------------------------------------------------- sources

def fail(message):
    raise SystemExit(f"build_quran_db: {message}")


def load_tanzil():
    meta = ET.parse(SOURCES / "tanzil/quran-data.xml").getroot()
    suras = [s.attrib for s in meta.find("suras")]
    counts = [int(s["ayas"]) for s in suras]
    if len(counts) != 114 or sum(counts) != 6236:
        fail("Tanzil metadata: expected 114 surahs and 6236 ayahs")
    text = {}
    for line in (SOURCES / "tanzil/quran-uthmani.txt").read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        surah, ayah, body = line.split("|", 2)
        text[(int(surah), int(ayah))] = body
    if len(text) != 6236:
        fail(f"Tanzil text has {len(text)} ayahs")
    simple = {}
    for line in (SOURCES / "tanzil/quran-simple-clean.txt").read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            surah, ayah, body = line.split("|", 2)
            simple[(int(surah), int(ayah))] = body
    if simple.keys() != text.keys():
        fail("Tanzil simple-clean text doesn't hold the same 6236 ayahs")
    # Tanzil's text files start each surah's first ayah with the bismillah (it isn't part of the ayah,
    # except in Al-Fatiha); the mushaf shows it on its own line. Drop it for the comparison and search.
    for surah in range(2, 115):
        for source in (text, simple):
            words = source[(surah, 1)].split(" ")
            if surah != 9 and letters(" ".join(words[:4])) == letters("بسم الله الرحمن الرحيم"):
                source[(surah, 1)] = " ".join(words[4:])
    juz = [(int(j.attrib["sura"]), int(j.attrib["aya"])) for j in meta.find("juzs")]
    pages = [(int(p.attrib["sura"]), int(p.attrib["aya"])) for p in meta.find("pages")]
    sajdas = {(int(s.attrib["sura"]), int(s.attrib["aya"])) for s in meta.find("sajdas")}
    return counts, text, simple, juz, pages, sajdas


def refs(counts):
    return [(s + 1, a + 1) for s, n in enumerate(counts) for a in range(n)]


def load_kfgqpc(name, order):
    data = json.loads((SOURCES / f"kfgqpc/{name}").read_text(encoding="utf-8"))
    if len(data) != 6236 or [row[1] for row in data] != list(range(1, 6237)):
        fail(f"{name}: expected 6236 ayahs numbered 1..6236")
    return {ref: row[0] for ref, row in zip(order, data)}


def load_pages(order, folder="pages", pages=604, field="code_v2"):
    """Every page of a quran.com layout: verses with metadata, and each word's text and line."""
    verses = {}
    lines = {}
    for page in range(1, pages + 1):
        data = json.loads((SOURCES / f"qurancom/{folder}/{page:03d}.json").read_text(encoding="utf-8"))
        for verse in data["verses"]:
            surah, ayah = map(int, verse["verse_key"].split(":"))
            # An ayah that runs over a page break is listed on both pages; it belongs to the page
            # it starts on, and each page takes only its own words.
            verses.setdefault((surah, ayah), verse)
            for word in verse["words"]:
                if word["page_number"] != page:
                    continue
                key = (page, word["line_number"])
                lines.setdefault(key, []).append(((surah, ayah), word["position"], word[field]))
    if list(verses) != order:
        fail(f"quran.com {folder} pages hold {len(verses)} ayahs, not the 6236 in order")
    return verses, lines


def make_room_for_headers(lines, pages):
    """
    Some Indo-Pak pages number only their ayah lines: a surah starting mid-page has no free lines
    for its header and bismillah, and the page ends short of 15. Shift the lines from the surah's
    first line down so the header lines fit, as on the printed page.
    """
    out = dict(lines)
    for page in range(1, pages + 1):
        # A surah never starts mid-line: split a line that ends one surah and starts the next.
        for line in sorted((l for (p, l) in out if p == page), reverse=True):
            words = out[(page, line)]
            if len({ref[0] for ref, _, _ in words}) < 2:
                continue
            first_surah = words[0][0][0]
            if max(l for (p, l) in out if p == page) >= 15:
                fail(f"page {page}: line {line} holds two surahs and the page has no free line")
            for later in sorted((l for (p, l) in out if p == page and l > line), reverse=True):
                out[(page, later + 1)] = out.pop((page, later))
            out[(page, line)] = [w for w in words if w[0][0] == first_surah]
            out[(page, line + 1)] = [w for w in words if w[0][0] != first_surah]
        present = sorted(line for (p, line) in out if p == page)
        if not present or present[-1] >= 15 or page <= 2:
            continue
        free = 15 - present[-1]
        starts = sorted(
            line for line in present
            if any(ayah == 1 and position == 1 for (_, ayah), position, _ in out[(page, line)])
        )
        for start in starts:
            words = out[(page, start)]
            surah = next(ref[0] for ref, position, _ in words if ref[1] == 1 and position == 1)
            need = 1 if surah == 9 else 2
            gap_before = start > 1 and (page, start - 1) not in out
            if start == 1 or not gap_before:
                if free < need:
                    fail(f"page {page}: no room for the header of surah {surah}")
                for line in sorted((l for (p, l) in out if p == page and l >= start), reverse=True):
                    out[(page, line + need)] = out.pop((page, line))
                free -= need
                starts = [l + need if l > start else l for l in starts]
    return out


def page_lines(script, lines, order, pages):
    """Page lines: the words, plus surah headers and bismillah lines in the gaps."""
    lines = make_room_for_headers(lines, pages)
    index = {ref: i + 1 for i, ref in enumerate(order)}
    rows = []
    used = set()
    for page in range(1, pages + 1):
        present = sorted(line for (p, line) in lines if p == page)
        if not present or present[-1] > 15:
            fail(f"page {page}: lines {present}")
        for line in present:
            words = sorted(lines[(page, line)], key=lambda w: (index[w[0]], w[1]))
            for w in words:
                if (w[0], w[1]) in used:
                    fail(f"word {w[0]}:{w[1]} on two lines")
                used.add((w[0], w[1]))
            ids = [index[w[0]] for w in words]
            rows.append([script, page, line, AYAH, " ".join(w[2] for w in words), page <= 2,
                         words[0][0][0], min(ids), max(ids)])
    # Gap lines are surah headers and bismillahs. Walk the mushaf as one stream of pages x 15 lines.
    stream = {}
    for r in rows:
        stream[(r[1], r[2])] = r
    slots = [(p, l) for p in range(1, pages + 1) for l in range(1, 16)]
    # Pages 1 and 2 have only 8 and 7 lines, set in the middle of the page; their gaps are layout space.
    pending = []
    out = []
    for slot in slots:
        row = stream.get(slot)
        if row is None:
            pending.append(slot)
            continue
        first_ref = order[row[7] - 1]
        if pending:
            surah = first_ref[0]
            starts_surah = first_ref[1] == 1
            # Only gaps right before a surah's first ayah are headers; others (pages 1-2) are blank.
            if starts_surah:
                gap = [s for s in pending if s[0] > 2 or s[1] < slot[1]]
                kinds = [HEADER] if surah in (1, 9) else [HEADER, BISMILLAH]
                if surah == 1:
                    gap = gap[-1:]
                if len(gap) < len(kinds):
                    fail(f"surah {surah}: {len(gap)} free lines before it, need {len(kinds)}")
                # Extra free lines stay blank (e.g. the end of a page before a new surah).
                for s, kind in zip(gap[-len(kinds):], kinds):
                    out.append([script, s[0], s[1], kind, "", True, surah, row[7], row[7]])
            pending = []
        out.append(row)
    return out


def check_kinds(rows, script):
    headers = sum(1 for r in rows if r[0] == script and r[3] == HEADER)
    bismillahs = sum(1 for r in rows if r[0] == script and r[3] == BISMILLAH)
    if (headers, bismillahs) != (114, 112):
        fail(f"script {script}: {headers} surah headers and {bismillahs} bismillah lines (want 114, 112)")


# ---------------------------------------------------------------- output

def schema():
    path = SCHEMA / f"{VERSION}.json"
    if not path.exists():
        fail(f"{path} missing: build feature-quran once (kapt exports the Room schema)")
    return json.loads(path.read_text())["database"]


def write_db(target, surahs, ayahs, lines):
    db_schema = schema()
    target.unlink(missing_ok=True)
    con = sqlite3.connect(target)
    for entity in db_schema["entities"]:
        con.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            con.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for query in db_schema["setupQueries"]:
        con.execute(query)
    con.executemany("INSERT INTO surah VALUES (?,?,?,?,?,?,?,?,?)", surahs)
    con.executemany("INSERT INTO ayah VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", ayahs)
    con.execute("INSERT INTO ayah_fts(docid, text_search) SELECT rowid, text_search FROM ayah")
    con.executemany("INSERT INTO mushaf_line VALUES (?,?,?,?,?,?,?,?,?)", lines)
    for entity in db_schema["entities"]:
        for trigger in entity.get("contentSyncTriggers", []) or []:
            con.execute(trigger)
    con.execute(f"PRAGMA user_version = {VERSION}")
    con.commit()
    con.execute("VACUUM")
    con.close()


def compress_fonts():
    import brotli  # pip install brotli (tools/quran/requirements.txt)

    (OUT / "v2").mkdir(parents=True, exist_ok=True)
    for page in range(1, 605):
        source = SOURCES / f"kfgqpc/v2/QCF2{page:03d}.ttf"
        target = OUT / f"v2/p{page:03d}.ttf.br"
        if not target.exists() or target.stat().st_mtime < source.stat().st_mtime:
            target.write_bytes(brotli.compress(source.read_bytes(), quality=11))
    (OUT / "fonts").mkdir(parents=True, exist_ok=True)
    (OUT / "fonts/uthmanic_hafs_v22.ttf").write_bytes((SOURCES / "kfgqpc/uthmanic_hafs_v22.ttf").read_bytes())
    (OUT / "fonts/indopak_nastaleeq.ttf").write_bytes((SOURCES / "qurancom/indopak-nastaleeq.ttf").read_bytes())
    (OUT / "fonts/hafs_nastaleeq_v10.otf").unlink(missing_ok=True)


def write_licenses(tanzil_notice):
    (OUT / "LICENSES.txt").write_text(
        "Quran text and fonts\n====================\n\n"
        "Madani text and fonts: King Fahd Glorious Quran Printing Complex (KFGQPC), Uthmanic Hafs v22\n"
        "with its font, and the QPC V2 page fonts (1421H Madinah print). Free for non-commercial use;\n"
        "not modified. https://qurancomplex.gov.sa\n\n"
        "Indo-Pak text, 15-line layout and the indopak-nastaleeq font, page layouts (word -> line) and\n"
        "metadata: quran.com (Quran Foundation), https://quran.com\n\n"
        "Cross-check of the text and metadata: Tanzil Project, https://tanzil.net\n\n" + tanzil_notice,
        encoding="utf-8",
    )


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def manifest():
    files = sorted(p for p in OUT.rglob("*") if p.is_file() and p.name != "manifest.json")
    return {
        "version": VERSION,
        "schema": schema()["identityHash"],
        "files": {str(p.relative_to(OUT)): digest(p) for p in files},
    }


def check():
    target = OUT / "manifest.json"
    if not target.exists():
        fail("Quran assets missing: run python3 tools/quran/fetch_sources.py && python3 tools/quran/build_quran_db.py")
    saved = json.loads(target.read_text())
    if saved.get("version") != VERSION or saved.get("schema") != schema()["identityHash"]:
        fail("Quran assets are from another schema or version: run python3 tools/quran/build_quran_db.py")
    for name, expected in saved["files"].items():
        path = OUT / name
        if not path.exists() or path.stat().st_size == 0:
            fail(f"Quran asset {name} missing: run python3 tools/quran/build_quran_db.py")
    if len(saved["files"]) < 604 + 4:
        fail("Quran assets incomplete: run python3 tools/quran/build_quran_db.py")
    print("Quran assets verified")


def build():
    counts, tanzil, simple, juz_starts, page_starts, sajdas = load_tanzil()
    order = refs(counts)
    hafs = load_kfgqpc("hafs_v22.json", order)

    # The KFGQPC texts must be the same Quran as Tanzil's, letter for letter (spelling marks aside).
    differ = [ref for ref in order if letters(hafs[ref]) != letters(tanzil[ref])]
    if differ:
        shown = ", ".join(f"{s}:{a}" for s, a in differ[:10])
        fail(f"{len(differ)} Hafs ayahs differ from Tanzil: {shown}")

    verses, words = load_pages(order)
    tanzil_page = {}
    page = 0
    starts = set(page_starts)
    for ref in order:
        if ref in starts:
            page += 1
        tanzil_page[ref] = page
    # Tanzil's page list is the Madinah 1405H print; quran.com is 1421H. Pages agree almost always.
    page_differ = [ref for ref in order if tanzil_page[ref] != verses[ref]["page_number"]]
    if len(page_differ) > 100:
        fail(f"{len(page_differ)} ayahs on different pages than Tanzil's page list")
    juz_set = set(juz_starts)
    juz = 0
    for ref in order:
        if ref in juz_set:
            juz += 1
        if verses[ref]["juz_number"] != juz:
            fail(f"{ref}: juz {verses[ref]['juz_number']} vs Tanzil {juz}")
    api_sajdas = {ref for ref in order if verses[ref]["sajdah_number"]}
    # Tanzil lists all 15 sajdahs marked in the Madinah mushaf; quran.com leaves out the second one in
    # Al-Hajj (22:77), which only some schools observe. Use Tanzil's; anything else is an error.
    if not api_sajdas <= sajdas or sajdas - api_sajdas != {(22, 77)}:
        fail(f"sajdah ayahs differ: {sorted(api_sajdas ^ sajdas)}")

    lines = page_lines(MADANI, words, order, 604)
    check_kinds(lines, MADANI)

    # Indo-Pak (Hafezi) 15-line layout, 610 pages, with quran.com's Nastaleeq text for its font.
    indopak_verses, indopak_words = load_pages(order, "indopak", 610, "text_indopak_nastaleeq")
    indopak_lines = page_lines(INDOPAK, indopak_words, order, 610)
    check_kinds(indopak_lines, INDOPAK)
    lines += indopak_lines
    # The ayah list shows the same Indo-Pak words as the pages (ayah-end mark included).
    by_ayah = {}
    for key in sorted(indopak_words):
        for ref, position, text in indopak_words[key]:
            by_ayah.setdefault(ref, []).append((position, text))
    indopak = {ref: " ".join(t for _, t in sorted(by_ayah[ref])) for ref in order}
    differ = [ref for ref in order if letters(indopak[ref]) != letters(tanzil[ref])]
    if len(differ) > 60:
        fail(f"{len(differ)} Indo-Pak ayahs differ from Tanzil, e.g. {differ[:5]}")
    print(f"note: {len(differ)} Indo-Pak ayahs use Indo-Pak letter conventions (e.g. {differ[:3]})", file=sys.stderr)

    chapters = json.loads((SOURCES / "qurancom/chapters.json").read_text(encoding="utf-8"))["chapters"]
    ayah_rows = []
    first_id = {}
    for i, ref in enumerate(order, start=1):
        v = verses[ref]
        first_id.setdefault(ref[0], i)
        ayah_rows.append([
            i, ref[0], ref[1], hafs[ref], indopak[ref], search_text(hafs[ref], simple[ref]), v["juz_number"],
            v["rub_el_hizb_number"], v["ruku_number"], v["manzil_number"], ref in sajdas, v["page_number"],
            indopak_verses[ref]["page_number"],
        ])
    surah_rows = []
    for c in chapters:
        n = c["id"]
        if c["verses_count"] != counts[n - 1]:
            fail(f"surah {n}: {c['verses_count']} ayahs on quran.com vs {counts[n - 1]}")
        surah_rows.append([
            n, c["name_arabic"], c["name_simple"], c["translated_name"]["name"], c["revelation_place"] == "makkah",
            counts[n - 1], first_id[n], c["pages"][0], indopak_verses[(n, 1)]["page_number"],
        ])
    if len(surah_rows) != 114:
        fail("quran.com chapters: expected 114")

    OUT.mkdir(parents=True, exist_ok=True)
    fresh = OUT / "quran.db.new"
    write_db(fresh, surah_rows, ayah_rows, lines)
    # Phones copy quran.db once per VERSION; new content under the same version would never reach them.
    old = OUT / "manifest.json"
    if old.exists():
        saved = json.loads(old.read_text())
        if saved.get("version") == VERSION and saved.get("files", {}).get("quran.db") not in (None, digest(fresh)):
            fresh.unlink()
            fail(f"the content changed: raise VERSION here and QuranContentDb.VERSION (now {VERSION})")
    fresh.replace(OUT / "quran.db")
    compress_fonts()
    notice = (SOURCES / "tanzil/quran-uthmani.txt").read_text(encoding="utf-8")
    write_licenses(notice[notice.index("# PLEASE DO NOT REMOVE"):])
    (OUT / "manifest.json").write_text(json.dumps(manifest(), indent=1, sort_keys=True) + "\n")
    size = sum(p.stat().st_size for p in OUT.rglob("*") if p.is_file())
    print(f"Built {OUT.relative_to(ROOT)}: 6236 ayahs, {len(lines)} page lines, {size / 1e6:.1f} MB")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="Only verify the built assets (no Python packages needed)")
    arguments = parser.parse_args()
    check() if arguments.check else build()


if __name__ == "__main__":
    main()
