package dev.akibh.mycompanion.ai.models

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class BanglishLexiconTest {

    private val latinWord = Regex("[A-Za-z]+")

    /** Rows of tools/banglish/fixtures/starter.tsv: category, input, expected. */
    private val starter = File("../tools/banglish/fixtures/starter.tsv").readLines().drop(1)
        .filter { it.isNotBlank() }
        .map { it.split('\t') }

    @Test
    fun `every fully covered Banglish phrase in the starter corpus comes out exactly`() {
        val covered = starter.filter { (category, input, _) ->
            category == "banglish" && latinWord.findAll(input).all { BanglishLexicon.lookup(it.value) != null }
        }
        // 9 of the 10 starter phrases; "amar nam rahim" keeps a name for the model.
        assertThat(covered.size).isAtLeast(9)
        covered.forEach { (_, input, expected) ->
            val converted = latinWord.replace(input) { BanglishLexicon.lookup(it.value)!! }
            assertThat(converted).isEqualTo(expected)
        }
    }

    @Test
    fun `the words the model got wrong on the phone have their right spelling`() {
        mapOf(
            "achi" to "আছি",
            "acho" to "আছো",
            "ache" to "আছে",
            "bari" to "বাড়ি",
            "kothay" to "কোথায়",
            "ashchi" to "আসছি",
            "ta" to "টা",
            "e" to "এ",
        ).forEach { (banglish, bangla) -> assertThat(BanglishLexicon.lookup(banglish)).isEqualTo(bangla) }
    }

    @Test
    fun `lookup ignores case and leaves unknown words to the model`() {
        assertThat(BanglishLexicon.lookup("AMI")).isEqualTo("আমি")
        assertThat(BanglishLexicon.lookup("Rahim")).isNull()
        assertThat(BanglishLexicon.lookup("meeting")).isNull()
    }
}
