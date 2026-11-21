package dev.akibh.mycompanion.quran

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.quran.data.QuranContentDb
import dev.akibh.mycompanion.quran.data.QuranRepository
import dev.akibh.mycompanion.quran.domain.AyahRef
import dev.akibh.mycompanion.quran.domain.LineKind
import dev.akibh.mycompanion.quran.domain.MushafScript
import dev.akibh.mycompanion.quran.ui.QuranSearchViewModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** The bundled quran.db (built by tools/quran/build_quran_db.py) opens through Room and holds the whole Quran. */
@RunWith(AndroidJUnit4::class)
class QuranContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = QuranContentDb.create(context)
    private val quran = QuranRepository(db.dao())

    @After
    fun close() = db.close()

    private suspend fun search(text: String) =
        quran.search(requireNotNull(QuranSearchViewModel.matchQuery(text)), 500).map { it.ref.toString() }

    @Test
    fun `all 114 surahs and 6236 ayahs, in order`() = runTest {
        val surahs = quran.surahs()
        assertThat(surahs).hasSize(114)
        assertThat(surahs.sumOf { it.ayahCount }).isEqualTo(6236)
        assertThat(quran.all(MushafScript.MADANI).map { it.id }).isEqualTo((1..6236).toList())
        assertThat(quran.ayah(AyahRef(2, 255))!!.page).isEqualTo(42)
        assertThat(
            quran.juzList().map { it.firstAyah.toString() }.take(3)
        ).containsExactly("1:1", "2:142", "2:253").inOrder()
    }

    @Test
    fun `search finds words without harakat, in Uthmani or everyday spelling`() = runTest {
        assertThat(search("الرحمن الرحيم")).containsAtLeast("1:1", "1:3", "27:30")
        assertThat(search("ٱلرَّحۡمَٰنُ")).contains("55:1")
        assertThat(search("الصلاة")).contains("2:3")
        assertThat(search("الصلوة")).contains("2:3")
        assertThat(search("قل هو الله احد")).containsExactly("112:1")
    }

    @Test
    fun `madani pages have 15 lines and every surah its header`() = runTest {
        val page2 = quran.page(MushafScript.MADANI, 2)
        assertThat(page2.first().kind).isEqualTo(LineKind.SURAH_HEADER)
        assertThat(page2[1].kind).isEqualTo(LineKind.BISMILLAH)
        val page3 = quran.page(MushafScript.MADANI, 3)
        assertThat(page3.map { it.line }).isEqualTo((1..15).toList())
        assertThat(page3.all { it.kind == LineKind.AYAH && it.text.isNotBlank() }).isTrue()
        val headers = (1..604).sumOf { page ->
            quran.page(MushafScript.MADANI, page).count {
                it.kind == LineKind.SURAH_HEADER
            }
        }
        assertThat(headers).isEqualTo(114)
    }
}
