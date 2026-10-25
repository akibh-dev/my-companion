package com.akib.mycompanion.vault.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akib.mycompanion.core.sinks.ReminderSink
import com.akib.mycompanion.vault.crypto.KeyWrapper
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.LinkPreviewer
import com.akib.mycompanion.vault.data.Vault
import com.akib.mycompanion.vault.data.VaultBackup
import com.akib.mycompanion.vault.data.VaultFilter
import com.akib.mycompanion.vault.data.VaultItem
import com.akib.mycompanion.vault.data.VaultSearch
import com.akib.mycompanion.vault.data.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultListState(
    val vault: VaultState = VaultState.NotSetUp,
    val items: List<VaultItem> = emptyList(),
    val filter: VaultFilter = VaultFilter(),
    val folders: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val trashCount: Int = 0,
    val message: String? = null,
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: Vault,
    private val previewer: LinkPreviewer,
    val autoLock: VaultAutoLock,
) : ViewModel() {
    private val filter = MutableStateFlow(VaultFilter())
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<VaultListState> = combine(vault.state, vault.items, filter, message) { s, items, f, m ->
        val live = items.filterNot { it.inTrash }
        VaultListState(
            vault = s,
            items = VaultSearch.apply(items, f),
            filter = f,
            folders = live.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted(),
            tags = live.flatMap { it.tags }.distinct().sorted(),
            trashCount = items.count { it.inTrash },
            message = m,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultListState())

    init {
        refresh()
    }

    /** Re-reads the vault's state, e.g. after you set a screen lock in system settings and came back. */
    fun refresh() {
        viewModelScope.launch { vault.refreshState() }
    }

    fun onFilter(change: (VaultFilter) -> VaultFilter) = filter.update(change)

    /** Called after the unlock prompt succeeded. */
    fun onUnlocked(firstTime: Boolean) {
        viewModelScope.launch {
            runCatching { if (firstTime) vault.setUp() else vault.unlock() }
                .onFailure {
                    message.value = when (it) {
                        is KeyWrapper.NeedsUnlock -> "Unlock didn't go through. Try again."
                        else -> it.message
                    }
                }
        }
    }

    fun onMessage(text: String?) {
        message.value = text
    }

    fun onLock() = vault.lock()

    fun onAddNote(title: String, text: String) {
        viewModelScope.launch {
            vault.add(
                VaultItem(
                    type = ItemType.TEXT,
                    title = title.ifBlank {
                        text.lineSequence().first().take(60)
                    },
                    text = text
                )
            )
        }
    }

    fun onAddLink(url: String, title: String) {
        viewModelScope.launch {
            val preview = previewer.fetch(url)
            vault.add(
                VaultItem(
                    type = ItemType.LINK,
                    title = title.ifBlank {
                        preview?.title?.ifBlank { null } ?: url
                    },
                    url = url,
                    preview = preview
                )
            )
        }
    }

    /** An image or file picked with the system picker. */
    fun onAddFile(uri: Uri) {
        viewModelScope.launch {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri).orEmpty()
            var name = "file"
            var size = 0L
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    size = c.getLong(1)
                }
            }
            val type = if (mime.startsWith("image/")) ItemType.IMAGE else ItemType.FILE
            resolver.openInputStream(uri)?.use { input ->
                vault.add(
                    VaultItem(
                        type = type,
                        title = name.substringBeforeLast('.'),
                        mime = mime,
                        fileName = name,
                        size = size
                    ),
                    input
                )
            }
        }
    }
}

@HiltViewModel
class VaultItemViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val vault: Vault,
    private val reminders: ReminderSink,
    val autoLock: VaultAutoLock,
) : ViewModel() {
    private val mutableReminderStatus = MutableStateFlow<String?>(null)

    /** What happened to the last "Remind me…" (set, or why not). */
    val reminderStatus: StateFlow<String?> = mutableReminderStatus.asStateFlow()

    private val id: Long = savedState.get<String>("id")?.toLongOrNull() ?: -1
    val item: StateFlow<VaultItem?> =
        vault.items.map { list -> list.firstOrNull { it.id == id } }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null
        )
    val unlocked: StateFlow<Boolean> = vault.state.map {
        it == VaultState.Unlocked
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun save(item: VaultItem) {
        viewModelScope.launch { vault.update(item) }
    }

    fun trash(item: VaultItem) {
        viewModelScope.launch { vault.trash(item) }
    }

    fun restore(item: VaultItem) {
        viewModelScope.launch { vault.restore(item) }
    }

    fun deleteForever(item: VaultItem) {
        viewModelScope.launch { vault.deleteForever(item) }
    }

    /**
     * "Remind me…" (FR-2.16). Only the title goes into the notification: vault content stays in the vault,
     * since notifications show on the lock screen.
     */
    fun remind(item: VaultItem, atMillis: Long, whenText: String) {
        viewModelScope.launch {
            val set = reminders.remind(item.title.ifBlank { "Vault item" }, "From your vault", atMillis)
            mutableReminderStatus.value = if (set) {
                "Reminder set for $whenText"
            } else {
                "Couldn't set the reminder: allow notifications and alarms for My Companion, and pick a future time"
            }
        }
    }

    suspend fun content(item: VaultItem): ByteArray = vault.content(item)

    suspend fun export(item: VaultItem, output: OutputStream) = vault.readContent(item, output)
}

@HiltViewModel
class VaultBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: Vault,
    private val backup: VaultBackup,
    val autoLock: VaultAutoLock,
) : ViewModel() {
    private val mutableStatus = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = mutableStatus.asStateFlow()
    val trash: StateFlow<List<VaultItem>> =
        vault.items.map { list -> list.filter { it.inTrash } }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

    fun export(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            mutableStatus.value = "Saving backup…"
            mutableStatus.value = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { backup.export(it, passphrase.toCharArray()) }
                "Backup saved (${vault.items.value.size} items)"
            }.getOrElse { "Backup failed: ${it.message}" }
        }
    }

    fun restore(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            mutableStatus.value = "Restoring…"
            mutableStatus.value = runCatching {
                val count = context.contentResolver.openInputStream(uri)
                    ?.use { backup.restore(it, passphrase.toCharArray()) } ?: 0
                "Restored $count items"
            }.getOrElse { it.message ?: "Restore failed" }
        }
    }

    fun restore(item: VaultItem) {
        viewModelScope.launch { vault.restore(item) }
    }

    fun deleteForever(item: VaultItem) {
        viewModelScope.launch { vault.deleteForever(item) }
    }
}
