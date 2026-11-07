package com.akib.mycompanion.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.data.local.AppDatabase
import com.akib.mycompanion.data.local.PlaybackHistoryEntity
import com.akib.mycompanion.data.repository.RoomPlaybackHistoryRepository
import com.akib.mycompanion.domain.model.PlaybackHistoryEntry
import com.akib.mycompanion.fakes.video
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackHistoryStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repo = RoomPlaybackHistoryRepository(db.playbackHistoryDao())
    private fun entry(id: String, at: Long = 100, position: Long = 5_000) =
        PlaybackHistoryEntry(video(id, "Song $id"), at, position, 60_000)

    @After fun close() {
        db.close()
    }

    @Test fun `replay updates the existing row and progress in most recent order`() = runTest {
        val a = repo.beginVisit("a")
        repo.record(a, entry("a", 100))
        repo.record(repo.beginVisit("b"), entry("b", 200))
        repo.record(a, entry("a", 300, 12_000))
        val entries = repo.observe().first()
        assertThat(entries.map { it.video.id }).containsExactly("a", "b").inOrder()
        assertThat(entries.first().positionMs).isEqualTo(12_000)
        assertThat(entries.first().video.title).isEqualTo("Song a")
    }

    @Test fun `clear invalidates delayed progress but a later visit can appear`() = runTest {
        val old = repo.beginVisit("a")
        repo.record(old, entry("a"))
        repo.clear()
        repo.record(old, entry("a", 200))
        assertThat(repo.observe().first()).isEmpty()
        repo.record(repo.beginVisit("a"), entry("a", 300))
        assertThat(repo.observe().first()).hasSize(1)
    }

    @Test fun `individual removal invalidates that visit without affecting other playback`() = runTest {
        val a = repo.beginVisit("a")
        val b = repo.beginVisit("b")
        repo.record(a, entry("a"))
        repo.remove("a")
        repo.record(a, entry("a", 200))
        repo.record(b, entry("b", 300))
        assertThat(repo.observe().first().map { it.video.id }).containsExactly("b")
    }

    @Test fun `only 500 latest distinct videos remain`() = runTest {
        repeat(502) { index ->
            val id = index.toString()
            repo.record(repo.beginVisit(id), entry(id, index.toLong()))
        }
        val entries = repo.observe().first()
        assertThat(entries).hasSize(500)
        assertThat(entries.map { it.video.id }).containsNoneOf("0", "1")
        assertThat(entries.first().video.id).isEqualTo("501")
    }

    @Test fun `DAO insertion and zero generation repository insertion both persist`() = runTest {
        val dao = db.playbackHistoryDao()
        dao.record(PlaybackHistoryEntity.from(entry("direct")))
        assertThat(dao.observe().first().map { it.videoId }).containsExactly("direct")
        dao.clear()
        val initial = repo.beginVisit("fresh")
        assertThat(initial.epoch).isEqualTo(0L)
        assertThat(initial.videoEpoch).isEqualTo(0L)
        repo.record(initial, entry("fresh"))
        assertThat(dao.observe().first().map { it.videoId }).containsExactly("fresh")
    }
}
