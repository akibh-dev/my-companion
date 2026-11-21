package dev.akibh.mycompanion.library

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.library.domain.BookFormat
import org.junit.Test

class BookFormatTest {
    private val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(26)
    private val epub = zip + "mimetypeapplication/epub+zip".toByteArray()

    @Test
    fun `the bytes decide, whatever the name says`() {
        assertThat(
            BookFormat.detect("book.pdf", "application/pdf", "%PDF-1.7\n".toByteArray())
        ).isEqualTo(BookFormat.PDF)
        assertThat(BookFormat.detect("download", "application/octet-stream", "%PDF-1.4".toByteArray()))
            .isEqualTo(BookFormat.PDF)
        assertThat(BookFormat.detect("fake.pdf", "application/pdf", "<html>".toByteArray())).isNull()
    }

    @Test
    fun `a PDF header after some junk still counts`() {
        assertThat(
            BookFormat.detect("scan.pdf", null, ByteArray(40) + "%PDF-1.3".toByteArray())
        ).isEqualTo(BookFormat.PDF)
    }

    @Test
    fun `zips are EPUB by their mimetype entry, comics by name or type`() {
        assertThat(BookFormat.detect("novel.bin", "application/octet-stream", epub)).isEqualTo(BookFormat.EPUB)
        assertThat(BookFormat.detect("novel.epub", null, zip)).isEqualTo(BookFormat.EPUB)
        assertThat(BookFormat.detect("issue 1.cbz", "application/octet-stream", zip)).isEqualTo(BookFormat.CBZ)
        assertThat(BookFormat.detect("x", "application/vnd.comicbook+zip", zip)).isEqualTo(BookFormat.CBZ)
        assertThat(BookFormat.detect("photos.rar", "application/octet-stream", zip)).isNull()
    }
}
