# Plan 21 — Quran reader (Arabic only), `feature-quran`

## Context
You want a Quran section inside My Companion that works like a good Quran app: read, jump, search,
bookmark, track a khatm. It shows Arabic text only, with no audio and no translation. The text should
be stored as text, ayah by ayah, not as PDF pages or page images. Your choices so far:
- **Both reading views, switchable.** The ayah list is reflowing text with adjustable size. Mushaf
  pages are exact printed-page layouts.
- **Both scripts, switchable.** Madani (Uthmani) and Indo-Pak (Hafezi).
- **v1 extras:** bookmarks and last read, a khatm/reading goal, a daily reminder, and a home widget
  or tile. More features can come later.

### What files we need (no decompiling required)
Quran apps don't hide anything special. The text, page layouts and fonts are all published openly
for this purpose, so decompiling an app would only get us an older copy of the same files.

| Need | File type | Source |
|---|---|---|
| Verified Arabic text, per ayah | Plain text / XML (`sura\|aya\|text`) | **Tanzil.net** Uthmani text (CC BY 3.0: copy verbatim, keep the notice, never edit) |
| Search text without harakat | Plain text | Tanzil "simple-clean", or our own normalisation of the Uthmani text |
| Metadata: surah names, juz, hizb/rub, manzil, ruku, page, sajdah | XML/JS (`quran-data`) | Tanzil metadata |
| Word-by-word text + **mushaf page layouts** (which words sit on each line of each page) | **SQLite** files | **QUL** (Quranic Universal Library, qul.tarteel.ai). Layouts: Madani V2 1421H, 604 pages × 15 lines; IndoPak 15/16/13-line |
| Madani page font | 604 per-page **TTF** glyph fonts (QPC V2: one glyph = one word) | QUL / King Fahd Complex (KFGQPC) |
| Ayah-list fonts | 1 TTF each: **KFGQPC Uthmanic Hafs**; **IndoPak Nastaleeq** | KFGQPC / QUL |

The fonts are free for personal, non-commercial use. A build for your own phone only is within those
terms. Each license goes in `assets/licenses/`, and an "About the text" screen credits Tanzil, QUL and
KFGQPC.

## Status (2026-11-21)
| Phase | State |
|---|---|
| 0 Data | Done: `tools/quran/fetch_sources.py` (pinned in `sources.lock.json`) and `build_quran_db.py` (integrity checks pass). Indo-Pak 15-line layout comes from quran.com (mushaf 6), so no QUL sign-in is needed. |
| 1 Home, index, ayah list, last read | Done, checked on the phone |
| 2 Mushaf pages | Done for both scripts: exact QPC V2 Madani fonts and Indo-Pak Nastaleeq, fitted to the screen (size from the 80th-percentile line, long lines shrink, short lines centred), pinch zoom and − / + buttons |
| 3 Search and go-to | Done (both spellings: الصلاة and ٱلصَّلَوٰةَ) |
| 4 Bookmarks + backup | Done: notes, Bookmarks tab, in the settings backup (older backups leave bookmarks alone) |
| 5 Khatm, reminder, widget, tile | Done: khatm counts pages read for 20 s; the daily reminder is a normal reminder with a link (DB v6); widget and Quick Settings tile open the last place |

Lessons from the phone: the theme's letter spacing spread Arabic words apart (Arabic text now uses none); the Indo-Pak layout sometimes leaves no lines for a surah header, or puts two surahs on one line, and the build tool repairs both; Indo-Pak copies number Al-Fatiha without the bismillah, so its ayah marks are one lower than Madani's.

Size: the page fonts are 118 MB brotli-compressed (more than the 70–100 MB estimate), the database
5.5 MB; the debug APK is about 250 MB with the IndicXlit models. A "download page fonts later"
option would bring the Quran back to about 6 MB if that becomes a problem.

