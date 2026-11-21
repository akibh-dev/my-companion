package dev.akibh.mycompanion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import dev.akibh.mycompanion.MainActivity
import dev.akibh.mycompanion.R
import dev.akibh.mycompanion.di.ApplicationScope
import dev.akibh.mycompanion.library.data.LibrarySummaries
import dev.akibh.mycompanion.library.data.LibrarySummary
import dev.akibh.mycompanion.library.reader.BookReaderActivity
import dev.akibh.mycompanion.presentation.home.LIBRARY_ROUTE
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Home-screen widget: the book being read and today's minutes; a tap opens it where reading stopped. */
@AndroidEntryPoint
class LibraryWidget : AppWidgetProvider() {

    @Inject lateinit var summaries: LibrarySummaries

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending: PendingResult? = goAsync()
        scope.launch {
            try {
                manager.updateAppWidget(appWidgetIds, views(context, summaries.summary.first()))
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        /** Redraws every placed widget; does nothing when none is on the home screen. */
        fun refresh(context: Context, summary: LibrarySummary) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, LibraryWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, summary))
        }

        fun views(context: Context, summary: LibrarySummary): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_library).apply {
                setTextViewText(R.id.widget_library_title, summary.title)
                setTextViewText(R.id.widget_library_detail, summary.detail)
                val cover = summary.coverPath?.let(::coverBitmap)
                if (cover != null) {
                    setImageViewBitmap(R.id.widget_library_cover, cover)
                    setViewVisibility(R.id.widget_library_cover, View.VISIBLE)
                    setViewVisibility(R.id.widget_library_icon, View.GONE)
                } else {
                    setViewVisibility(R.id.widget_library_cover, View.GONE)
                    setViewVisibility(R.id.widget_library_icon, View.VISIBLE)
                }
                // With a book: straight into the reader (it shows the missing-file screen if needed).
                val open = summary.bookId?.let {
                    BookReaderActivity.intent(context, it)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                } ?: MainActivity.openRouteIntent(context, LIBRARY_ROUTE)
                setOnClickPendingIntent(
                    R.id.widget_library,
                    PendingIntent.getActivity(
                        context,
                        REQUEST_OPEN,
                        open,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }

        /** The saved cover, shrunk: widget bitmaps travel to the launcher in a size-limited parcel. */
        private fun coverBitmap(path: String): Bitmap? {
            val file = File(path).takeIf { it.exists() } ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= COVER_HEIGHT) sample *= 2
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        private const val COVER_HEIGHT = 150
        private const val REQUEST_OPEN = 11
    }
}
