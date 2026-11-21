@file:OptIn(ExperimentalReadiumApi::class)

package dev.akibh.mycompanion.library.reader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import dev.akibh.mycompanion.library.data.BookFont
import dev.akibh.mycompanion.library.data.PageTheme
import dev.akibh.mycompanion.library.data.ReaderLook
import dev.akibh.mycompanion.library.data.Spacing
import dev.akibh.mycompanion.library.domain.BookFormat
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/** Builds the Readium navigator fragment for each format, with the reader's look applied. */
internal object Navigators {

    fun factory(session: ReaderSession, look: ReaderLook, dark: Boolean): FragmentFactory =
        when (session.book.format) {
            BookFormat.EPUB -> EpubNavigatorFactory(session.publication).createFragmentFactory(
                initialLocator = session.initial,
                initialPreferences = epub(look, dark),
            )
            BookFormat.PDF -> PdfNavigatorFactory(
                session.publication,
                FixedPdfiumEngineProvider()
            ).createFragmentFactory(
                initialLocator = session.initial,
                initialPreferences = pdf(look),
            )
            BookFormat.CBZ -> ImageNavigatorFragment.createFactory(session.publication, session.initial)
        }

    fun fragmentClass(format: BookFormat): Class<out Fragment> = when (format) {
        BookFormat.EPUB -> EpubNavigatorFragment::class.java
        BookFormat.PDF -> PdfNavigatorFragment::class.java
        BookFormat.CBZ -> ImageNavigatorFragment::class.java
    }

    /** Applies a changed look to a running navigator (comics have nothing to change). */
    @Suppress("UNCHECKED_CAST")
    fun submit(fragment: Fragment?, look: ReaderLook, dark: Boolean) {
        when (fragment) {
            is EpubNavigatorFragment -> fragment.submitPreferences(epub(look, dark))
            is PdfNavigatorFragment<*, *> ->
                (fragment as PdfNavigatorFragment<*, PdfiumPreferences>).submitPreferences(pdf(look))
        }
    }

    /** Whether the page itself is dark, so the controls over it match. */
    fun pageIsDark(look: ReaderLook, systemDark: Boolean): Boolean = when (look.theme) {
        PageTheme.AUTO -> systemDark
        PageTheme.DARK -> true
        PageTheme.LIGHT, PageTheme.SEPIA -> false
    }

    /** The page's own background, shown around it (the camera cutout strip). Sepia is for EPUB text only. */
    fun pageColor(look: ReaderLook, systemDark: Boolean, format: BookFormat?): Int = when {
        look.theme == PageTheme.SEPIA && format == BookFormat.EPUB -> 0xFFFAF4E8.toInt()
        pageIsDark(look, systemDark) -> 0xFF000000.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    fun epub(look: ReaderLook, dark: Boolean): EpubPreferences {
        val custom = look.font != BookFont.ORIGINAL || look.spacing != Spacing.NORMAL
        return EpubPreferences(
            fontSize = look.fontScale,
            fontFamily = when (look.font) {
                BookFont.ORIGINAL -> null
                BookFont.SERIF -> FontFamily.SERIF
                BookFont.SANS -> FontFamily.SANS_SERIF
            },
            theme = when (look.theme) {
                PageTheme.AUTO -> if (dark) Theme.DARK else Theme.LIGHT
                PageTheme.LIGHT -> Theme.LIGHT
                PageTheme.SEPIA -> Theme.SEPIA
                PageTheme.DARK -> Theme.DARK
            },
            // Line height and margins only apply once the book's own styles step aside.
            publisherStyles = !custom,
            lineHeight = look.spacing.lineHeight.takeIf { custom },
            pageMargins = look.spacing.margins,
            scroll = look.scroll,
        )
    }

    fun pdf(look: ReaderLook) = PdfiumPreferences(
        fit = if (look.pdfFitWidth) Fit.WIDTH else Fit.CONTAIN,
        scrollAxis = if (look.pdfVertical) Axis.VERTICAL else Axis.HORIZONTAL,
    )
}