Building the assets:
```bash
pip install -r tools/quran/requirements.txt   # brotli, pytest
python3 tools/quran/fetch_sources.py           # ~210 MB into tools/quran/sources (git-ignored)
python3 tools/quran/build_quran_db.py          # writes feature-quran/src/main/assets/quran
python3 -m pytest tools/quran
```

## Approach

### Data pipeline (like the IndicXlit models)
- `tools/quran/build_quran_db.py` reads the downloaded sources from a git-ignored `tools/quran/sources/`
  and writes `feature-quran/src/main/assets/quran/`:
  - `quran.db`: a prepackaged read-only SQLite file, built in the exact schema Room expects.
  - `fonts/`: the Madani per-page fonts, plus the Hafs and IndoPak fonts.
  - `SHA256SUMS`.
- These assets are git-ignored. A `verifyQuranData` Exec task runs before `preBuild`, copying
  `verifyBanglishModels` in `ai-models/build.gradle.kts`.
- **Integrity checks inside the tool** stop the build on any mismatch:
  - 114 surahs, 6236 ayahs, and the per-surah ayah counts;
  - the Uthmani text hash matches Tanzil's file byte for byte;
  - every layout page's word range covers each word exactly once;
  - every surah header and bismillah line sits in the right place.
- The text is never edited. Normalised text is stored in a separate column used only for search.

### Database schema (`quran.db`, read-only, opened with Room `createFromAsset`)
- `surah(number, name_ar, name_translit, name_bn, ayah_count, revelation, start_page)`
- `ayah(id, surah, number, text_uthmani, text_indopak, juz, hizb_quarter, manzil, ruku, sajdah, page_madani, page_indopak)`
- `ayah_fts`: FTS4 over the normalised text, joined to `ayah`.
- `word(id, surah, ayah, position, glyph_madani, text_indopak)`
- `layout_line(script, page, line, type[ayah|surah_name|bismillah], centered, first_word, last_word, surah)`

User data goes in a **separate** Room DB, `quran_user.db`: `bookmark`, `reading_session`, `khatm_plan`,
plus last-read in DataStore. Because the content DB holds no user data, it can be swapped for a newer
text version without migrations: bump the asset version and Room falls back destructively for that DB only.

### Module `feature-quran`
Uses the template of `feature-context/build.gradle.kts`, including the Room dependencies.
- `domain/`, pure Kotlin:
  - `AyahRef(surah, ayah)` and `MushafScript { MADANI, INDOPAK }`;
  - `ReadingMode { AYAH_LIST, MUSHAF }`;
  - `ArabicNormalizer`, which strips harakat and Quranic marks, unifies alef/ya/ta-marbuta and removes tatweel;
  - `GoTo` parser: `2:255`, `2 255`, surah names in Arabic/English/Bangla, `page 50`, `juz 30`;
  - khatm math: pages per day and progress.
- `data/`: `QuranContentDb` (createFromAsset), `QuranUserDb`, `QuranRepository`, and `QuranFonts`, which
  loads and caches per-page `Typeface`s from assets in an LRU of about 10 pages.
- `ui/`:
  - **Quran home:** a "continue reading" card, a khatm progress card, and tabs for Surah / Juz / Bookmarks.
  - **Reader:**
    - The **Mushaf** view is an RTL `HorizontalPager` over the pages. Each line is drawn from
      `layout_line`. Madani lines use that page's glyph font. IndoPak lines use the Nastaleeq font,
      justified to the line width. Surah header frames and bismillah lines are drawn too.
    - The **ayah list** view is a `LazyColumn` with ayah-end markers ۝ in Arabic-Indic numerals, plus
      a font-size slider.
    - Toggles switch the script and the mode, and the position carries across views by mapping the
      ayah to its page.
    - Juz, hizb and sajdah markers are shown, and the screen can stay on while reading.
    - Long-pressing an ayah offers: bookmark (with a note), copy (adds "— Surah 2:255") or share text.
  - **Search:** a debounced FTS query, using the same pattern as `PlaceEditorViewModel`
    (`feature-context/.../ui/PlacesScreen.kt`). Results show the matched words highlighted, plus a
    "Go to" row when the query parses as a reference.
  - **Settings:** default script and mode, font size, night page colour, keep screen on.

