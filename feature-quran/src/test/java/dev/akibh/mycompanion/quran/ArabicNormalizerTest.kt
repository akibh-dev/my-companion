package dev.akibh.mycompanion.quran

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.akibh.mycompanion.quran.domain.ArabicNormalizer
import java.io.File
import org.junit.Test

/** Search normalisation; the same cases run against the Python index builder (tools/quran/test_build.py). */
class ArabicNormalizerTest {

    @Test
    fun `the shared cases normalise the same way as the search index`() {
        val cases = File("../tools/quran/normalizer_cases.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t').let { (input, expected) -> input to expected } }
        assertThat(cases).isNotEmpty()
        for ((input, expected) in cases) {
            assertWithMessage(input).that(ArabicNormalizer.normalize(input)).isEqualTo(expected)
        }
    }

    @Test
    fun `latin text and digits don't survive, words stay apart`() {
        assertThat(ArabicNormalizer.normalize("2:255")).isEmpty()
        assertThat(ArabicNormalizer.normalize("قُلۡ هُوَ ٱللَّهُ أَحَدٌ")).isEqualTo("قل هو الله احد")
    }
}
