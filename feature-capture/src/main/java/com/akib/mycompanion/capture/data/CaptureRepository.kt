package com.akib.mycompanion.capture.data

import com.akib.mycompanion.ai.Summary
import com.akib.mycompanion.capture.domain.Capture
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.sinks.VaultSink
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.core.util.enumOf
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Stores captures encrypted (plan §11) and decrypts them for the capture space. The files of voice
 * notes, photos and screenshots stay in [files]; deleting a capture deletes its file too.
 */
@Singleton
class CaptureRepository @Inject constructor(
    private val dao: CaptureDao,
    @CaptureCipher private val cipher: FieldCipher,
    private val files: CaptureFiles,
    private val clock: Clock,
    private val log: AppLog,
) {
    /** Every capture, newest first. */
    val captures: Flow<List<Capture>> = dao.observeAll()
        .map { rows -> rows.map(::decode) }
        .flowOn(Dispatchers.Default)

    fun capture(id: Long): Flow<Capture?> = dao.observeOne(id)
        .map { row -> row?.let(::decode) }
        .flowOn(Dispatchers.Default)

    suspend fun get(id: Long): Capture? = dao.get(id)?.let { row -> withContext(Dispatchers.Default) { decode(row) } }

    /** A typed note or clipboard text. */
    suspend fun addText(type: CaptureType, text: String): Long {
        val sealed = withContext(Dispatchers.Default) { seal(text) }
        val id = dao.insert(CaptureEntity(type = type.name, created = clock.nowMillis(), textEnc = sealed))
        log.i(TAG, "saved ${type.name} #$id")
        return id
    }

    /** A voice note, photo or screenshot already written to [file] (inside [files]). */
    suspend fun addFile(type: CaptureType, file: File): Long {
        val id = dao.insert(CaptureEntity(type = type.name, created = clock.nowMillis(), file = file.name))
        log.i(TAG, "saved ${type.name} #$id")
        return id
    }

    /** Saves what can be edited: text, title, transcript, summary and action items. */
    suspend fun update(capture: Capture) {
        val row = dao.get(capture.id) ?: return
        val sealed = withContext(Dispatchers.Default) {
            row.copy(
                textEnc = seal(capture.text),
                titleEnc = seal(capture.title),
                transcriptEnc = seal(capture.transcript),
                summaryEnc = seal(capture.summary),
                actionsEnc = seal(capture.actions.joinToString(ACTION_SEPARATOR)),
            )
        }
        dao.update(sealed)
    }

    suspend fun setTranscript(id: Long, transcript: String) {
        val capture = get(id) ?: return
        update(capture.copy(transcript = transcript.trim()))
    }

    /** Keeps a summary (FR-4.5, FR-4.6); the summary's title is used only when there's none yet. */
    suspend fun applySummary(id: Long, summary: Summary) {
        val capture = get(id) ?: return
        update(
            capture.copy(
                title = capture.title.ifBlank { summary.title.trim() },
                summary = summary.summary.trim(),
                actions = summary.actionItems.map(String::trim).filter(String::isNotEmpty),
            ),
        )
    }

    /** The audio or image file, when the capture has one and it still exists. */
    fun fileOf(capture: Capture): File? = capture.file?.let(files::file)?.takeIf { it.exists() }

    suspend fun delete(capture: Capture) {
        withContext(Dispatchers.IO) { capture.file?.let { files.file(it).delete() } }
        dao.delete(capture.id)
        log.i(TAG, "deleted ${capture.type.name} #${capture.id}")
    }

    /**
     * Move to vault (FR-4.9): hands the capture to the vault (which takes it even while locked), then
     * forgets it here. The row stays as a marker, without content, so the timeline shows where it went.
     */
    suspend fun moveToVault(capture: Capture, vault: VaultSink) {
        val title = capture.displayTitle
        val notes = listOf(capture.text, capture.transcript, capture.summary, capture.actions.joinToString("\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
        if (capture.type.hasFile) {
            val file = fileOf(capture) ?: error("The capture's file is missing")
            withContext(Dispatchers.IO) {
                file.inputStream().use { vault.saveFile(title, capture.type.mime, file.name, it) }
            }
            if (notes.isNotBlank()) vault.saveNote(title, notes)
        } else {
            vault.saveNote(title, notes)
        }
        withContext(Dispatchers.IO) { capture.file?.let { files.file(it).delete() } }
        val row = dao.get(capture.id) ?: return
        dao.update(
            row.copy(
                textEnc = null,
                file = null,
                transcriptEnc = null,
                summaryEnc = null,
                actionsEnc = null,
                titleEnc = seal(title),
                movedToVault = true,
            ),
        )
        log.i(TAG, "moved ${capture.type.name} #${capture.id} to the vault")
    }

    private fun decode(row: CaptureEntity) = Capture(
        id = row.id,
        type = enumOf<CaptureType>(row.type) ?: CaptureType.TEXT,
        created = row.created,
        text = open(row.textEnc),
        file = row.file,
        transcript = open(row.transcriptEnc),
        title = open(row.titleEnc),
        summary = open(row.summaryEnc),
        actions = open(row.actionsEnc).split(ACTION_SEPARATOR).filter { it.isNotBlank() },
        movedToVault = row.movedToVault,
    )

    /** Empty text isn't stored at all. */
    private fun seal(text: String): String? = text.takeIf { it.isNotEmpty() }?.let(cipher::encryptText)

    /** A value that can't be decrypted (its key is gone, e.g. after a restore to a new phone) reads as empty. */
    private fun open(sealed: String?): String {
        if (sealed == null) return ""
        return runCatching { cipher.decryptText(sealed) }
            .onFailure { log.w(TAG, "a capture field couldn't be decrypted", it) }
            .getOrDefault("")
    }

    private companion object {
        const val TAG = "Capture"
        const val ACTION_SEPARATOR = "\n"
    }
}
