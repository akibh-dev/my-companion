package com.akib.mycompanion.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.vault.crypto.KeyWrapper
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.LinkPreviewer
import com.akib.mycompanion.vault.data.Vault
import com.akib.mycompanion.vault.data.VaultBackup
import com.akib.mycompanion.vault.data.VaultItem
import com.akib.mycompanion.vault.data.VaultSinkImpl
import com.akib.mycompanion.vault.data.VaultState
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
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
class VaultMoreTest {

    private val test = TestVault()
    private val vault = test.vault

    @After
    fun tearDown() = test.close()

    // ---- States ----

    @Test
    fun `a phone without a screen lock can't have a vault`() = runTest {
        test.wrapper.screenLock = false

        vault.refreshState()

        assertThat(vault.state.value).isEqualTo(VaultState.NoScreenLock)
    }

    @Test
    fun `setting up needs a fresh unlock and leaves nothing behind when it fails`() = runTest {
        test.wrapper.justUnlocked = false

        assertThrows(KeyWrapper.NeedsUnlock::class.java) { runBlocking { vault.setUp() } }
        vault.refreshState()

        assertThat(vault.state.value).isEqualTo(VaultState.NotSetUp)
        assertThat(vault.isUnlocked).isFalse()
    }

    @Test
    fun `locking forgets the key and the items, unlocking brings them back`() = runTest {
        vault.setUp()
        assertThat(vault.isUnlocked).isTrue()
        assertThat(vault.state.value).isEqualTo(VaultState.Unlocked)
        vault.add(VaultItem(type = ItemType.TEXT, title = "PIN", text = "4321"))

        vault.lock()
        assertThat(vault.isUnlocked).isFalse()
        assertThat(vault.state.value).isEqualTo(VaultState.Locked(0))
        assertThat(vault.items.value).isEmpty()

        vault.unlock()
        assertThat(vault.items.value.single().text).isEqualTo("4321")
    }

    @Test
    fun `locking a vault that isn't open changes nothing`() = runTest {
        vault.refreshState()

        vault.lock()

        assertThat(vault.state.value).isEqualTo(VaultState.NotSetUp)
    }

    // ---- Items ----

