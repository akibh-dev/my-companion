package com.akib.mycompanion.fakes

import com.akib.mycompanion.domain.model.Contact
import com.akib.mycompanion.domain.model.PlaybackState
import com.akib.mycompanion.domain.model.Playlist
import com.akib.mycompanion.domain.model.PlaylistItem
import com.akib.mycompanion.domain.model.Reminder
import com.akib.mycompanion.domain.model.Video
import com.akib.mycompanion.domain.port.AlarmScheduler
import com.akib.mycompanion.domain.port.PhonebookSource
import com.akib.mycompanion.domain.port.PickedPhone
import com.akib.mycompanion.domain.port.Playback
import com.akib.mycompanion.domain.port.ReminderNotifier
import com.akib.mycompanion.domain.port.VideoInfoSource
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.repository.ContactRepository
import com.akib.mycompanion.domain.repository.PlaylistRepository
import com.akib.mycompanion.domain.repository.ReminderRepository
import com.akib.mycompanion.domain.time.Clock
import com.akib.mycompanion.presentation.shortcuts.HomeShortcuts
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")

/** Epoch millis for a local date-time in [zone]. */
fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId = DHAKA): Long =
    ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

/** Friday 23 Oct 2026, 10:00 in Dhaka unless told otherwise. */
class FakeClock(
    var now: Long = millisAt(2026, 10, 23, 10, 0),
    override val zone: ZoneId = DHAKA,
) : Clock {
    override fun nowMillis(): Long = now

    fun advanceMinutes(minutes: Long) {
        now += minutes * 60_000
    }
}

