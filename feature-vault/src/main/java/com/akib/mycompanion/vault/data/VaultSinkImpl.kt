package com.akib.mycompanion.vault.data

import com.akib.mycompanion.core.sinks.VaultSink
import java.io.InputStream
import javax.inject.Inject

class VaultSinkImpl @Inject constructor(private val vault: Vault, private val previewer: LinkPreviewer) : VaultSink {
    override suspend fun saveNote(title: String, text: String) =
        vault.add(VaultItem(type = ItemType.TEXT, title = title, text = text))

    override suspend fun saveLink(url: String, title: String) {
        val preview = previewer.fetch(url)
        vault.add(VaultItem(type = ItemType.LINK, title = title.ifBlank { preview?.title ?: url }, url = url, preview = preview))
    }

    override suspend fun saveFile(title: String, mime: String, fileName: String, content: InputStream) {
        val type = if (mime.startsWith("image/")) ItemType.IMAGE else ItemType.FILE
        vault.add(VaultItem(type = type, title = title, mime = mime, fileName = fileName), content)
    }
}
