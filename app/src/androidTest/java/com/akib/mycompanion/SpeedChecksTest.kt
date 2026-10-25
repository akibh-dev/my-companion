package com.akib.mycompanion

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.capture.ui.CapturePanelActivity
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.time.SystemClock as WallClock
import com.akib.mycompanion.vault.crypto.InboxCipher
import com.akib.mycompanion.vault.crypto.KeyWrapper
import com.akib.mycompanion.vault.crypto.WrappedKeyFile
import com.akib.mycompanion.vault.data.BlobStore
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.Vault
import com.akib.mycompanion.vault.data.VaultDatabase
import com.akib.mycompanion.vault.data.VaultFilter
import com.akib.mycompanion.vault.data.VaultItem
import com.akib.mycompanion.vault.data.VaultSearch
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The plan's speed checks (NFR-6), measured on the device: vault search with 500 items and opening
 * the capture panel, each within 1 second. Timings are written to logcat with the tag "SpeedCheck"
 * so a run on the real phone can be compared with the emulator.
 *
 * The vault here is a real [Vault] (real AES-GCM, real SQLite) but in a temporary folder, with a test
 * key wrapper instead of the Keystore one that needs a fingerprint. It never touches the app's own vault.
 */
@RunWith(AndroidJUnit4::class)
class SpeedChecksTest {

    @get:Rule val compose = createEmptyComposeRule()

    private val app: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(app.cacheDir, "speed-check-${System.nanoTime()}").apply { mkdirs() }

    /** Keeps WrappedKeyFile's "vault.key" inside [dir] instead of the app's real no-backup folder. */
    private val isolated = object : ContextWrapper(app) {
        override fun getNoBackupFilesDir() = File(dir, "nobackup").apply { mkdirs() }
    }
    private val db = Room.inMemoryDatabaseBuilder(app, VaultDatabase::class.java).build()

    private val testWrapper = object : KeyWrapper {
        override fun canProtect() = true
        override fun wrap(dataKey: ByteArray) = dataKey.copyOf()
        override fun unwrap(wrapped: ByteArray) = wrapped.copyOf()
    }
    private val passThrough = object : InboxCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(sealed: ByteArray) = sealed
    }
    private val vault = Vault(
        db.dao(),
        testWrapper,
        passThrough,
        WrappedKeyFile(isolated),
        BlobStore(File(dir, "blobs")),
        WallClock(),
        AppLog(File(dir, "logs"), mirrorToLogcat = false),
    )

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @Test
    fun vaultUnlockAndSearchWith500ItemsStayUnderOneSecond() = runBlocking {
        vault.setUp()
        repeat(ITEMS) { i -> vault.add(sampleItem(i)) }
        vault.lock()

        // Unlocking decrypts all 500 rows and builds the in-memory search index (D3).
        val unlockMillis = timed { vault.unlock() }
        assertThat(vault.items.value).hasSize(ITEMS)

        val queries = listOf("invoice", "trip plan", "dhaka bank", "zzz-no-match", "")
        val searchMillis = queries.associateWith { query ->
            timed { VaultSearch.apply(vault.items.value, VaultFilter(query = query)) }
        }
        val folderMillis = timed { VaultSearch.apply(vault.items.value, VaultFilter(folder = "Work", tag = "bills")) }

        Log.i(
            TAG,
            "vault: unlock+index of $ITEMS items ${unlockMillis}ms, searches $searchMillis, filter ${folderMillis}ms"
        )
        assertThat(unlockMillis).isLessThan(ONE_SECOND)
        searchMillis.values.forEach { assertThat(it).isLessThan(ONE_SECOND) }
        assertThat(folderMillis).isLessThan(ONE_SECOND)

        // And the answers are right, not just fast.
        assertThat(VaultSearch.apply(vault.items.value, VaultFilter(query = "invoice"))).hasSize(ITEMS / 5)
        assertThat(VaultSearch.apply(vault.items.value, VaultFilter(query = "trip plan")).map { it.title })
            .containsExactly("Trip 42 plan")
        assertThat(VaultSearch.apply(vault.items.value, VaultFilter(query = "zzz-no-match"))).isEmpty()
    }

    @Test
    fun capturePanelOpensWithinOneSecond() {
        // Warm-up so the measurement is the panel, not loading the app's code for the first time.
        ActivityScenario.launch<CapturePanelActivity>(CapturePanelActivity.intent(app)).use { awaitPanel() }

        val runs = (1..3).map {
            val start = SystemClock.elapsedRealtime()
            ActivityScenario.launch<CapturePanelActivity>(CapturePanelActivity.intent(app)).use {
                awaitPanel()
                SystemClock.elapsedRealtime() - start
            }
        }
        Log.i(TAG, "capture panel open (ms): $runs")
        assertThat(runs.max()).isLessThan(ONE_SECOND)
    }

    private fun awaitPanel() = compose.waitUntil(5_000) {
        compose.onAllNodes(hasText("Capture space")).fetchSemanticsNodes().isNotEmpty()
    }

    private fun sampleItem(i: Int) = VaultItem(
        type = if (i % 2 == 0) ItemType.TEXT else ItemType.LINK,
        title = if (i == 42) "Trip 42 plan" else "Item $i ${WORDS[i % WORDS.size]}",
        notes = "Note number $i for ${WORDS[(i * 7) % WORDS.size]} with some longer text to search through",
        text = if (i % 5 == 0) "invoice for month ${i % 12}" else "plain text $i",
        url = if (i % 2 == 1) "https://example.org/page/$i" else "",
        tags = listOf("t${i % 10}") + if (i % 3 == 0) listOf("bills") else emptyList(),
        folder = if (i % 4 == 0) "Work" else "Home",
    )

    private inline fun timed(block: () -> Unit): Long {
        val start = SystemClock.elapsedRealtime()
        block()
        return SystemClock.elapsedRealtime() - start
    }

    private companion object {
        const val TAG = "SpeedCheck"
        const val ITEMS = 500
        const val ONE_SECOND = 1_000L
        val WORDS = listOf("dhaka bank", "receipt", "passport", "recipe", "school", "doctor", "trip", "car")
    }
}
