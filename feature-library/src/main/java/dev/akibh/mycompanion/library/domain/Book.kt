package dev.akibh.mycompanion.library.domain

/** The file types the library opens. */
enum class BookFormat(val label: String, val mimeType: String, val extension: String) {
    PDF("PDF", "application/pdf", "pdf"),
    EPUB("EPUB", "application/epub+zip", "epub"),
    CBZ("Comic", "application/vnd.comicbook+zip", "cbz"),
    ;

    companion object {
        /**
         * What the file picker offers. Browsers often save comics (and sometimes books) as plain zip
         * or "octet-stream", so those are offered too and the bytes decide.
         */
        val PICKER_TYPES = arrayOf(
            PDF.mimeType,
            EPUB.mimeType,
            CBZ.mimeType,
            "application/x-cbz",
            "application/zip",
            "application/octet-stream",
        )

        /** The EPUB container rule: a zip whose first entry is "mimetype" holding this text. */
        private val EPUB_MARK = "mimetypeapplication/epub+zip".toByteArray()
        private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        private val PDF_MAGIC = "%PDF-".toByteArray()

        /**
         * The format from the file's first bytes ([head], 1 KB is plenty), its [name] and [mimeType].
         * The bytes win: a ".pdf" that isn't a PDF is rejected rather than failing later in the reader.
         */
        fun detect(name: String?, mimeType: String?, head: ByteArray): BookFormat? {
            val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
            if (head.indexOf(PDF_MAGIC) >= 0) return PDF
            if (!head.startsWith(ZIP_MAGIC)) return null
            return when {
                head.indexOf(EPUB_MARK) >= 0 -> EPUB
                extension == "epub" || mimeType == EPUB.mimeType -> EPUB
                extension in setOf("cbz", "zip") -> CBZ
                mimeType == CBZ.mimeType || mimeType == "application/x-cbz" -> CBZ
                else -> null
            }
        }

        private fun ByteArray.startsWith(prefix: ByteArray) =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

        private fun ByteArray.indexOf(needle: ByteArray): Int {
            for (start in 0..size - needle.size) {
                if (needle.indices.all { this[start + it] == needle[it] }) return start
            }
            return -1
        }
    }
}

enum class BookStatus(val label: String) {
    TO_READ("To read"),
    READING("Reading"),
    FINISHED("Finished"),
}

/** Whether the linked file can still be opened. */
enum class Availability {
    OK,

    /** The file isn't there any more (deleted, or moved to another folder). */
    MISSING,

    /** The app can't read it: the file was deleted or moved, or the permission was lost (a restore). */
    NO_ACCESS,
}

/** A shelf groups books ("Course books"); a tag describes them ("history"). Both are user-made. */
enum class LabelKind { SHELF, TAG }

data class Label(val id: Long, val name: String, val kind: LabelKind)

/** A book in the library: a link to a file plus where reading stopped. */
data class Book(
    val id: Long,
    val uri: String,
    val format: BookFormat,
    val title: String,
    val author: String,
    val coverPath: String?,
    val fileName: String,
    val size: Long,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    /** Readium Locator JSON of the last place read, null before the first read. */
    val locator: String?,
    /** 0..1 through the whole book. */
    val progress: Double,
    /** Pages (PDF, comics) or Readium positions (EPUB, about a screen each). */
    val positions: Int,
    val status: BookStatus,
    val finishedAt: Long?,
    val labels: Set<Long> = emptySet(),
)

/** "1.2 MB", for the details sheet and the missing-file dialog. */
fun Long.fileSize(): String = when {
    this >= 1L shl 20 -> "%.1f MB".format(this / (1024.0 * 1024.0))
    this >= 1L shl 10 -> "${this / 1024} KB"
    else -> "$this B"
}
