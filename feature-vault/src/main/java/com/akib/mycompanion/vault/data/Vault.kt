package com.akib.mycompanion.vault.data

import android.content.Context
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.vault.crypto.InboxCipher
import com.akib.mycompanion.vault.crypto.KeyWrapper
import com.akib.mycompanion.vault.crypto.VaultCrypto
import com.akib.mycompanion.vault.crypto.WrappedKeyFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface VaultState {
    /** The phone has no screen lock, so nothing can protect the vault key. */
    data object NoScreenLock : VaultState

    data object NotSetUp : VaultState

    /** [inbox] items were shared while locked and move in at the next unlock. */
    data class Locked(val inbox: Int) : VaultState

    data object Unlocked : VaultState
}

/** Encrypted files of images and documents, one per item. */
class BlobStore(private val dir: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "vault/blobs"))

    fun newName(): String = UUID.randomUUID().toString()

    fun file(name: String) = File(dir.apply { mkdirs() }, "$name.bin")

    fun delete(name: String?) {
        name?.let { file(it).delete() }
    }
}

/**
 * The vault: locked, it holds only ciphertext; unlocked, it holds the data key and a decrypted index
 * of every item in memory for search as you type (D3). [lock] forgets both.
 */
@Singleton
class Vault @Inject constructor(
    private val dao: VaultDao,
    private val wrapper: KeyWrapper,
    private val inbox: InboxCipher,
    private val keyFile: WrappedKeyFile,
    private val blobs: BlobStore,
    private val clock: Clock,
    private val log: AppLog,
) {
    private val lock = Mutex()
    private var crypto: VaultCrypto? = null
    private val mutableState = MutableStateFlow<VaultState>(VaultState.NotSetUp)
    val state: StateFlow<VaultState> = mutableState.asStateFlow()
    private val mutableItems = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = mutableItems.asStateFlow()

    suspend fun refreshState() = lock.withLock { publishState() }

    /** Right after the system unlock prompt: makes the data key and seals it with the unlock-only key. */
    suspend fun setUp() = lock.withLock {
        withContext(Dispatchers.IO) {
            val dataKey = VaultCrypto.newDataKey()
            keyFile.write(wrapper.wrap(dataKey))
            open(dataKey)
        }
        log.i(TAG, "set up")
    }

    /** Right after the system unlock prompt. @throws KeyWrapper.NeedsUnlock if it wasn't. */
    suspend fun unlock() = lock.withLock {
        withContext(Dispatchers.IO) { open(wrapper.unwrap(keyFile.read())) }
        log.i(TAG, "unlocked")
    }

    fun lock() {
        crypto = null
        mutableItems.value = emptyList()
        if (mutableState.value == VaultState.Unlocked) mutableState.value = VaultState.Locked(0)
    }

    val isUnlocked: Boolean get() = crypto != null

    /**
     * Saves an item; [content] is an image's or file's bytes. Works while locked too (share target):
     * then it's sealed with the inbox key and moved in at the next unlock (FR-2.4).
     */
    suspend fun add(item: VaultItem, content: InputStream? = null): Unit = lock.withLock {
        withContext(Dispatchers.IO) {
            val now = clock.nowMillis()
            val main = crypto
            val blob = content?.let { input ->
                blobs.newName().also { name ->
                    blobs.file(name).outputStream().use { out ->
                        if (main != null) {
                            main.encryptStream(
                                input,
                                out
                            )
                        } else {
                            out.write(inbox.encrypt(input.readBytes()))
                        }
                    }
                }
            }
            // A restored backup keeps its items' dates.
            val stored = item.copy(created = item.created.takeIf { it > 0 } ?: now, updated = now, blob = blob)
            val entity = entity(stored, if (main != null) MAIN else INBOX, main)
            val id = dao.insert(entity)
            if (main != null) mutableItems.value = mutableItems.value + stored.copy(id = id) else publishState()
        }
    }

    suspend fun update(item: VaultItem) = lock.withLock {
        val main = crypto ?: return@withLock
        val stored = item.copy(updated = clock.nowMillis())
        withContext(Dispatchers.IO) { dao.upsert(entity(stored, MAIN, main)) }
        mutableItems.value = mutableItems.value.map { if (it.id == item.id) stored else it }
    }

    suspend fun trash(item: VaultItem) = update(item.copy(deletedAt = clock.nowMillis()))

    suspend fun restore(item: VaultItem) = update(item.copy(deletedAt = null))

    suspend fun deleteForever(item: VaultItem) = lock.withLock {
        withContext(Dispatchers.IO) {
            dao.delete(item.id)
            blobs.delete(item.blob)
        }
        mutableItems.value = mutableItems.value.filterNot { it.id == item.id }
    }

    /** Decrypts an item's file into [output] (an in-app viewer, or an export you asked for). */
    suspend fun readContent(item: VaultItem, output: OutputStream) {
        val main = crypto ?: throw KeyWrapper.NeedsUnlock()
        val name = item.blob ?: return
        withContext(Dispatchers.IO) { blobs.file(name).inputStream().use { main.decryptStream(it, output) } }
    }

    suspend fun content(item: VaultItem): ByteArray = ByteArrayOutputStream().also {
        readContent(
            item,
            it
        )
    }.toByteArray()

    private suspend fun open(dataKey: ByteArray) {
        val main = VaultCrypto.of(dataKey)
        crypto = main
        moveInbox(main)
        purgeTrash()
        mutableItems.value = dao.withKey(MAIN).mapNotNull { row -> runCatching { item(row, main) }.getOrNull() }
        mutableState.value = VaultState.Unlocked
    }

    /** Re-seals items shared while locked with the vault key. */
    private suspend fun moveInbox(main: VaultCrypto) {
        dao.withKey(INBOX).forEach { row ->
            runCatching {
                val payload = inbox.decrypt(row.payload)
                row.blob?.let { name ->
                    val plain = inbox.decrypt(blobs.file(name).readBytes())
                    blobs.file(name).outputStream().use { main.encryptStream(ByteArrayInputStream(plain), it) }
                }
                dao.upsert(row.copy(keyId = MAIN, payload = main.encrypt(payload)))
            }.onFailure { log.e(TAG, "couldn't move a shared item in", it) }
        }
    }

    /** The trash keeps items for 30 days (FR-2.14). */
    private suspend fun purgeTrash() {
        dao.trashedBefore(clock.nowMillis() - TRASH_MILLIS).forEach { row ->
            dao.delete(row.id)
            blobs.delete(row.blob)
        }
    }

    private suspend fun publishState() {
        mutableState.value = when {
            crypto != null -> VaultState.Unlocked
            !wrapper.canProtect() -> VaultState.NoScreenLock
            !keyFile.exists() -> VaultState.NotSetUp
            else -> VaultState.Locked(withContext(Dispatchers.IO) { dao.inboxCount() })
        }
    }

    private fun entity(item: VaultItem, keyId: String, main: VaultCrypto?): ItemEntity {
        val plain = ItemCodec.encode(item)
        return ItemEntity(
            id = item.id,
            type = item.type.name,
            keyId = keyId,
            payload = if (keyId == MAIN) main!!.encrypt(plain) else inbox.encrypt(plain),
            pinned = item.pinned,
            favourite = item.favourite,
            created = item.created,
            updated = item.updated,
            deletedAt = item.deletedAt,
            size = item.size,
            blob = item.blob,
        )
    }

    private fun item(row: ItemEntity, main: VaultCrypto): VaultItem {
        val base = VaultItem(
            id = row.id,
            type = ItemType.valueOf(row.type),
            title = "",
            pinned = row.pinned,
            favourite = row.favourite,
            created = row.created,
            updated = row.updated,
            deletedAt = row.deletedAt,
            size = row.size,
            blob = row.blob,
        )
        return ItemCodec.decode(main.decrypt(row.payload), base)
    }

    companion object {
        private const val TAG = "Vault"
        const val MAIN = "main"
        const val INBOX = "inbox"
        const val TRASH_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}
