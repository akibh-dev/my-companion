package com.akib.mycompanion.domain

import com.akib.mycompanion.domain.youtube.YouTubeLinks
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class YouTubeLinksTest {

    private val id = "dQw4w9WgXcQ"

    @Test
    fun `finds the id in every common link shape`() {
        listOf(
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=AbCdEf123",
            "https://www.youtube.com/watch?v=$id",
            "https://www.youtube.com/watch?feature=share&v=$id&t=42s",
            "https://m.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM$id",
            "https://www.youtube.com/shorts/$id",
            "https://youtube.com/shorts/$id?si=xyz",
            "https://www.youtube.com/live/$id?feature=shared",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube-nocookie.com/embed/$id",
            "http://youtube.com/watch?v=$id",
            "youtube.com/watch?v=$id",
            "youtu.be/$id",
            "HTTPS://WWW.YOUTUBE.COM/watch?v=$id",
        ).forEach { link ->
            assertWithMessage(link).that(YouTubeLinks.videoId(link)).isEqualTo(id)
        }
    }

    @Test
    fun `finds the link inside shared text`() {
        assertThat(YouTubeLinks.videoId("Check this out: https://youtu.be/$id?si=abc. So good!")).isEqualTo(id)
        assertThat(YouTubeLinks.videoId("Song title\nhttps://www.youtube.com/watch?v=$id\n")).isEqualTo(id)
        assertThat(YouTubeLinks.videoId("(https://youtu.be/$id)")).isEqualTo(id)
    }

    @Test
    fun `skips links that are not videos and takes the first video`() {
        val text = "Channel https://www.youtube.com/@someone and video https://youtu.be/$id"
        assertThat(YouTubeLinks.videoId(text)).isEqualTo(id)
    }

    @Test
    fun `rejects non-YouTube text and malformed ids`() {
        listOf(
            "",
            "hello world",
            "Interesting",
            "https://example.com/watch?v=$id",
            "https://notyoutube.com/watch?v=$id",
            "https://www.youtube.com/@channel",
            "https://www.youtube.com/playlist?list=PL123",
            "https://youtu.be/short",
            "https://www.youtube.com/watch?v=${id}EXTRA",
        ).forEach { text ->
            assertWithMessage(text).that(YouTubeLinks.videoId(text)).isNull()
        }
        assertThat(YouTubeLinks.isYouTubeLink("https://youtu.be/$id")).isTrue()
    }
}
