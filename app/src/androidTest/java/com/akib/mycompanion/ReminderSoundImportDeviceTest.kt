package com.akib.mycompanion

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.local.ReminderEntity
import com.akib.mycompanion.data.sound.AndroidReminderSounds
import com.akib.mycompanion.data.sound.ReminderSoundChannels
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.ReminderSound
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith

/** Real extractor/decoder discovery, MediaStore publication and cold-process-readable sound URIs. */
@RunWith(AndroidJUnit4::class)
class ReminderSoundImportDeviceTest {
    @Test fun importsSupportedContainersAndKeepsCopyAfterOriginalRemoval() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db.reminderSoundDao()
        val library = AndroidReminderSounds(context, dao)
        val channels = ReminderSoundChannels(context, dao)
        val importedIds = mutableListOf<String>()
        var originalFailure: Throwable? = null
        try {
            for (extension in listOf("wav", "mp3", "ogg", "aac", "m4a")) {
                val source = File(context.cacheDir, "sample.$extension")
                val bytes = instrumentation.context.assets.open("reminder-sounds/sample.$extension").use {
                    it.readBytes()
                }
                source.writeBytes(bytes)
                val imported = library.import(Uri.fromFile(source))
                importedIds += imported.id
                val duplicate = library.import(Uri.fromFile(source))
                assertThat(duplicate.id).isEqualTo(imported.id)
                assertThat(source.delete()).isTrue()
                val selection = channels.resolve(ReminderSound.Imported(imported.id))
                assertThat(selection.id).isNotEqualTo(ReminderSoundChannels.DEFAULT_CHANNEL)
                assertThat(ReminderSoundChannels.isReadable(context, requireNotNull(selection.uri))).isTrue()
                val record = requireNotNull(dao.find(imported.id))
                assertThat(record.mimeType).isEqualTo(
                    when (extension) {
                        "wav" -> "audio/wav"
                        "mp3" -> "audio/mpeg"
                        "ogg" -> "audio/ogg"
                        "aac" -> "audio/aac"
                        else -> "audio/mp4"
                    }
                )
            }
            assertThat(dao.count()).isEqualTo(5)
            val lostId = importedIds.first()
            val lost = requireNotNull(dao.find(lostId))
            context.contentResolver.delete(Uri.parse(lost.uri), null, null)
            assertThat(channels.resolve(ReminderSound.Imported(lostId)).id)
                .isEqualTo(ReminderSoundChannels.DEFAULT_CHANNEL)
            val referencingReminder = db.reminderDao().insert(
                ReminderEntity.from(
                    Reminder(
                        title = "Sound removal guard",
                        message = "",
                        triggerAtMillis = 0,
                        sound = ReminderSound.Imported(lostId)
                    ),
                )
            )
            var protected = false
            try {
                library.remove(lostId)
            } catch (_: IllegalArgumentException) {
                protected = true
            }
            assertThat(protected).isTrue()
            assertThat(dao.find(lostId)).isNotNull()
            db.reminderDao().delete(referencingReminder)
            library.remove(lostId)
            assertThat(dao.find(lostId)).isNull()
            val corrupt = File(context.cacheDir, "corrupt.mp3").apply { writeText("This is not audio") }
            var rejected = false
            try {
                library.import(Uri.fromFile(corrupt))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertThat(rejected).isTrue()
            corrupt.delete()
            val oversized = File(context.cacheDir, "oversized.wav")
            java.io.RandomAccessFile(oversized, "rw").use { it.setLength(AndroidReminderSounds.MAX_BYTES + 1) }
            rejected = false
            try {
                library.import(Uri.fromFile(oversized))
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertThat(rejected).isTrue()
            oversized.delete()
            assertThat(dao.count()).isEqualTo(4)
            assertThat(
                context.cacheDir.listFiles().orEmpty().filter {
                    it.name.startsWith("reminder-sound-") && it.name.endsWith(".audio")
                }
            ).isEmpty()
        } catch (failure: Throwable) {
            originalFailure = failure
            throw failure
        } finally {
            val cleanupFailures = mutableListOf<Throwable>()
            withContext(NonCancellable) {
                importedIds.forEach { id ->
                    try {
                        library.remove(id)
                    } catch (failure: Throwable) {
                        cleanupFailures += failure
                    }
                }
            }
            try {
                db.close()
            } catch (failure: Throwable) {
                cleanupFailures += failure
            }
            val original = originalFailure
            if (original != null) {
                cleanupFailures.forEach(original::addSuppressed)
            } else if (cleanupFailures.isNotEmpty()) {
                val failure = cleanupFailures.first()
                cleanupFailures.drop(1).forEach(failure::addSuppressed)
                throw failure
            }
        }
    }
}