class FakeContactRepository(initial: List<Contact> = emptyList()) : ContactRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val all: List<Contact> get() = state.value

    override fun observeAll(): Flow<List<Contact>> = state.map { list -> list.sortedBy { it.name.lowercase() } }

    override suspend fun get(id: Long): Contact? = state.value.firstOrNull { it.id == id }

    override suspend fun findByPhone(phone: String): Contact? = state.value.firstOrNull { it.phone == phone }

    override suspend fun save(contact: Contact): Contact {
        val saved = if (contact.id == 0L) contact.copy(id = nextId++) else contact
        state.value = state.value.filterNot { it.id == saved.id } + saved
        return saved
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeReminderRepository(initial: List<Reminder> = emptyList()) : ReminderRepository {
    private val state = MutableStateFlow(initial)
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val all: List<Reminder> get() = state.value

    override fun observeUpcoming(): Flow<List<Reminder>> =
        state.map { list -> list.filterNot { it.delivered }.sortedBy { it.triggerAtMillis } }

    override suspend fun get(id: Long): Reminder? = state.value.firstOrNull { it.id == id }

    override suspend fun getPending(): List<Reminder> =
        state.value.filterNot { it.delivered }.sortedBy { it.triggerAtMillis }

    override suspend fun save(reminder: Reminder): Reminder {
        val saved = if (reminder.id == 0L) reminder.copy(id = nextId++) else reminder
        state.value = state.value.filterNot { it.id == saved.id } + saved
        return saved
    }

    override suspend fun delete(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun deleteDeliveredBefore(beforeMillis: Long) {
        state.value = state.value.filterNot { it.delivered && it.triggerAtMillis < beforeMillis }
    }
}

class FakeAlarmScheduler : AlarmScheduler {
    /** reminder id → trigger time of the currently armed alarm. */
    val scheduled = mutableMapOf<Long, Long>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(reminder: Reminder) {
        scheduled[reminder.id] = reminder.triggerAtMillis
    }

    override fun cancel(reminderId: Long) {
        scheduled.remove(reminderId)
        cancelled += reminderId
    }
}

class FakeNotifier(var enabled: Boolean = true) : ReminderNotifier {
    val shown = mutableListOf<Pair<Reminder, Contact?>>()
    val cancelled = mutableListOf<Int>()

    override fun show(reminder: Reminder, contact: Contact?): Boolean {
        if (!enabled) return false
        shown += reminder to contact
        return true
    }

    override fun cancel(notificationId: Int) {
        cancelled += notificationId
    }
}

class FakeWhatsAppGateway(
    var installed: Boolean = true,
    var target: VoiceCallTarget? = null,
    var callWorks: Boolean = true,
    var chatWorks: Boolean = true,
) : WhatsAppGateway {
    val calls = mutableListOf<VoiceCallTarget>()
    val chats = mutableListOf<String>()

    override fun isInstalled(): Boolean = installed

    override fun findVoiceCallTarget(digits: String): VoiceCallTarget? = target

    override fun startVoiceCall(target: VoiceCallTarget): Boolean {
        calls += target
        return callWorks
    }

    override fun openChat(digits: String): Boolean {
        chats += digits
        return chatWorks
    }
}

class FakePhonebook(var result: PickedPhone? = null) : PhonebookSource {
    val requested = mutableListOf<String>()

    override suspend fun read(uri: String): PickedPhone? {
        requested += uri
        return result
    }
}

class FakeHomeShortcuts(override var isSupported: Boolean = true) : HomeShortcuts {
    val pinned = mutableListOf<Contact>()
    val updated = mutableListOf<Contact>()

    override fun pin(contact: Contact): Boolean {
        if (!isSupported) return false
        pinned += contact
        return true
    }

    override fun update(contact: Contact) {
        updated += contact
    }
}

class FakePlaylistRepository : PlaylistRepository {
    private val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private val items = MutableStateFlow<List<PlaylistItem>>(emptyList())
    private var nextId = 1L

    val allItems: List<PlaylistItem> get() = items.value

    private fun withCounts(list: List<Playlist>, all: List<PlaylistItem>) = list.map { p ->
        val mine = all.filter { it.playlistId == p.id }
        p.copy(itemCount = mine.size, totalSeconds = mine.sumOf { it.video.durationSeconds })
    }.sortedBy { it.name.lowercase() }

    override fun observePlaylists(): Flow<List<Playlist>> =
        combine(playlists, items) { list, all -> withCounts(list, all) }

    override fun observeItems(playlistId: Long): Flow<List<PlaylistItem>> =
        items.map { all -> all.filter { it.playlistId == playlistId }.sortedBy { it.position } }

    override suspend fun get(playlistId: Long): Playlist? =
        withCounts(playlists.value, items.value).firstOrNull { it.id == playlistId }

    override suspend fun findByName(name: String): Playlist? =
        withCounts(playlists.value, items.value).firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun create(name: String): Playlist {
        val playlist = Playlist(id = nextId++, name = name)
        playlists.value = playlists.value + playlist
        return playlist
    }

    override suspend fun rename(playlistId: Long, name: String) {
        playlists.value = playlists.value.map { if (it.id == playlistId) it.copy(name = name) else it }
    }

    override suspend fun delete(playlistId: Long) {
        playlists.value = playlists.value.filterNot { it.id == playlistId }
        items.value = items.value.filterNot { it.playlistId == playlistId }
    }

    override suspend fun items(playlistId: Long): List<PlaylistItem> =
        items.value.filter { it.playlistId == playlistId }.sortedBy { it.position }

    override suspend fun contains(playlistId: Long, videoId: String): Boolean =
        items.value.any { it.playlistId == playlistId && it.video.id == videoId }

    override suspend fun addItem(playlistId: Long, video: Video): PlaylistItem {
        val position = items.value.filter { it.playlistId == playlistId }.maxOfOrNull { it.position + 1 } ?: 0
        val item = PlaylistItem(id = nextId++, playlistId = playlistId, video = video, position = position)
        items.value = items.value + item
        return item
    }

    override suspend fun removeItem(itemId: Long) {
        items.value = items.value.filterNot { it.id == itemId }
    }
}

class FakeVideoSource(
    val videos: MutableMap<String, Video> = mutableMapOf(),
    var failure: IOException? = null,
) : VideoInfoSource {
    override suspend fun fetch(videoId: String): Video {
        failure?.let { throw it }
        return videos[videoId] ?: throw IOException("Video unavailable")
    }
}

/** Records calls and keeps a simple queue so screens see realistic state. */
class FakePlayback : Playback {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState
    val calls = mutableListOf<String>()

    fun set(state: PlaybackState) {
        mutableState.value = state
    }

    override fun connect() {
        calls += "connect"
    }

    override fun playNow(videos: List<Video>, startIndex: Int, shuffle: Boolean) {
        calls += "playNow:${videos.joinToString(",") { it.id }}@$startIndex shuffle=$shuffle"
        mutableState.value =
            PlaybackState(connected = true, queue = videos, currentIndex = startIndex, isPlaying = true)
    }

    override fun addToQueue(video: Video) {
        calls += "addToQueue:${video.id}"
        val queue = mutableState.value.queue + video
        val index = mutableState.value.currentIndex.coerceAtLeast(0)
        mutableState.value = mutableState.value.copy(queue = queue, currentIndex = index)
    }

    override fun togglePlayPause() {
        calls += "toggle"
    }

    override fun next() {
        calls += "next"
    }

    override fun previous() {
        calls += "previous"
    }

    override fun seekTo(positionMs: Long) {
        calls += "seek:$positionMs"
    }

    override fun skipTo(index: Int) {
        calls += "skipTo:$index"
    }

    override fun removeAt(index: Int) {
        calls += "removeAt:$index"
    }

    override fun cycleRepeat() {
        calls += "repeat"
    }

    override fun toggleShuffle() {
        calls += "shuffle"
    }
}

fun video(id: String, title: String = "Video $id", seconds: Long = 200) = Video(
    id = id,
    title = title,
    author = "Channel",
    durationSeconds = seconds,
    thumbnailUrl = "https://i.ytimg.com/vi/$id/hq.jpg",
)
