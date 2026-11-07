package com.akib.mycompanion.data.sound

import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.akib.mycompanion.data.local.ReminderSoundDao
import com.akib.mycompanion.data.local.ReminderSoundEntity
import com.akib.mycompanion.domain.model.ImportedReminderSound
import com.akib.mycompanion.domain.model.ReminderSound
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Own copies live in MediaStore, so notification playback also works without the app process. */
@Singleton
class AndroidReminderSounds @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReminderSoundDao,
) {
    private val resolver = context.contentResolver
    private val imports = Mutex()
    private val refresh = MutableStateFlow(0L)
    fun refresh() {
        refresh.value++
    }
    val sounds: Flow<List<ImportedReminderSound>> = combine(dao.observeAll(), refresh) { rows, _ ->
        withContext(Dispatchers.IO) {
            rows.map {
                ImportedReminderSound(
                    it.id,
                    it.name,
                    it.durationMillis,
                    ReminderSoundChannels.isReadable(context, Uri.parse(it.uri))
                )
            }
        }
    }

    suspend fun import(source: Uri): ImportedReminderSound = withContext(Dispatchers.IO) {
        imports.withLock {
            val name = sourceName(source)
            val file = File.createTempFile("reminder-sound-", ".audio", context.cacheDir)
            var published: Uri? = null
            var stored = false
            try {
                val checksum = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                resolver.openInputStream(source)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            require(bytes <= MAX_BYTES) { "Choose audio no larger than 10 MB" }
                            checksum.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("The selected file cannot be opened")
                require(bytes > 0) { "The selected audio file is empty" }
                val audio = validate(file)
                val id = checksum.digest().joinToString("") { "%02x".format(it) }
                val old = dao.find(id)
                if (old != null && ReminderSoundChannels.isReadable(context, Uri.parse(old.uri))) {
                    return@withLock ImportedReminderSound(old.id, old.name, old.durationMillis, true)
                }
                require(
                    old != null || dao.count() < MAX_IMPORTS
                ) { "Sound library is full (32 files). Remove an unused sound first." }
                val channels = context.getSystemService(NotificationManager::class.java).notificationChannels
                require(
                    channels.count { it.id.startsWith("reminders_imported_") } < MAX_CHANNELS
                ) {
                    "The notification sound limit has been reached. Reuse an existing sound."
                }
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "companion-$id.${audio.extension}")
                    put(MediaStore.Audio.Media.MIME_TYPE, audio.mime)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_NOTIFICATIONS}/My Companion")
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                    put(MediaStore.Audio.Media.IS_MUSIC, 0)
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                published = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Couldn't create the notification sound copy")
                resolver.openOutputStream(published)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                    ?: error("Couldn't write the notification sound copy")
                check(
                    resolver.update(
                        published,
                        ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        },
                        null, null
                    ) == 1
                ) {
                    "Couldn't publish the notification sound"
                }
                currentCoroutineContext().ensureActive()
                withContext(NonCancellable) {
                    dao.insert(ReminderSoundEntity(id, name, published.toString(), audio.mime, audio.duration, bytes))
                    stored = true
                }
                ImportedReminderSound(id, name, audio.duration, true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IllegalArgumentException) {
                throw failure
            } catch (_: Exception) {
                throw IllegalArgumentException(
                    "Couldn't import this audio. Choose a playable MP3, OGG, WAV or AAC/M4A file."
                )
            } finally {
                file.delete()
                if (!stored && published != null) {
                    withContext(NonCancellable) {
                        runCatching { resolver.delete(published, null, null) }
                    }
                }
            }
        }
    }

    /** No deletion of referenced/posted sounds, and no channel recreation to bypass user settings. */
    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        imports.withLock {
            require(dao.references(ReminderSound.Imported(id).encode()) == 0) { "This sound is used by a reminder" }
            val manager = context.getSystemService(NotificationManager::class.java)
            require(
                manager.activeNotifications.none {
                    it.notification.channelId.startsWith(ReminderSoundChannels.importedChannelId(id))
                }
            ) {
                "Dismiss notifications using this sound first"
            }
            val record = dao.find(id) ?: return@withLock
            val uri = Uri.parse(record.uri)
            // MediaStore can throw SecurityException for a row which was already removed externally.
            // Forget missing/inaccessible library records, after the reference/posted-notification guards.
            if (ReminderSoundChannels.isReadable(context, uri)) {
                try {
                    resolver.delete(uri, null, null)
                } catch (denied: SecurityException) {
                    // Handle an external deletion/revocation racing the readability check; never hide
                    // a failure to delete a still-accessible sound.
                    if (ReminderSoundChannels.isReadable(context, uri)) throw denied
                }
            }
            dao.remove(id)
            // Retain its channel identity/settings if this exact audio is imported again.
        }
    }

    private fun sourceName(uri: Uri): String = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.take(120) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.take(120)?.takeIf { it.isNotBlank() }
        ?: "Imported sound"

    private data class Audio(val mime: String, val extension: String, val duration: Long)

    private fun validate(file: File): Audio {
        val duration = MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        }
        require(duration in 1..MAX_DURATION_MILLIS) { "Choose audio between 1 millisecond and 30 seconds long" }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) in FORMATS
            } ?: throw IllegalArgumentException("Choose a playable MP3, OGG, WAV or AAC/M4A file")
            val format = extractor.getTrackFormat(track)
            require(MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format) != null) {
                "This phone cannot decode the selected audio format"
            }
            extractor.selectTrack(track)
            require(extractor.readSampleData(ByteBuffer.allocate(256 * 1024), 0) > 0) {
                "The audio file contains no playable samples"
            }
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            val header = file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
            val container = when (mime) {
                "audio/vorbis", "audio/opus" -> {
                    require(header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "OggS") { "Choose OGG audio" }
                    "audio/ogg" to "ogg"
                }
                "audio/mp4a-latm" -> {
                    if (header[0].toInt() and 0xff == 0xff && header[1].toInt() and 0xf0 == 0xf0) {
                        "audio/aac" to "aac"
                    } else {
                        require(header.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp") {
                            "Choose AAC or M4A audio"
                        }
                        "audio/mp4" to "m4a"
                    }
                }
                else -> FORMATS.getValue(mime)
            }
            return Audio(container.first, container.second, duration)
        } finally {
            extractor.release()
        }
    }

    companion object {
        const val MAX_BYTES = 10L * 1024 * 1024
        const val MAX_DURATION_MILLIS = 30_000L
        const val MAX_IMPORTS = 32
        const val MAX_CHANNELS = 64
        private val FORMATS = mapOf(
            "audio/mpeg" to ("audio/mpeg" to "mp3"),
            "audio/vorbis" to ("audio/ogg" to "ogg"),
            "audio/opus" to ("audio/ogg" to "ogg"),
            "audio/raw" to ("audio/wav" to "wav"),
            "audio/mp4a-latm" to ("audio/mp4" to "m4a"),
        )
    }
}