    @Test
    fun `edits are ignored while locked`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "note", text = "v1"))
        val item = vault.items.value.single()
        vault.lock()

        vault.update(item.copy(text = "v2"))
        vault.unlock()

        assertThat(vault.items.value.single().text).isEqualTo("v1")
    }

    @Test
    fun `edits keep the id and refresh the time`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "note", text = "v1"))
        val item = vault.items.value.single()
        test.clock.now += 5_000

        vault.update(item.copy(text = "v2", favourite = true))
        vault.lock()
        vault.unlock()

        val saved = vault.items.value.single()
        assertThat(saved.id).isEqualTo(item.id)
        assertThat(saved.text).isEqualTo("v2")
        assertThat(saved.favourite).isTrue()
        assertThat(saved.updated).isEqualTo(item.updated + 5_000)
        assertThat(saved.created).isEqualTo(item.created)
    }

    @Test
    fun `something in the trash can be restored`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "oops", text = "x"))
        vault.trash(vault.items.value.single())

        vault.restore(vault.items.value.single())

        assertThat(vault.items.value.single().inTrash).isFalse()
    }

    @Test
    fun `the trash keeps things younger than 30 days`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "recent", text = "x"))
        vault.trash(vault.items.value.single())

        test.clock.now += Vault.TRASH_MILLIS - 1
        vault.lock()
        vault.unlock()

        assertThat(vault.items.value.single().inTrash).isTrue()
    }

    @Test
    fun `deleting forever removes the encrypted file too`() = runTest {
        vault.setUp()
        vault.add(
            VaultItem(type = ItemType.IMAGE, title = "receipt", mime = "image/png"),
            ByteArrayInputStream(byteArrayOf(9, 8, 7)),
        )
        val item = vault.items.value.single()
        val blob = test.blobs.file(item.blob!!)
        assertThat(blob.exists()).isTrue()
        assertThat(blob.readBytes()).isNotEqualTo(byteArrayOf(9, 8, 7))

        vault.deleteForever(item)

        assertThat(blob.exists()).isFalse()
        assertThat(vault.items.value).isEmpty()
        assertThat(test.db.dao().all()).isEmpty()
    }

    @Test
    fun `files can't be read while locked, notes have no file`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.FILE, title = "cv"), ByteArrayInputStream(byteArrayOf(1)))
        vault.add(VaultItem(type = ItemType.TEXT, title = "note", text = "x"))
        val (file, note) = vault.items.value

        assertThat(vault.content(note)).isEmpty()
        val out = ByteArrayOutputStream()
        vault.readContent(file, out)
        assertThat(out.toByteArray()).isEqualTo(byteArrayOf(1))

        vault.lock()
        assertThrows(KeyWrapper.NeedsUnlock::class.java) { runBlocking { vault.content(file) } }
    }

    @Test
    fun `a restored item keeps its date, a new one gets now`() = runTest {
        vault.setUp()

        vault.add(VaultItem(type = ItemType.TEXT, title = "old", created = 42))
        vault.add(VaultItem(type = ItemType.TEXT, title = "new"))

        val byTitle = vault.items.value.associateBy { it.title }
        assertThat(byTitle.getValue("old").created).isEqualTo(42)
        assertThat(byTitle.getValue("new").created).isEqualTo(test.clock.now)
    }

    @Test
    fun `a shared item that can't be read is skipped, the others move in`() = runTest {
        vault.setUp()
        vault.lock()
        vault.add(VaultItem(type = ItemType.TEXT, title = "good", text = "ok"))
        val bad = test.db.dao().all().single().copy(id = 0, payload = ByteArray(13) { it.toByte() })
        test.db.dao().insert(bad)
        vault.refreshState()
        assertThat(vault.state.value).isEqualTo(VaultState.Locked(inbox = 2))

        vault.unlock()

        assertThat(vault.items.value.map { it.title }).containsExactly("good")
        assertThat(test.log.read().any { it.message.startsWith("couldn't move a shared item in") }).isTrue()
    }

    // ---- Backup ----

    @Test
    fun `a file that isn't a backup is refused`() = runTest {
        val backup = VaultBackup(vault)

        val error = assertThrows(IOException::class.java) {
            val notABackup = ByteArrayInputStream("hello world, not a backup".toByteArray())
            runBlocking { backup.restore(notABackup, "x".toCharArray()) }
        }
        assertThat(error.message).isEqualTo("Not a My Companion vault backup")
    }

    @Test
    fun `an empty vault backs up and restores nothing`() = runTest {
        vault.setUp()
        val backup = VaultBackup(vault)
        val file = ByteArrayOutputStream().also { backup.export(it, "pass".toCharArray()) }.toByteArray()

        assertThat(backup.restore(ByteArrayInputStream(file), "pass".toCharArray())).isEqualTo(0)
        assertThat(vault.items.value).isEmpty()
    }

    @Test
    fun `restoring adds to what's there, keeping pins and dates`() = runTest {
        vault.setUp()
        vault.add(VaultItem(type = ItemType.TEXT, title = "kept", text = "a", pinned = true, favourite = true))
        val backup = VaultBackup(vault)
        val file = ByteArrayOutputStream().also { backup.export(it, "pass".toCharArray()) }.toByteArray()
        val original = vault.items.value.single()

        backup.restore(ByteArrayInputStream(file), "pass".toCharArray())

        val items = vault.items.value
        assertThat(items).hasSize(2)
        items.forEach {
            assertThat(it.title).isEqualTo("kept")
            assertThat(it.pinned).isTrue()
            assertThat(it.favourite).isTrue()
            assertThat(it.created).isEqualTo(original.created)
        }
    }

    @Test
    fun `the passphrase key is the same for the same salt, and different otherwise`() {
        val salt = ByteArray(16) { 1 }

        val first = VaultBackup.derive("pass".toCharArray(), salt, 1_000)
        val again = VaultBackup.derive("pass".toCharArray(), salt, 1_000)
        val otherSalt = VaultBackup.derive("pass".toCharArray(), ByteArray(16) { 2 }, 1_000)
        val otherPass = VaultBackup.derive("Pass".toCharArray(), salt, 1_000)

        assertThat(first).hasLength(32)
        assertThat(first).isEqualTo(again)
        assertThat(first).isNotEqualTo(otherSalt)
        assertThat(first).isNotEqualTo(otherPass)
    }

    // ---- What other features put in (VaultSink) ----

    @Test
    fun `a note from another feature is saved as a note`() = runTest {
        vault.setUp()

        VaultSinkImpl(vault, LinkPreviewer(OkHttpClient())).saveNote("Gate code", "4321")

        val item = vault.items.value.single()
        assertThat(item.type).isEqualTo(ItemType.TEXT)
        assertThat(item.title).isEqualTo("Gate code")
        assertThat(item.text).isEqualTo("4321")
    }

    @Test
    fun `a link without a title takes the page's title`() = runTest {
        vault.setUp()
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("<title>Docs home</title>"))
        server.start()
        val url = server.url("/docs").toString()

        VaultSinkImpl(vault, LinkPreviewer(OkHttpClient())).saveLink(url, " ")
        server.shutdown()

        val item = vault.items.value.single()
        assertThat(item.type).isEqualTo(ItemType.LINK)
        assertThat(item.title).isEqualTo("Docs home")
        assertThat(item.url).isEqualTo(url)
        assertThat(item.preview?.title).isEqualTo("Docs home")
    }

    @Test
    fun `a link that can't be previewed is titled with its address`() = runTest {
        vault.setUp()
        val server = MockWebServer().apply { start() }
        val url = server.url("/gone").toString()
        server.shutdown()

        VaultSinkImpl(vault, LinkPreviewer(OkHttpClient())).saveLink(url, "")

        val item = vault.items.value.single()
        assertThat(item.title).isEqualTo(url)
        assertThat(item.preview).isNull()
    }

    @Test
    fun `files become images or files by their type, and work while locked`() = runTest {
        vault.setUp()
        vault.lock()
        val sink = VaultSinkImpl(vault, LinkPreviewer(OkHttpClient()))

        sink.saveFile("Photo", "image/jpeg", "photo.jpg", ByteArrayInputStream(byteArrayOf(1, 2)))
        sink.saveFile("Voice", "audio/mp4", "voice.m4a", ByteArrayInputStream(byteArrayOf(3)))
        vault.refreshState()
        assertThat(vault.state.value).isEqualTo(VaultState.Locked(inbox = 2))

        vault.unlock()
        val byTitle = vault.items.value.associateBy { it.title }
        assertThat(byTitle.getValue("Photo").type).isEqualTo(ItemType.IMAGE)
        assertThat(byTitle.getValue("Voice").type).isEqualTo(ItemType.FILE)
        assertThat(byTitle.getValue("Voice").fileName).isEqualTo("voice.m4a")
        assertThat(vault.content(byTitle.getValue("Photo"))).isEqualTo(byteArrayOf(1, 2))
    }
}
