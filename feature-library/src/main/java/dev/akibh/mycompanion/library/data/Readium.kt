package dev.akibh.mycompanion.library.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.akibh.mycompanion.library.domain.BookFormat
import javax.inject.Inject
import javax.inject.Singleton
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.FileExtension
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.format.FormatHints
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

sealed interface OpenResult {
    data class Opened(val publication: Publication) : OpenResult

    /** Readium couldn't open the file; [message] is for the log, the UI checks availability. */
    data class Failed(val message: String) : OpenResult
}

/** Opens linked books (content:// URIs) with Readium, without copying them. */
@Singleton
class Readium @Inject constructor(@ApplicationContext private val context: Context) {
    private val http = DefaultHttpClient()
    private val assets = AssetRetriever(context.contentResolver, http)
    private val opener = PublicationOpener(
        DefaultPublicationParser(context, http, assets, PdfiumDocumentFactory(context)),
    )

    /** [rightToLeft] turns a comic's pages from right to left (manga), whatever the file says. */
    suspend fun open(uri: Uri, format: BookFormat, rightToLeft: Boolean = false): OpenResult {
        val url = uri.toAbsoluteUrl() ?: return OpenResult.Failed("Not a file link: $uri")
        val hints = FormatHints(mediaType = format.mediaType, fileExtension = FileExtension(format.extension))
        val asset = assets.retrieve(url, hints).getOrElse { return OpenResult.Failed(it.message) }
        val publication = opener.open(
            asset,
            allowUserInteraction = false,
            onCreatePublication = {
                if (format == BookFormat.CBZ && rightToLeft) {
                    manifest = manifest.copy(
                        metadata = manifest.metadata.copy(
                            readingProgression = org.readium.r2.shared.publication.ReadingProgression.RTL,
                        ),
                    )
                }
            },
        ).getOrElse {
            asset.close()
            return OpenResult.Failed(it.message)
        }
        return OpenResult.Opened(publication)
    }

    private val BookFormat.mediaType: MediaType
        get() = when (this) {
            BookFormat.PDF -> MediaType.PDF
            BookFormat.EPUB -> MediaType.EPUB
            BookFormat.CBZ -> MediaType.CBZ
        }
}
