package com.akib.mycompanion.autoreply.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.akib.mycompanion.autoreply.domain.Audience
import com.akib.mycompanion.autoreply.domain.AutoReplySettings
import com.akib.mycompanion.autoreply.domain.GroupPolicy
import com.akib.mycompanion.autoreply.domain.ReplyContext
import com.akib.mycompanion.autoreply.domain.ReplyTemplate
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.core.util.enumOf
import com.akib.mycompanion.core.util.enumsOf
import com.akib.mycompanion.messaging.ChatApp
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val text: String,
    val context: String,
    @ColumnInfo(name = "sort_order") val order: Int,
)

/**
 * Reply log (FR-1.8). Who and what are encrypted (plan §11: "Room, encrypted"); [conversationHash]
 * stays readable so the cool-down can look up the last reply without decrypting everything.
 */
@Entity(tableName = "reply_log", indices = [Index("conversation_hash")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val app: String,
    @ColumnInfo(name = "conversation_hash") val conversationHash: String,
    @ColumnInfo(name = "person_enc") val personEncrypted: String,
    @ColumnInfo(name = "text_enc") val textEncrypted: String,
    val template: String,
    val replied: Boolean,
    val reason: String,
)

@Dao
interface AutoReplyDao {
    @Query("SELECT * FROM templates ORDER BY sort_order, id")
    fun observeTemplates(): Flow<List<TemplateEntity>>

    @Upsert
    suspend fun upsertTemplate(template: TemplateEntity)

    @Insert
    suspend fun insertTemplate(template: TemplateEntity): Long

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun templateCount(): Int

    @Insert
    suspend fun insertLog(entry: LogEntity)

    @Query("SELECT MAX(time) FROM reply_log WHERE conversation_hash = :hash AND replied = 1")
    suspend fun lastReply(hash: String): Long?

    @Query("SELECT * FROM reply_log ORDER BY time DESC LIMIT 200")
    fun observeLog(): Flow<List<LogEntity>>

    @Query("DELETE FROM reply_log WHERE time < :before")
    suspend fun deleteLogBefore(before: Long)
}

@Database(entities = [TemplateEntity::class, LogEntity::class], version = 1, exportSchema = false)
abstract class AutoReplyDatabase : RoomDatabase() {
    abstract fun dao(): AutoReplyDao

    companion object {
        fun create(
            context: Context
        ) = Room.databaseBuilder(context, AutoReplyDatabase::class.java, "autoreply.db").build()
    }
}

data class LogEntry(
    val time: Long,
    val app: String,
    val person: String,
    val text: String,
    val template: String,
    val replied: Boolean,
    val reason: String,
)

@Singleton
class AutoReplyRepository @Inject constructor(private val dao: AutoReplyDao, private val cipher: FieldCipher) {
    val templates: Flow<List<ReplyTemplate>> = dao.observeTemplates().map { list ->
        list.map { ReplyTemplate(it.id, it.name, it.text, ReplyContext.decode(it.context)) }
    }

    val log: Flow<List<LogEntry>> = dao.observeLog().map { list ->
        list.map { e ->
            LogEntry(
                e.time,
                e.app,
                decrypt(e.personEncrypted),
                decrypt(e.textEncrypted),
                e.template,
                e.replied,
                e.reason
            )
        }
    }

    suspend fun save(template: ReplyTemplate, order: Int) {
        val entity = TemplateEntity(template.id, template.name, template.text, template.context.encode(), order)
        if (template.id == 0L) dao.insertTemplate(entity) else dao.upsertTemplate(entity)
    }

    suspend fun delete(template: ReplyTemplate) = dao.deleteTemplate(template.id)

    /** A first set of templates so the feature works the moment it's switched on. */
    suspend fun addDefaultsIfEmpty() {
        if (dao.templateCount() > 0) return
        DEFAULTS.forEachIndexed { index, template -> save(template, index) }
    }

    suspend fun lastReply(app: String, conversation: String): Long? = dao.lastReply(hash(app, conversation))

    suspend fun record(
        time: Long,
        app: String,
        conversation: String,
        person: String,
        text: String,
        template: String,
        replied: Boolean,
        reason: String,
    ) = withContext(Dispatchers.IO) {
        dao.insertLog(
            LogEntity(
                time = time,
                app = app,
                conversationHash = hash(app, conversation),
                personEncrypted = cipher.encryptText(person),
                textEncrypted = cipher.encryptText(text),
                template = template,
                replied = replied,
                reason = reason,
            ),
        )
        dao.deleteLogBefore(time - KEEP_MILLIS)
    }

    private fun decrypt(text: String) = runCatching { cipher.decryptText(text) }.getOrDefault("(unreadable)")

    private fun hash(app: String, conversation: String): String =
        MessageDigest.getInstance("SHA-256").digest("$app|${conversation.lowercase()}".toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    companion object {
        private const val KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000
        val DEFAULTS = listOf(
            ReplyTemplate(
                name = "Busy",
                text = "Hi {name}, I'm busy right now. I'll reply after {until}.",
                context = ReplyContext.Busy
            ),
            ReplyTemplate(
                name = "Driving",
                text = "Hi {name}, I'm driving. I'll reply when I stop.",
                context = ReplyContext.WhileRule("Driving")
            ),
            ReplyTemplate(
                name = "Do Not Disturb",
                text = "Hi {name}, I can't look at my phone now. I'll reply soon.",
                context = ReplyContext.WhileDnd
            ),
        )
    }
}

private val Context.autoReplyStore: DataStore<Preferences> by preferencesDataStore(name = "autoreply")

@Singleton
class AutoReplySettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.autoReplyStore

    val settings: Flow<AutoReplySettings> = store.data.map(::read)

    suspend fun update(change: (AutoReplySettings) -> AutoReplySettings) {
        store.edit { p ->
            val s = change(read(p))
            p[AUDIENCE] = s.audience.name
            p[ALLOW] = s.allowList.toSet()
            p[BLOCK] = s.blockList.toSet()
            p[GROUPS] = s.groups.name
            p[MENTIONS] = s.mentionWords.toSet()
            p[COOLDOWN] = s.cooldownMinutes
            p[LABEL] = s.label
            p[APPS] = s.apps.map { it.name }.toSet()
            p[BUSY] = s.busy
            p[BUSY_UNTIL] = s.busyUntil ?: 0
            p[MISSED_TEXT] = s.missedCallText
            p[MISSED_CONTACTS] = s.missedCallContactsOnly
        }
    }

    private fun read(p: Preferences): AutoReplySettings {
        val defaults = AutoReplySettings()
        return AutoReplySettings(
            audience = enumOf<Audience>(p[AUDIENCE]) ?: defaults.audience,
            allowList = p[ALLOW].orEmpty().sorted(),
            blockList = p[BLOCK].orEmpty().sorted(),
            groups = enumOf<GroupPolicy>(p[GROUPS]) ?: defaults.groups,
            mentionWords = p[MENTIONS].orEmpty().sorted(),
            cooldownMinutes = p[COOLDOWN] ?: defaults.cooldownMinutes,
            label = p[LABEL] ?: defaults.label,
            apps = enumsOf<ChatApp>(p[APPS]) ?: defaults.apps,
            busy = p[BUSY] ?: false,
            busyUntil = p[BUSY_UNTIL]?.takeIf { it > 0 },
            missedCallText = p[MISSED_TEXT] ?: defaults.missedCallText,
            missedCallContactsOnly = p[MISSED_CONTACTS] ?: defaults.missedCallContactsOnly,
        )
    }

    private companion object {
        val AUDIENCE = stringPreferencesKey("audience")
        val ALLOW = stringSetPreferencesKey("allow")
        val BLOCK = stringSetPreferencesKey("block")
        val GROUPS = stringPreferencesKey("groups")
        val MENTIONS = stringSetPreferencesKey("mentions")
        val COOLDOWN = intPreferencesKey("cooldown_minutes")
        val LABEL = stringPreferencesKey("label")
        val APPS = stringSetPreferencesKey("apps")
        val BUSY = booleanPreferencesKey("busy")
        val BUSY_UNTIL = longPreferencesKey("busy_until")
        val MISSED_TEXT = stringPreferencesKey("missed_text")
        val MISSED_CONTACTS = booleanPreferencesKey("missed_contacts_only")
    }
}

/** Whether a name or number is a saved contact. Null when contacts access isn't granted. */
class ContactLookup @Inject constructor(@ApplicationContext private val context: Context) {
    fun isContact(nameOrNumber: String): Boolean? {
        if (!canRead()) return null
        val digits = nameOrNumber.filter { it.isDigit() || it == '+' }
        return if (digits.length >= MIN_DIGITS && digits.length >= nameOrNumber.count { !it.isWhitespace() } - 2) {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(digits))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use {
                it.count > 0
            } ?: false
        } else {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME} = ? COLLATE NOCASE",
                arrayOf(nameOrNumber.trim()),
                null,
            )?.use { it.count > 0 } ?: false
        }
    }

    /** The saved name for a phone number, or null. */
    fun nameFor(number: String): String? {
        if (!canRead()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun canRead() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MIN_DIGITS = 6
    }
}
