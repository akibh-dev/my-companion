package com.akib.mycompanion.core.sinks

import java.io.InputStream

/**
 * Lets other features put things in the vault without depending on it (capture → vault, FR-4.9).
 * Works while the vault is locked: items wait in its inbox until the next unlock.
 */
interface VaultSink {
    suspend fun saveNote(title: String, text: String)

    suspend fun saveLink(url: String, title: String)

    suspend fun saveFile(title: String, mime: String, fileName: String, content: InputStream)
}

/** Lets other features set a reminder without depending on the app's reminder code (FR-2.16). */
interface ReminderSink {
    /** @return false when notifications or exact alarms aren't allowed. */
    suspend fun remind(title: String, message: String, atMillis: Long): Boolean
}
