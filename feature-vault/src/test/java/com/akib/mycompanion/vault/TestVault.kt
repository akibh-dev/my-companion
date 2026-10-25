package com.akib.mycompanion.vault

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.vault.crypto.InboxCipher
import com.akib.mycompanion.vault.crypto.KeyWrapper
import com.akib.mycompanion.vault.crypto.VaultCrypto
import com.akib.mycompanion.vault.crypto.WrappedKeyFile
import com.akib.mycompanion.vault.data.BlobStore
import com.akib.mycompanion.vault.data.Vault
import com.akib.mycompanion.vault.data.VaultDatabase
import java.io.File
import java.nio.file.Files
import java.time.ZoneId

/**
 * A vault on Robolectric with the Keystore swapped out: a fixed wrapping key that only works while
 * [wrapper] says the user just unlocked, and a fixed inbox key. Call [close] after each test.
 */
class TestVault {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).allowMainThreadQueries().build()
    val dir: File = Files.createTempDirectory("vault").toFile()
    val blobs = BlobStore(File(dir, "blobs"))
    val log = AppLog(File(dir, "logs"), mirrorToLogcat = false)

    class TestClock : Clock {
        var now = 1_000_000L
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }

    /** "Unlocked" only while [justUnlocked]; no screen lock while [screenLock] is false. */
    class TestKeyWrapper : KeyWrapper {
        private val kek = VaultCrypto.of(ByteArray(32) { 7 })
        var justUnlocked = true
        var screenLock = true
        override fun canProtect() = screenLock
        override fun wrap(dataKey: ByteArray) = if (justUnlocked) kek.encrypt(dataKey) else throw needsUnlock()
        override fun unwrap(wrapped: ByteArray) = if (justUnlocked) kek.decrypt(wrapped) else throw needsUnlock()
        private fun needsUnlock() = KeyWrapper.NeedsUnlock()
    }

    class TestInbox : InboxCipher {
        private val key = VaultCrypto.of(ByteArray(32) { 3 })
        override fun encrypt(plain: ByteArray) = key.encrypt(plain)
        override fun decrypt(sealed: ByteArray) = key.decrypt(sealed)
    }

    val clock = TestClock()
    val wrapper = TestKeyWrapper()
    val inbox = TestInbox()

    val vault = Vault(db.dao(), wrapper, inbox, WrappedKeyFile(context), blobs, clock, log)

    fun close() {
        db.close()
        context.noBackupFilesDir.resolve("vault.key").delete()
        dir.deleteRecursively()
    }
}
