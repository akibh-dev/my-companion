package dev.akibh.mycompanion.export

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.R
import dev.akibh.mycompanion.data.local.AppDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Android's own backup: the database must travel with its write-ahead log, or recent writes are lost. */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    /** Section name → included database paths. */
    private fun includes(xml: Int): Map<String, List<String>> {
        val parser = app.resources.getXml(xml)
        val found = mutableMapOf<String, MutableList<String>>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "include") {
                if (parser.getAttributeValue(null, "domain") == "database") {
                    found.getOrPut(section) { mutableListOf() } += parser.getAttributeValue(null, "path")
                }
            } else {
                section = parser.name
            }
        }
        return found
    }

    @Test
    fun `every backup takes the database together with its write-ahead log`() {
        val db = AppDatabase.NAME
        val sections = includes(R.xml.backup_rules) + includes(R.xml.data_extraction_rules)

        assertThat(sections.keys).containsExactly("full-backup-content", "cloud-backup", "device-transfer")
        sections.values.forEach { assertThat(it).containsExactly(db, "$db-wal") }
    }
}
