package com.akib.mycompanion.vault.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.akib.mycompanion.vault.data.ItemType
import com.akib.mycompanion.vault.data.LinkPreviewer
import com.akib.mycompanion.vault.data.Vault
import com.akib.mycompanion.vault.data.VaultItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Saves whatever was shared (text, a link, images, files) straight into the vault, then closes. */
@AndroidEntryPoint
class VaultShareActivity : ComponentActivity() {

    @Inject lateinit var vault: Vault

    @Inject lateinit var previewer: LinkPreviewer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val saved = runCatching { save(intent) }.getOrElse { 0 }
            val where = if (vault.isUnlocked) "the vault" else "the vault (it moves in when you unlock)"
            Toast.makeText(
                applicationContext,
                if (saved > 0) "Saved to $where" else "Nothing to save",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private suspend fun save(intent: Intent): Int {
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        streams.forEach { saveFile(it) }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (streams.isEmpty() && !text.isNullOrEmpty()) {
            saveText(
                text,
                intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            )
        }
        return streams.size + if (streams.isEmpty() && !text.isNullOrEmpty()) 1 else 0
    }

    private suspend fun saveText(text: String, subject: String) {
        val url = URL.find(text)?.value
        if (url != null && text.length <= url.length + LINK_SLACK) {
            val preview = previewer.fetch(url)
            vault.add(
                VaultItem(
                    type = ItemType.LINK,
                    title = subject.ifBlank {
                        preview?.title?.ifBlank { null } ?: url
                    },
                    url = url,
                    preview = preview
                )
            )
        } else {
            vault.add(
                VaultItem(
                    type = ItemType.TEXT,
                    title = subject.ifBlank {
                        text.lineSequence().first().take(60)
                    },
                    text = text
                )
            )
        }
    }

    private suspend fun saveFile(uri: Uri) {
        val mime = contentResolver.getType(uri).orEmpty()
        var name = "shared"
        var size = 0L
        contentResolver.query(
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
        contentResolver.openInputStream(uri)?.use { input ->
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

    private companion object {
        val URL = Regex("""https?://\S+""")

        /** "Look at this https://…" still counts as a link; a long text with a link in it is a note. */
        const val LINK_SLACK = 40
    }
}
