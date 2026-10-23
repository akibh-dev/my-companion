package com.akib.mycompanion.domain.youtube

import java.net.URI

/**
 * Finds the video id in whatever YouTube shares or the user pastes, e.g.
 * - `https://youtu.be/dQw4w9WgXcQ?si=abc`
 * - `Check this out https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s`
 * - `https://m.youtube.com/shorts/dQw4w9WgXcQ`, `/live/…`, `/embed/…`, `music.youtube.com/watch?v=…`
 * - `youtube.com/watch?v=…` without a scheme
 */
object YouTubeLinks {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val LINK = Regex(
        """(?<![\w.-])(?:https?://)?(?:[a-z0-9-]+\.)*(?:youtube\.com|youtube-nocookie\.com|youtu\.be)/\S*""",
        RegexOption.IGNORE_CASE,
    )
    private val PATH_PREFIXES = listOf("shorts", "live", "embed", "v", "e")

    fun videoId(text: String): String? {
        return LINK.findAll(text.trim())
            .mapNotNull { idFromUrl(it.value.trimEnd('.', ',', ')', '!', '?', '"', '\'')) }
            .firstOrNull()
    }

    fun isYouTubeLink(text: String): Boolean = videoId(text) != null

    private fun idFromUrl(raw: String): String? {
        val withScheme = if (raw.contains("://")) raw else "https://$raw"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }

        val candidate = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> queryParam(uri.rawQuery, "v")
            segments.size >= 2 && segments[0] in PATH_PREFIXES -> segments[1]
            else -> queryParam(uri.rawQuery, "v")
        }
        return candidate?.takeIf { ID.matches(it) }
    }

    private fun queryParam(query: String?, name: String): String? =
        query?.split('&')
            ?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it.size == 2 && it[0] == name }
            ?.get(1)
}
