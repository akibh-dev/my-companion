package dev.akibh.mycompanion.library.reader

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search

/** One place a search found: the words around it and where to go. */
data class SearchHit(val locator: Locator, val before: String, val match: String, val after: String, val where: String)

object BookSearch {
    const val MAX_HITS = 300
    private const val CONTEXT = 40

    /** EPUB: Readium's search through every chapter. */
    suspend fun epub(publication: Publication, query: String, onHits: (List<SearchHit>) -> Unit) {
        val iterator = publication.search(query) ?: return
        val hits = mutableListOf<SearchHit>()
        try {
            while (hits.size < MAX_HITS) {
                val page = iterator.next().getOrNull() ?: break
                page.locators.forEach { locator ->
                    hits += SearchHit(
                        locator = locator,
                        before = locator.text.before.orEmpty().takeLast(CONTEXT).trimStart(),
                        match = locator.text.highlight.orEmpty(),
                        after = locator.text.after.orEmpty().take(CONTEXT).trimEnd(),
                        where = locator.title.orEmpty(),
                    )
                }
                onHits(hits.toList())
            }
        } finally {
            iterator.close()
        }
    }

    /**
     * PDF: Android's own PDF text search (API 35+), page by page; Readium's PDF engine has none.
     * [pages] are the publication's positions, one per page, to turn a page number into a Locator.
     */
    suspend fun pdf(
        context: Context,
        uri: Uri,
        pages: List<Locator>,
        query: String,
        onHits: (List<SearchHit>) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val hits = mutableListOf<SearchHit>()
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    ensureActive()
                    val found = renderer.openPage(index).use { page ->
                        val matches = page.searchText(query)
                        if (matches.isEmpty()) return@use emptyList()
                        // Match offsets count the page's text pieces joined as they are; line breaks become
                        // spaces (same length) so snippets read as one line.
                        val text = page.textContents.joinToString("") { it.text.orEmpty() }
                            .replace('\n', ' ').replace('\r', ' ')
                        matches.map { match ->
                            val start = match.textStartIndex.coerceIn(0, text.length)
                            val end = (start + query.length).coerceAtMost(text.length)
                            SearchHit(
                                locator = pages.getOrNull(index) ?: return@map null,
                                before = text.substring((start - CONTEXT).coerceAtLeast(0), start).trimStart(),
                                match = text.substring(start, end),
                                after = text.substring(end, (end + CONTEXT).coerceAtMost(text.length)).trimEnd(),
                                where = "Page ${index + 1}",
                            )
                        }.filterNotNull()
                    }
                    if (found.isNotEmpty()) {
                        hits += found
                        onHits(hits.take(MAX_HITS))
                        if (hits.size >= MAX_HITS) break
                    }
                }
            }
        }
    }
}
