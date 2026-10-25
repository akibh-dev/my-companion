package com.akib.mycompanion.vault

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.time.Clock
import com.akib.mycompanion.vault.crypto.InboxCipher
import com.akib.mycompanion.vault.crypto.KeyWrapper
import com.akib.mycompanion.vault.crypto.VaultCrypto
import com.akib.mycompanion.vault.crypto.WrappedKeyFile
import com.akib.mycompanion.vault.data.BlobStore
import com.akib.mycompanion.vault.data.ItemCodec
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.LinkPreview
import com.akib.mycompanion.vault.data.LinkPreviewer
import com.akib.mycompanion.vault.data.SortOrder
import com.akib.mycompanion.vault.data.Vault
import com.akib.mycompanion.vault.data.VaultBackup
import com.akib.mycompanion.vault.data.VaultDatabase
import com.akib.mycompanion.vault.data.VaultFilter
import com.akib.mycompanion.vault.data.VaultItem
import com.akib.mycompanion.vault.data.VaultSearch
import com.akib.mycompanion.vault.data.VaultState
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).allowMainThreadQueries().build()
    private val dir = Files.createTempDirectory("vault").toFile()
    private val clock = object : Clock {
        var now = 1_000_000L
        override fun nowMillis() = now
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }

    /** Stands in for the Keystore: a fixed key, and "unlocked" only when the test says so. */
    private val wrapper = object : KeyWrapper {
        val kek = VaultCrypto.of(ByteArray(32) { 7 })
        var justUnlocked = true
        override fun canProtect() = true
        override fun wrap(
            dataKey: ByteArray
        ) = if (justUnlocked) kek.encrypt(dataKey) else throw KeyWrapper.NeedsUnlock()
        override fun unwrap(
            wrapped: ByteArray
        ) = if (justUnlocked) kek.decrypt(wrapped) else throw KeyWrapper.NeedsUnlock()
    }
    private val inbox = object : InboxCipher {
        val key = VaultCrypto.of(ByteArray(32) { 3 })
        override fun encrypt(plain: ByteArray) = key.encrypt(plain)
        override fun decrypt(sealed: ByteArray) = key.decrypt(sealed)
    }
    private val keyFile = WrappedKeyFile(context)
    private val vault =
        Vault(db.dao(), wrapper, inbox, keyFile, BlobStore(dir), clock, AppLog(dir, mirrorToLogcat = false))

    @After
    fun tearDown() {
        db.close()
        context.noBackupFilesDir.resolve("vault.key").delete()
    }

    @Test
    fun `set up, save, lock and unlock again`() = runTest {
        vault.refreshState()
        assertThat(vault.state.value).isEqualTo(VaultState.NotSetUp)

        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "Wi-Fi", text = "password123"))
        vault.lock()
        assertThat(vault.items.value).isEmpty()

        wrapper.justUnlocked = false
        assertThrows(KeyWrapper.NeedsUnlock::class.java) { runBlocking { vault.unlock() } }

        wrapper.justUnlocked = true
        vault.unlock()
        assertThat(vault.items.value.single().text).isEqualTo("password123")
    }

    @Test
    fun `nothing readable is stored`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "Bank PIN", text = "4321"))

        val row = db.dao().all().single()
        assertThat(String(row.payload, Charsets.ISO_8859_1)).doesNotContain("4321")
        assertThat(String(row.payload, Charsets.ISO_8859_1)).doesNotContain("Bank")
    }

    @Test
    fun `shared while locked waits in the inbox and moves in on unlock`() = runTest {
        vault.setUp()
        vault.lock()
        vault.add(
            VaultItem(type = ItemType.IMAGE, title = "receipt", mime = "image/png"),
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        )
        vault.refreshState()
        assertThat(vault.state.value).isEqualTo(VaultState.Locked(inbox = 1))

        vault.unlock()

        val item = vault.items.value.single()
        assertThat(item.title).isEqualTo("receipt")
        assertThat(vault.content(item)).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(db.dao().inboxCount()).isEqualTo(0)
    }

    @Test
    fun `the trash keeps things for 30 days`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "old", text = "x"))
        vault.trash(vault.items.value.single())
        assertThat(vault.items.value.single().inTrash).isTrue()

        clock.now += Vault.TRASH_MILLIS + 1
        vault.lock()
        vault.unlock()

        assertThat(vault.items.value).isEmpty()
    }

    @Test
    fun `a backup restores with the passphrase and refuses without it`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.LINK, title = "Docs", url = "https://example.com", tags = listOf("work")))
        vault.add(
            VaultItem(
                type = ItemType.FILE,
                title = "cv",
                fileName = "cv.pdf"
            ),
            ByteArrayInputStream(
                ByteArray(70_000) {
                    it.toByte()
                }
            )
        )
        val backup = VaultBackup(vault)
        val file = ByteArrayOutputStream().also { backup.export(it, "correct horse".toCharArray()) }.toByteArray()

        assertThrows(IOException::class.java) {
            runBlocking { backup.restore(ByteArrayInputStream(file), "wrong".toCharArray()) }
        }

        vault.items.value.forEach { vault.deleteForever(it) }
        assertThat(backup.restore(ByteArrayInputStream(file), "correct horse".toCharArray())).isEqualTo(2)
        val restored = vault.items.value.associateBy { it.title }
        assertThat(restored.getValue("Docs").tags).containsExactly("work")
        assertThat(vault.content(restored.getValue("cv"))).isEqualTo(ByteArray(70_000) { it.toByte() })
    }

    @Test
    fun `secret fields survive encoding, odd characters too`() {
        val item = VaultItem(
            type = ItemType.LINK,
            title = "a=b\nc",
            tags = listOf("x, y", "ঢাকা"),
            url = "https://x.y/?a=1&b=2",
            preview = LinkPreview("T", "D", "x.y"),
        )
        assertThat(
            ItemCodec.decode(
                ItemCodec.encode(item),
                item.copy(title = "", tags = emptyList(), url = "", preview = null)
            )
        )
            .isEqualTo(item)
    }

    @Test
    fun `search finds words anywhere, pinned first, trash hidden`() {
        val items = listOf(
            VaultItem(1, ItemType.TEXT, "Shopping", text = "milk eggs", created = 1),
            VaultItem(2, ItemType.LINK, "Recipe", url = "https://food.example/eggs", created = 2, pinned = true),
            VaultItem(3, ItemType.TEXT, "Eggs old", created = 3, deletedAt = 5),
            VaultItem(4, ItemType.IMAGE, "Passport", tags = listOf("travel"), created = 4, favourite = true),
        )
        assertThat(
            VaultSearch.apply(items, VaultFilter(query = "eggs")).map { it.id }
        ).containsExactly(2L, 1L).inOrder()
        assertThat(VaultSearch.apply(items, VaultFilter(tag = "TRAVEL")).map { it.id }).containsExactly(4L)
        assertThat(VaultSearch.apply(items, VaultFilter(favouritesOnly = true)).map { it.id }).containsExactly(4L)
        assertThat(
            VaultSearch.apply(items, VaultFilter(sort = SortOrder.TITLE)).map {
                it.id
            }
        ).containsExactly(2L, 4L, 1L).inOrder()
    }

    @Test
    fun `link previews come from the page, without cookies`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """<html><head><title>Fallback</title><meta property="og:title" content="Tea &amp; Biscuits">
                   <meta name="description" content="Best in Dhaka"></head></html>""",
            ),
        )
        server.start()
        val preview = LinkPreviewer(OkHttpClient()).fetch(server.url("/page").toString())
        val request = server.takeRequest()
        server.shutdown()

        assertThat(preview!!.title).isEqualTo("Tea & Biscuits")
        assertThat(preview.description).isEqualTo("Best in Dhaka")
        assertThat(request.getHeader("Cookie")).isNull()
    }
}
