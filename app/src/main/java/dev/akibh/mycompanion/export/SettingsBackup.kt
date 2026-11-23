package dev.akibh.mycompanion.export

import dev.akibh.mycompanion.autoreply.data.AutoReplySettingsStore
import dev.akibh.mycompanion.core.settings.SettingsStore
import dev.akibh.mycompanion.library.data.LibraryBackups
import dev.akibh.mycompanion.quran.data.BookmarkRepository
import dev.akibh.mycompanion.voice.system.VoiceSettingsStore
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Settings backup (Phase 5): the app's switches, voice mode and auto-reply settings, the Quran
 * bookmarks and the book library (without the files themselves) as one JSON file. Restoring
 * overwrites them all (the screen asks first).
 */
class SettingsBackup @Inject constructor(
    private val settingsStore: SettingsStore,
    private val voiceSettings: VoiceSettingsStore,
    private val autoReplySettings: AutoReplySettingsStore,
    private val quranBookmarks: BookmarkRepository,
    private val library: LibraryBackups,
) {
    suspend fun current(): SettingsBundle = SettingsBundle(
        app = settingsStore.settings.first(),
        voice = voiceSettings.settings.first(),
        autoReply = autoReplySettings.settings.first(),
        quran = quranBookmarks.backup(),
        library = library.backup(),
    )

    suspend fun exportJson(): String = ExportJson.settings(current()).toString(INDENT)

    /** @throws IllegalArgumentException when [text] isn't a settings backup. Nothing is changed then. */
    fun parse(text: String): SettingsBundle = ExportJson.parseSettings(text)

    /**
     * Replaces the current settings with [bundle]. Not cancellable: leaving the screen halfway must
     * not leave some stores restored and others not.
     */
    suspend fun restore(bundle: SettingsBundle): Unit = withContext(NonCancellable) {
        settingsStore.replace(bundle.app)
        voiceSettings.update { bundle.voice }
        autoReplySettings.update { bundle.autoReply }
        bundle.quran?.let { quranBookmarks.restore(it) }
        bundle.library?.let { library.restore(it) }
    }

    companion object {
        private const val INDENT = 2

        /**
         * A settings backup is far smaller than this even with a big library and years of reading
         * stats; anything larger isn't one, and reading it whole could run the app out of memory.
         */
        const val MAX_BACKUP_BYTES = 4 * 1024 * 1024

        /** Reads a picked file as text, refusing anything over [MAX_BACKUP_BYTES] without reading it all. */
        fun read(input: InputStream): String {
            val bytes = input.readNBytes(MAX_BACKUP_BYTES + 1)
            require(bytes.size <= MAX_BACKUP_BYTES) { "That file is too big to be a Buddy settings backup" }
            return bytes.toString(Charsets.UTF_8)
        }
    }
}