### App wiring (reuse existing hooks)
- Add `include(":feature-quran")` in `settings.gradle.kts`, and `implementation(project(":feature-quran"))`
  in `app/build.gradle.kts`.
- Add a screen: `@Provides @IntoSet Screen("quran", ...)` in
  `app/.../di/FeatureScreensModule.kt`, with sub-routes for the reader and search.
- Add a Home tool tile, like "AI engines" in `presentation/home/HomeScreen.kt`, with a new
  `DotSymbol` (book) in `ui/components/DotAlphabet.kt`. It does **not** get a 5th tab, because the
  design keeps 4.
- **Backup:**
  - Export `quran-bookmarks.json` through `DataExporter.files()` and `ExportJson`.
  - Make bookmarks and the khatm plan restorable by extending `SettingsBundle`, `parseSettings` and
    `restore`, with `SETTINGS_VERSION` raised to 2 (older backups still restore).
- **Daily reminder:** create a reminder in the existing Reminders engine with a deep link into the
  reader, rather than building a second alarm system.
- **Widget/tile:**
  - a "Continue reading" home widget showing surah:ayah, page and today's goal progress, built on the
    same tech as the quick-actions widget;
  - a Quick Settings tile that opens the last-read page.

### Khatm / reading goal
The plan is set with a start and an end date, or with pages per day. Progress counts the distinct
mushaf pages viewed for at least about 20 s, so flicking past a page doesn't count. The plan screen
shows pages left, today's target and the streak. Progress is counted in Madani pages, and IndoPak
pages are mapped to them through their ayahs.

## Phases (each its own branch → local merge into main, per current workflow)
0. **Data spike:** download the sources and write `build_quran_db.py` with its integrity checks.
   Measure the size; the estimate is +25–45 MB. Then render one Madani page and one IndoPak page on
   the phone, so you can choose the IndoPak line layout (13 or 15 lines) by eye. Record the
   decision as D22 in `Docs/decisions.md`, and write `Docs/21-quran-plan.md`.
1. **Module, content DB, Quran home (surah/juz index), ayah list reader, last read.**
2. **Mushaf page view in both scripts, with mode and script switching.**
3. **Search and Go-to.**
4. **Bookmarks and backup.**
5. **Khatm goal, daily reminder, widget and tile.**

Before phase 0: the Google Maps work on `feat/google-maps-places` is still uncommitted and waiting
on your key decision. It needs to be committed on its branch, or parked, before the Quran branch
starts from main.

## Verification
- **Tool (pytest):** the counts and hashes above, and the layout coverage check.
- **Unit tests (JUnit/Truth):**
  - `ArabicNormalizer`, with shared test vectors also run by the Python tool so query and index
    normalisation can't drift apart;
  - the `GoTo` parser;
  - khatm math;
  - the ayah↔page mapping in both scripts.
- **Robolectric:**
  - `createFromAsset` opens, and all 6236 ayahs are present;
  - FTS: searching `الرحمن` without harakat finds 1:1, 1:3 and 55:1;
  - bookmark DAO, backup round trip, and a v1 backup still restoring;
  - Compose: the reader shows the right page after a go-to.
- **On the phone:**
  - pages 1, 2, 50, 604 and a sajdah page match a printed Madani mushaf;
  - IndoPak spot checks;
  - pager scrolling stays smooth;
  - cold start of the reader;
  - the widget and tile open the last-read page;
  - the reminder fires and deep-links in.
- **Full check:** `./gradlew ktlintFormat ktlintCheck testDebugUnitTest lintDebug assembleDebug --continue --max-workers=3`.
