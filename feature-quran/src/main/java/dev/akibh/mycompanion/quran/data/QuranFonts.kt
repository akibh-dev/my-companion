package dev.akibh.mycompanion.quran.data

import android.content.Context
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.text.font.FontFamily
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream

/**
 * The Quran fonts. Each Madani page has its own QPC V2 font (one glyph per word, D22); the app
 * ships them brotli-compressed (~1/3 of the size) and unpacks a page's font the first time that
 * page is opened, into no-backup storage. The two text fonts are small and read straight from assets.
 */
@Singleton
class QuranFonts @Inject constructor(@ApplicationContext private val context: Context) {
    private val pages = LruCache<Int, FontFamily>(PAGES_IN_MEMORY)
    private val unpack = Mutex()
    private val folder get() = File(context.noBackupFilesDir, "quran-v2")

    val hafs: FontFamily by lazy { asset("quran/fonts/uthmanic_hafs_v22.ttf") }
    val nastaleeq: FontFamily by lazy { asset("quran/fonts/indopak_nastaleeq.ttf") }

    /** The font for a Madani page; unpacks it on first use (a few ms). */
    suspend fun page(page: Int): FontFamily {
        pages.get(page)?.let { return it }
        val file = unpacked(page)
        return withContext(Dispatchers.IO) { FontFamily(Typeface.createFromFile(file)) }
            .also { pages.put(page, it) }
    }

    /** Only from memory, so an already open page draws without a frame of blank text. */
    fun cached(page: Int): FontFamily? = pages.get(page)

    private suspend fun unpacked(page: Int): File = unpack.withLock {
        withContext(Dispatchers.IO) {
            val target = File(folder, "p%03d.ttf".format(page))
            if (target.length() > 0) return@withContext target
            folder.mkdirs()
            val temporary = File(folder, "${target.name}.part")
            context.assets.open("quran/v2/p%03d.ttf.br".format(page)).use { raw ->
                BrotliInputStream(raw.buffered()).use { input ->
                    temporary.outputStream().use { input.copyTo(it) }
                }
            }
            check(temporary.renameTo(target)) { "Couldn't save the font of page $page" }
            target
        }
    }

    private fun asset(path: String) = FontFamily(Typeface.createFromAsset(context.assets, path))

    private companion object {
        const val PAGES_IN_MEMORY = 12
    }
}
