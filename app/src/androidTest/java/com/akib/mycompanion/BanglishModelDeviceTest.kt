package com.akib.mycompanion

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.ai.models.IndicXlitPrototype
import com.akib.mycompanion.ai.models.TransliterationPreview
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Real native ONNX inference. Run explicitly after fetching the pinned model assets. */
@RunWith(AndroidJUnit4::class)
class BanglishModelDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prototype = IndicXlitPrototype(instrumentation.targetContext)

    @Test
    fun modelProducesCompleteBanglaWordsAndPreservesPunctuation() = runBlocking<Unit> {
        val result = prototype.preview("ami, bangla!")
        assertThat(result).isInstanceOf(TransliterationPreview.Ready::class.java)
        result as TransliterationPreview.Ready
        assertThat(result.text).contains(", ")
        assertThat(result.text).endsWith("!")
        assertThat(result.text.any { it in 'A'..'Z' || it in 'a'..'z' }).isFalse()
        assertThat(result.words.first().candidates).contains("আমি")
        Log.i("BanglishEvaluation", "load=${result.loadMillis} ms inference=${result.inferenceMillis} ms")
    }

    @Test
    fun invalidAndNonLatinInputDoNotLoadTheModel() = runBlocking<Unit> {
        assertThat(prototype.preview(" ")).isInstanceOf(TransliterationPreview.Unavailable::class.java)
        assertThat(prototype.preview("a".repeat(601))).isInstanceOf(TransliterationPreview.Unavailable::class.java)
        assertThat(prototype.preview("a".repeat(49))).isInstanceOf(TransliterationPreview.Unavailable::class.java)
        assertThat(prototype.preview("আমি 123 😊")).isEqualTo(
            TransliterationPreview.Ready("আমি 123 😊", emptyList(), 0, 0),
        )
    }

    @Test
    fun evaluateStarterCorpusAndWriteReport() = runBlocking<Unit> {
        val report = StringBuilder("category\tinput\texpected\tactual\tload_ms\tinference_ms\n")
        val lines = instrumentation.context.assets.open("starter.tsv").bufferedReader().use { it.readLines() }
        for (line in lines.drop(1).filter { it.isNotBlank() }) {
            val fields = line.split('\t')
            val result = prototype.preview(fields[1])
            assertThat(result).isInstanceOf(TransliterationPreview.Ready::class.java)
            result as TransliterationPreview.Ready
            report.appendLine(
                (fields + listOf(result.text, result.loadMillis, result.inferenceMillis)).joinToString("\t")
            )
        }
        val destination = File(instrumentation.targetContext.filesDir, "banglish-evaluation.tsv")
        destination.writeText(report.toString())
        Log.i("BanglishEvaluation", "Saved ${lines.size - 1} starter cases to ${destination.name}")
    }
}
