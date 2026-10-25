package com.akib.mycompanion.export

import com.akib.mycompanion.autoreply.data.AutoReplyRepository
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.PlaylistRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * "Export my data" (NFR-11): everything the app keeps, as readable JSON files in one ZIP. The vault is
 * left out on purpose: it has its own encrypted backup, and an export here would be unencrypted.
 */
class DataExporter @Inject constructor(
    private val contacts: ContactRepository,
    private val reminders: ReminderRepository,
    private val playlists: PlaylistRepository,
    private val context: ContextRepository,
    private val autoReply: AutoReplyRepository,
    private val settings: SettingsBackup,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** File name → JSON text, in the order they go into the ZIP. */
    suspend fun files(): Map<String, String> {
        val people = contacts.observeAll().first()
        val lists = playlists.observePlaylists().first().associateWith { playlists.items(it.id) }
        val places = context.places.first()
        return linkedMapOf(
            "README.txt" to README,
            "people.json" to ExportJson.people(people).toString(INDENT),
            "reminders.json" to ExportJson.reminders(reminders.getPending(), people, clock.zone).toString(INDENT),
            "playlists.json" to ExportJson.playlists(lists).toString(INDENT),
            "rules.json" to ExportJson.rules(context.rules.first(), places).toString(INDENT),
            "places.json" to ExportJson.places(places).toString(INDENT),
            "auto-reply-templates.json" to ExportJson.templates(autoReply.templates.first()).toString(INDENT),
            "settings.json" to settings.exportJson(),
        )
    }

    /** Writes the ZIP to [output] (a file the user picked) and returns how many files went in. */
    suspend fun writeZip(output: OutputStream): Int {
        val files = files()
        withContext(io) { zip(files, output) }
        return files.size
    }

    companion object {
        private const val INDENT = 2

        val README = """
            My Companion — your data
            ========================

            Plain JSON files, one per area: people, reminders (upcoming), playlists, context rules,
            saved places, auto-reply templates and settings.

            Not included: the vault. It has its own encrypted backup (Vault > Trash & backup), so
            its contents never leave the phone unencrypted. The auto-reply log and captures aren't
            included either.

            settings.json can be restored in the app: Home > Export & backup > Restore settings.
        """.trimIndent() + "\n"

        /** Plain ZIP writing, separate so it's easy to test. */
        fun zip(files: Map<String, String>, output: OutputStream) {
            ZipOutputStream(output).use { zip ->
                files.forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
    }
}
