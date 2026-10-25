package com.akib.mycompanion.vault.data

import java.net.URLDecoder
import java.net.URLEncoder

/** The kinds of things the vault keeps (FR-2.1). */
enum class ItemType(val title: String) { TEXT("Note"), LINK("Link"), IMAGE("Image"), FILE("File") }

/** Fetched once when a link is saved (FR-2.11). */
data class LinkPreview(val title: String, val description: String, val site: String)

/** One vault item, decrypted (exists only in memory while the vault is unlocked). */
data class VaultItem(
    val id: Long = 0,
    val type: ItemType,
    val title: String,
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val folder: String = "",
    val text: String = "",
    val url: String = "",
    val mime: String = "",
    val fileName: String = "",
    val size: Long = 0,
    val preview: LinkPreview? = null,
    val pinned: Boolean = false,
    val favourite: Boolean = false,
    val created: Long = 0,
    val updated: Long = 0,
    /** Set while in the trash (FR-2.14). */
    val deletedAt: Long? = null,
    /** The encrypted file holding an image or file's bytes. */
    val blob: String? = null,
) {
    val inTrash: Boolean get() = deletedAt != null
}

/** The secret fields of an item as text before encryption: one URL-encoded "key=value" per line. */
object ItemCodec {
    fun encode(item: VaultItem): ByteArray = buildList {
        add("title" to item.title)
        add("notes" to item.notes)
        add("tags" to item.tags.joinToString("\u001f"))
        add("folder" to item.folder)
        add("text" to item.text)
        add("url" to item.url)
        add("mime" to item.mime)
        add("file" to item.fileName)
        item.preview?.let {
            add("p.title" to it.title)
            add("p.desc" to it.description)
            add("p.site" to it.site)
        }
    }.joinToString("\n") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }.toByteArray()

    /** Fills the secret fields of [base] (which carries the plain columns). */
    fun decode(bytes: ByteArray, base: VaultItem): VaultItem {
        val f = String(bytes).lines().filter { '=' in it }.associate { line ->
            line.substringBefore('=') to URLDecoder.decode(line.substringAfter('='), "UTF-8")
        }
        return base.copy(
            title = f["title"].orEmpty(),
            notes = f["notes"].orEmpty(),
            tags = f["tags"].orEmpty().split('\u001f').filter { it.isNotBlank() },
            folder = f["folder"].orEmpty(),
            text = f["text"].orEmpty(),
            url = f["url"].orEmpty(),
            mime = f["mime"].orEmpty(),
            fileName = f["file"].orEmpty(),
            preview = f["p.title"]?.let { LinkPreview(it, f["p.desc"].orEmpty(), f["p.site"].orEmpty()) },
        )
    }
}

enum class SortOrder(val title: String) { NEWEST("Newest"), OLDEST("Oldest"), TITLE("A–Z"), TYPE("Type") }

data class VaultFilter(
    val query: String = "",
    val type: ItemType? = null,
    val favouritesOnly: Boolean = false,
    val folder: String? = null,
    val tag: String? = null,
    val sort: SortOrder = SortOrder.NEWEST,
)

/** Search as you type over the in-memory index (FR-2.5–2.8, D3). Pinned items stay on top. */
object VaultSearch {
    fun apply(items: List<VaultItem>, filter: VaultFilter): List<VaultItem> {
        val words = filter.query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val matching = items.filter { item ->
            !item.inTrash &&
                (filter.type == null || item.type == filter.type) &&
                (!filter.favouritesOnly || item.favourite) &&
                (filter.folder == null || item.folder.equals(filter.folder, ignoreCase = true)) &&
                (filter.tag == null || item.tags.any { it.equals(filter.tag, ignoreCase = true) }) &&
                words.all { word -> haystack(item).contains(word) }
        }
        val order = when (filter.sort) {
            SortOrder.NEWEST -> compareByDescending<VaultItem> { it.created }
            SortOrder.OLDEST -> compareBy { it.created }
            SortOrder.TITLE -> compareBy { it.title.lowercase() }
            SortOrder.TYPE -> compareBy<VaultItem> { it.type.ordinal }.thenByDescending { it.created }
        }
        return matching.sortedWith(compareByDescending<VaultItem> { it.pinned }.then(order))
    }

    private fun haystack(item: VaultItem) = listOf(
        item.title, item.notes, item.text, item.url, item.folder, item.fileName, item.tags.joinToString(" "),
        item.preview?.title.orEmpty(), item.preview?.description.orEmpty(),
    ).joinToString(" ").lowercase()
}
