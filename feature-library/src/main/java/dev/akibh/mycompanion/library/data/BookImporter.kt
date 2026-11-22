package dev.akibh.mycompanion.library.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.akibh.mycompanion.core.time.Clock
import dev.akibh.mycompanion.library.domain.BookFormat
import dev.akibh.mycompanion.library.domain.BookStatus
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.positions

/** What adding a batch of files did, for the snackbar. */
data class ImportReport(
    val added: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val failed: List<Pair<String, String>> = emptyList(),
)

/** The answer to "Locate file". */
sealed interface RelinkResult {
    data object Done : RelinkResult

    /** The picked file has another name or size: maybe another book. Ask before linking. */
    data class LooksDifferent(val fileName: String, val size: Long) : RelinkResult

    data class Failed(val reason: String) : RelinkResult
}

/** The file's name, size and format, read through the content provider. */
data class FileInfo(val name: String, val size: Long, val format: BookFormat?)

/** Links picked files into the library: keeps read access, reads title, author and cover. */
@Singleton
class BookImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryDao,
    private val readium: Readium,
    private val clock: Clock,
) {
    private val resolver get() = context.contentResolver

    suspend fun import(uris: List<Uri>): ImportReport = withContext(Dispatchers.IO) {
        var report = ImportReport()
        for (uri in uris) {
            val info = runCatching { info(uri) }.getOrElse {
                if (it is CancellationException) throw it
                val name = uri.lastPathSegment.orEmpty()
                report = report.copy(failed = report.failed + (name to "Couldn't read the file"))
                continue
            }
            val existing = dao.bookByUri(uri.toString()) ?: dao.bookByFile(info.name, info.size)
            when {
                existing != null -> report = report.copy(duplicates = report.duplicates + existing.title)
                info.format == null ->
                    report = report.copy(failed = report.failed + (info.name to "Not a PDF, EPUB or CBZ comic"))
                else -> when (val result = add(uri, info, info.format)) {
                    null -> report = report.copy(added = report.added + info.name)
                    else -> report = report.copy(failed = report.failed + (info.name to result))
                }
            }
        }
        report
    }

    /** Adds one checked file; returns why it failed, or null. */
    private suspend fun add(uri: Uri, info: FileInfo, format: BookFormat): String? {
        if (!keepAccess(uri)) return "Android didn't allow keeping access to it"
        val publication = when (val opened = readium.open(uri, format)) {
            is OpenResult.Opened -> opened.publication
            is OpenResult.Failed -> {
                releaseAccess(uri)
                return "Couldn't open it (damaged, or locked with DRM)"
            }
        }
        try {
            val meta = publication.metadata
            dao.insert(
                BookEntity(
                    uri = uri.toString(),
                    format = format.name,
                    title = meta.title?.takeIf { it.isNotBlank() } ?: info.name.substringBeforeLast('.'),
                    author = meta.authors.joinToString(", ") { it.name },
                    coverPath = saveCover(publication),
                    fileName = info.name,
                    size = info.size,
                    addedAt = clock.nowMillis(),
                    positions = runCatching { publication.positions().size }.getOrDefault(0),
                    status = BookStatus.TO_READ.name,
                ),
            )
        } finally {
            publication.close()
        }
        return null
    }

    /**
     * Points a book at a newly picked file, keeping its progress, bookmarks and stats. A file with
     * another name or size needs [force] (the user confirmed it's the same book).
     */
    suspend fun relink(bookId: Long, uri: Uri, force: Boolean = false): RelinkResult = withContext(Dispatchers.IO) {
        val book = dao.book(bookId) ?: return@withContext RelinkResult.Failed("The book is gone")
        val info = runCatching { info(uri) }.getOrElse {
            if (it is CancellationException) throw it
            return@withContext RelinkResult.Failed("Couldn't read that file")
        }
        if (info.format?.name != book.format) {
            val kind = BookFormat.valueOf(book.format).label
            return@withContext RelinkResult.Failed("That file isn't the same kind of book ($kind)")
        }
        dao.bookByUri(uri.toString())?.takeIf { it.id != bookId }?.let {
            return@withContext RelinkResult.Failed("That file is already in the library as \"${it.title}\"")
        }
        if (!force && (info.name != book.fileName || info.size != book.size)) {
            return@withContext RelinkResult.LooksDifferent(info.name, info.size)
        }
        if (!keepAccess(uri)) return@withContext RelinkResult.Failed("Android didn't allow keeping access to it")
        if (book.uri != uri.toString()) releaseAccess(Uri.parse(book.uri))
        dao.update(book.copy(uri = uri.toString(), fileName = info.name, size = info.size))
        RelinkResult.Done
    }

    /** Forgets a book: its row (bookmarks and shelves go with it), cover and file access. Stats stay. */
    suspend fun remove(bookId: Long) = withContext(Dispatchers.IO) {
        val book = dao.book(bookId) ?: return@withContext
        dao.delete(bookId)
        book.coverPath?.let { File(it).delete() }
        releaseAccess(Uri.parse(book.uri))
    }

    /** Android keeps at most about 512 file grants per app; the library warns near the limit. */
    fun grantsUsed(): Int = resolver.persistedUriPermissions.size

    fun info(uri: Uri): FileInfo {
        var name = uri.lastPathSegment.orEmpty()
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                it.getString(0)?.let { value -> name = value }
                if (!it.isNull(1)) size = it.getLong(1)
            }
        }
        val head = resolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(HEAD_BYTES)
            var read = 0
            while (read < buffer.size) {
                val n = stream.read(buffer, read, buffer.size - read)
                if (n < 0) break
                read += n
            }
            buffer.copyOf(read)
        } ?: ByteArray(0)
        return FileInfo(name, size, BookFormat.detect(name, resolver.getType(uri), head))
    }

    private fun keepAccess(uri: Uri): Boolean = try {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        false
    }

    private fun releaseAccess(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Already gone.
        }
    }

    /** A small JPEG of the cover (or first page), kept so the grid never opens the books. */
    private suspend fun saveCover(publication: Publication): String? {
        val bitmap = runCatching { publication.cover() }.getOrNull() ?: return null
        val scaled = if (bitmap.height > COVER_HEIGHT) {
            bitmap.scale((bitmap.width * COVER_HEIGHT / bitmap.height).coerceAtLeast(1), COVER_HEIGHT)
        } else {
            bitmap
        }
        val dir = File(context.filesDir, "library/covers").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it) }
        return file.absolutePath
    }

    private companion object {
        const val HEAD_BYTES = 1024
        const val COVER_HEIGHT = 480
        const val COVER_QUALITY = 85
    }
}
