/*
 * Adapted from Readium's PdfiumDocumentFragment and PdfiumEngineProvider (readium/kotlin-toolkit 3.1.2,
 * Copyright 2022 Readium Foundation, BSD-3-Clause, see Docs/decisions.md D23).
 *
 * Readium 3.1.2 mixes up page numbers and page indexes in its PDF page fragment: it reports the page
 * after the one on screen and jumps to the page before the one asked for, and it can never report the
 * last page. Readium 3.4.0 fixes it, but needs Kotlin 2.4 and Media3 1.10. Until the app moves up,
 * this copy keeps page indexes 0-based in both directions, the way 3.4.0 does.
 */
@file:OptIn(InternalReadiumApi::class, ExperimentalReadiumApi::class)

package dev.akibh.mycompanion.library.reader

import android.graphics.PointF
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumPreferencesEditor
import org.readium.adapter.pdfium.navigator.PdfiumSettings
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfDocumentFragment
import org.readium.r2.navigator.pdf.PdfDocumentFragmentInput
import org.readium.r2.navigator.pdf.PdfEngineProvider
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.util.SingleFragmentFactory
import org.readium.r2.navigator.util.createFragmentFactory
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.InternalReadiumApi
import org.readium.r2.shared.publication.LocalizedString
import org.readium.r2.shared.publication.Manifest
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.SingleJob
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.getOrElse

/** Readium's PDFium engine with [FixedPdfiumFragment] for pages; settings and preferences are Readium's own. */
internal class FixedPdfiumEngineProvider(
    private val readium: PdfiumEngineProvider = PdfiumEngineProvider(),
) : PdfEngineProvider<PdfiumSettings, PdfiumPreferences, PdfiumPreferencesEditor> {

    override fun createDocumentFragmentFactory(
        input: PdfDocumentFragmentInput<PdfiumSettings>,
    ): SingleFragmentFactory<FixedPdfiumFragment> = createFragmentFactory {
        FixedPdfiumFragment(
            publication = input.publication,
            href = input.href,
            initialPageIndex = input.pageIndex,
            initialSettings = input.settings,
            onTap = { point -> input.inputListener?.onTap(TapEvent(point)) ?: false },
        )
    }

    override fun computeSettings(metadata: Metadata, preferences: PdfiumPreferences): PdfiumSettings =
        readium.computeSettings(metadata, preferences)

    override fun computeOverflow(settings: PdfiumSettings): OverflowableNavigator.Overflow =
        readium.computeOverflow(settings)

    override fun createPreferenceEditor(
        publication: Publication,
        initialPreferences: PdfiumPreferences,
    ): PdfiumPreferencesEditor = readium.createPreferenceEditor(publication, initialPreferences)

    override fun createEmptyPreferences(): PdfiumPreferences = readium.createEmptyPreferences()
}

/** One PDF document on a [PDFView]; [pageIndex] and [goToPageIndex] are 0-based. */
internal class FixedPdfiumFragment(
    private val publication: Publication,
    private val href: Url,
    private val initialPageIndex: Int,
    initialSettings: PdfiumSettings,
    private val onTap: (PointF) -> Boolean,
) : PdfDocumentFragment<PdfiumSettings>() {

    // Android may try to recreate it; the reader never restores fragments, so this is never shown.
    constructor() : this(
        publication = Publication(
            Manifest(metadata = Metadata(identifier = "dummy", localizedTitle = LocalizedString("")))
        ),
        href = Url("publication.pdf")!!,
        initialPageIndex = 0,
        initialSettings = PdfiumSettings(Fit.WIDTH, 0.0, ReadingProgression.LTR, Axis.VERTICAL),
        onTap = { false },
    )

    private lateinit var pdfView: PDFView
    private lateinit var resetJob: SingleJob
    private var pageCount = 0
    private var settings: PdfiumSettings = initialSettings

    private val _pageIndex = MutableStateFlow(initialPageIndex)
    override val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        PDFView(inflater.context, null).also { pdfView = it }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resetJob = SingleJob(viewLifecycleOwner.lifecycleScope)
        reset(pageIndex = initialPageIndex)
    }

    private fun reset(pageIndex: Int = _pageIndex.value) {
        if (view == null) return
        val context = context?.applicationContext ?: return
        resetJob.launch {
            val resource = requireNotNull(publication.get(href))
            val document = PdfiumDocumentFactory(context).open(resource, null).getOrElse { error ->
                Log.e(TAG, "Couldn't open the PDF: ${error.message}")
                return@launch
            }
            pageCount = document.pageCount
            val page = toView(pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
            pdfView.recycle()
            pdfView
                .fromSource { _, _, _ -> document.document }
                .apply {
                    // PDFView has no right-to-left mode: give it the pages in reverse.
                    if (reversed) pages(*((pageCount - 1) downTo 0).toList().toIntArray())
                }
                .swipeHorizontal(settings.scrollAxis == Axis.HORIZONTAL)
                .spacing(settings.pageSpacing.roundToInt())
                .defaultPage(page)
                .onRender {
                    if (settings.fit == Fit.WIDTH) {
                        pdfView.fitToWidth(page)
                        // fitToWidth often loses defaultPage, so jump there again.
                        pdfView.jumpTo(page, false)
                    }
                }
                .onPageChange { index, _ -> _pageIndex.value = fromView(index) }
                .onTap { event -> onTap(PointF(event.x, event.y)) }
                .load()
        }
    }

    override fun goToPageIndex(index: Int, animated: Boolean): Boolean {
        if (index !in 0 until pageCount) return false
        pdfView.jumpTo(toView(index), animated)
        return true
    }

    override fun applySettings(settings: PdfiumSettings) {
        if (this.settings == settings) return
        this.settings = settings
        reset()
    }

    private val reversed: Boolean
        get() = settings.scrollAxis == Axis.HORIZONTAL && settings.readingProgression == ReadingProgression.RTL

    private fun toView(index: Int): Int = if (reversed) pageCount - 1 - index else index

    private fun fromView(index: Int): Int = if (reversed) pageCount - 1 - index else index

    private companion object {
        const val TAG = "FixedPdfium"
    }
}
