package com.akib.mycompanion.vault

import com.akib.mycompanion.vault.data.ItemCodec
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.LinkPreview
import com.akib.mycompanion.vault.data.SortOrder
import com.akib.mycompanion.vault.data.VaultFilter
import com.akib.mycompanion.vault.data.VaultItem
import com.akib.mycompanion.vault.data.VaultSearch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VaultSearchTest {

    private val note = VaultItem(1, ItemType.TEXT, "Wi-Fi", text = "router password", folder = "Home", created = 10)
    private val link = VaultItem(
        2,
        ItemType.LINK,
        "Recipe",
        url = "https://food.example",
        preview = LinkPreview("Khichuri", "Rainy day lunch", "food.example"),
        tags = listOf("cooking"),
        created = 30,
    )
    private val image = VaultItem(3, ItemType.IMAGE, "passport", folder = "home", fileName = "scan.jpg", created = 20)
    private val file = VaultItem(4, ItemType.FILE, "CV", fileName = "cv.pdf", notes = "updated in May", created = 40)
    private val items = listOf(note, link, image, file)

    private fun ids(filter: VaultFilter) = VaultSearch.apply(items, filter).map { it.id }

    @Test
    fun `newest first by default`() {
        assertThat(ids(VaultFilter())).containsExactly(4L, 2L, 3L, 1L).inOrder()
    }

    @Test
    fun `every word must match somewhere, ignoring case`() {
        assertThat(ids(VaultFilter(query = "ROUTER pass"))).containsExactly(1L)
        assertThat(ids(VaultFilter(query = "router recipe"))).isEmpty()
        assertThat(ids(VaultFilter(query = "  "))).hasSize(4)
    }

    @Test
    fun `search looks in notes, file names, folders, tags and the link preview`() {
        assertThat(ids(VaultFilter(query = "may"))).containsExactly(4L)
        assertThat(ids(VaultFilter(query = "scan.jpg"))).containsExactly(3L)
        assertThat(ids(VaultFilter(query = "khichuri rainy"))).containsExactly(2L)
        assertThat(ids(VaultFilter(query = "cooking"))).containsExactly(2L)
        assertThat(ids(VaultFilter(query = "food.example"))).containsExactly(2L)
        assertThat(ids(VaultFilter(query = "home"))).containsExactly(3L, 1L).inOrder()
    }

    @Test
    fun `filters by type and folder, folders ignoring case`() {
        assertThat(ids(VaultFilter(type = ItemType.IMAGE))).containsExactly(3L)
        assertThat(ids(VaultFilter(folder = "HOME"))).containsExactly(3L, 1L).inOrder()
        assertThat(ids(VaultFilter(folder = "Work"))).isEmpty()
        assertThat(ids(VaultFilter(type = ItemType.TEXT, folder = "home"))).containsExactly(1L)
    }

    @Test
    fun `oldest, title and type orders`() {
        assertThat(ids(VaultFilter(sort = SortOrder.OLDEST))).containsExactly(1L, 3L, 2L, 4L).inOrder()
        assertThat(ids(VaultFilter(sort = SortOrder.TITLE))).containsExactly(4L, 3L, 2L, 1L).inOrder()
        assertThat(ids(VaultFilter(sort = SortOrder.TYPE))).containsExactly(1L, 2L, 3L, 4L).inOrder()
    }

    @Test
    fun `pinned items stay on top whatever the order`() {
        val pinned = items.map { if (it.id == 1L) it.copy(pinned = true) else it }

        SortOrder.entries.forEach { sort ->
            assertThat(VaultSearch.apply(pinned, VaultFilter(sort = sort)).first().id).isEqualTo(1L)
        }
    }

    @Test
    fun `items in the trash are never listed`() {
        val trashed = items.map { if (it.id == 2L) it.copy(deletedAt = 99) else it }

        assertThat(VaultSearch.apply(trashed, VaultFilter(query = "recipe"))).isEmpty()
        assertThat(trashed.single { it.id == 2L }.inTrash).isTrue()
        assertThat(note.inTrash).isFalse()
    }

    @Test
    fun `decoding keeps the plain columns and skips damaged lines`() {
        val base = VaultItem(id = 7, type = ItemType.TEXT, title = "", pinned = true, created = 5, size = 12)
        val bytes = "title=Shopping\nnot a field\ntext=milk%0Aeggs\nfuture=whatever".toByteArray()

        val item = ItemCodec.decode(bytes, base)

        assertThat(item.title).isEqualTo("Shopping")
        assertThat(item.text).isEqualTo("milk\neggs")
        assertThat(item.id).isEqualTo(7)
        assertThat(item.pinned).isTrue()
        assertThat(item.size).isEqualTo(12)
        assertThat(item.preview).isNull()
        assertThat(item.tags).isEmpty()
    }

    @Test
    fun `an item without a preview round-trips without one`() {
        val plain = VaultItem(type = ItemType.FILE, title = "cv", mime = "application/pdf", fileName = "cv.pdf")

        assertThat(ItemCodec.decode(ItemCodec.encode(plain), plain.copy(title = "", mime = "", fileName = "")))
            .isEqualTo(plain)
    }
}
