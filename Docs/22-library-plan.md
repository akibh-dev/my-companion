# Plan 22: Book library (PDF, EPUB, CBZ)

A simple Calibre-style library. Books are downloaded with the browser as usual (they land in
`Download/`); in the app, Library → **Add books** picks the files. The app **links** each file
(a persistable read grant on its `content://` URI) instead of copying it, and remembers the place
read, bookmarks and reading time. Decision record: [D23](decisions.md).

## Status

| Part | Status |
|---|---|
| Module `feature-library`, Room `library.db`, DataStore `library` | Done |
| Add books (multi-select), format check by bytes, title/author/cover, duplicates | Done |
| Missing file: badge, dialog, "Locate file" (keeps place, bookmarks, stats), Remove | Done |
| Reader: EPUB (reflow), PDF (PDFium), CBZ comics, one activity for all three | Done |
| Place saved and restored (Readium Locator), page "X / Y" and % with a dotted slider | Done |
| Contents, bookmarks, search in EPUB and PDF (with highlight), look sheet | Done |
| Shelves, tags, status (To read / Reading / Finished), sort, filter, search, cover/list view | Done |
| Reading time (idle after 2 min stops counting), daily goal, streak, 30-day chart, books per year | Done |
| "Continue reading" home widget, Home tile, settings backup/restore | Done |

## How it fits

- **Engine:** Readium Kotlin Toolkit 3.1.2 (streamer + navigator + PDFium adapter). 3.2+ needs
  Kotlin 2.3/2.4 and Media3 1.10; the app is on Kotlin 2.2 and Media3 1.8.
- **Readium PDF page bug:** 3.1.2's PDFium page fragment reports the page after the one on screen
  and jumps one page early (fixed upstream in 3.4.0). `reader/FixedPdfium.kt` is a corrected copy
  plugged in through Readium's `PdfEngineProvider`. Remove it when moving to Readium 3.4+.
- **PDF search** uses Android's own `PdfRenderer.Page.searchText()` (API 35+); Readium's PDF engine
  has no search. EPUB search is Readium's.
- **Design:** the dotted Nothing-style pieces (theme, `DotText`, `DotIcon`) moved from `app` into a
  new `:design` module (same packages) so feature modules can use them.
- The reader is `BookReaderActivity` (Readium's navigators are Fragments). It never restores its
  fragments: the book reopens at its saved place.

## Checked on the phone (CMF Phone 1, 21 Nov 2026)

Added an EPUB (Standard Ebooks), a 15-page PDF (arXiv) and a generated CBZ from `Download/`;
covers came from the files. Read, closed and reopened each: same page. EPUB contents jump,
search (53 hits, highlight), text size/sepia/serif, bookmark. PDF page numbers match the printed
pages after the fix; PDF search (11 hits). Comic paging. Deleted the comic in the background:
"Missing" badge and dialog; "Locate file" relinked it and it reopened on the same page. Stats
counted reading minutes and the streak.

Not checked by hand: the widget on the home screen and restoring a backup on the phone (both covered
by unit tests).
