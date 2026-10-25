package com.akib.mycompanion.vault.data

import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches a link's title and description once, when it's saved (FR-2.11): straight from the link's
 * own site, with no cookies, no identifiers and a size limit. The vault's only network use.
 */
class LinkPreviewer(private val client: OkHttpClient) {
    @Inject constructor() : this(
        OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .callTimeout(10, TimeUnit.SECONDS)
            .build(),
    )

    suspend fun fetch(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(
                url
            ).header("User-Agent", "Mozilla/5.0").header("Accept", "text/html").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                val html = body.source().run {
                    request(MAX_BYTES)
                    buffer.clone().readUtf8(minOf(buffer.size, MAX_BYTES))
                }
                parse(html, URI(url).host.removePrefix("www."))
            }
        }.getOrNull()
    }

    companion object {
        private const val MAX_BYTES = 256 * 1024L

        fun parse(html: String, site: String): LinkPreview? {
            fun meta(name: String) = Regex(
                """<meta[^>]+(?:property|name)=["']$name["'][^>]+content=["']([^"']*)["']""",
                RegexOption.IGNORE_CASE,
            ).find(html)?.groupValues?.get(1)?.let(::unescape)
            val title = meta("og:title") ?: Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(html)
                ?.groupValues?.get(1)?.trim()?.let(::unescape)
            val description = meta("og:description") ?: meta("description")
            if (title.isNullOrBlank() && description.isNullOrBlank()) return null
            return LinkPreview(title.orEmpty().take(200), description.orEmpty().take(300), meta("og:site_name") ?: site)
        }

        private fun unescape(text: String) = text
            .replace(
                "&amp;",
                "&"
            ).replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    }
}
