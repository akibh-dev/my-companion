package dev.akibh.mycompanion.quran

import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.quran.domain.AyahRef
import dev.akibh.mycompanion.quran.domain.GoTo
import dev.akibh.mycompanion.quran.domain.GoToParser
import dev.akibh.mycompanion.quran.ui.QuranSearchViewModel
import org.junit.Test

class GoToParserTest {
    // A tiny Quran: three surahs are enough to check the rules.
    private val counts = listOf(7, 286, 200)
    private val names = listOf("Al-Fatihah" to "الفاتحة", "Al-Baqarah" to "البقرة", "Ali 'Imran" to "آل عمران")

    private fun parse(text: String) = GoToParser.parse(text, counts, names)

    @Test
    fun `surah and ayah in any common form, in Arabic or Bangla digits too`() {
        val ayatulKursi = listOf(GoTo.ToAyah(AyahRef(2, 255)))
        assertThat(parse("2:255")).isEqualTo(ayatulKursi)
        assertThat(parse("2 255")).isEqualTo(ayatulKursi)
        assertThat(parse(" 2.255 ")).isEqualTo(ayatulKursi)
        assertThat(parse("٢:٢٥٥")).isEqualTo(ayatulKursi)
        assertThat(parse("২:২৫৫")).isEqualTo(ayatulKursi)
    }

    @Test
    fun `an ayah or surah that doesn't exist goes nowhere`() {
        assertThat(parse("1:8")).isEmpty()
        assertThat(parse("4:1")).isEmpty()
        assertThat(parse("0")).isEmpty()
    }

    @Test
    fun `pages, juz and surah numbers`() {
        assertThat(parse("page 50")).containsExactly(GoTo.ToPage(50))
        assertThat(parse("p604")).containsExactly(GoTo.ToPage(604))
        assertThat(parse("p605")).isEmpty()
        assertThat(parse("juz 30")).containsExactly(GoTo.ToJuz(30))
        assertThat(parse("Para 1")).containsExactly(GoTo.ToJuz(1))
        assertThat(parse("3")).containsExactly(GoTo.ToSurah(3))
    }

    @Test
    fun `surah names without the article, in English or Arabic`() {
        assertThat(parse("baqara")).containsExactly(GoTo.ToSurah(2))
        assertThat(parse("Al-Baqarah")).containsExactly(GoTo.ToSurah(2))
        assertThat(parse("imran")).containsExactly(GoTo.ToSurah(3))
        assertThat(parse("البقره")).containsExactly(GoTo.ToSurah(2))
        assertThat(parse("x")).isEmpty()
    }

    @Test
    fun `arabic words search as required prefixes, without harakat`() {
        assertThat(QuranSearchViewModel.matchQuery("ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ")).isEqualTo("الرحمن* الرحيم*")
        assertThat(QuranSearchViewModel.matchQuery("الصلاة")).isEqualTo("الصلاه*")
        assertThat(QuranSearchViewModel.matchQuery("2:255")).isNull()
        assertThat(QuranSearchViewModel.matchQuery("baqara")).isNull()
    }
}
