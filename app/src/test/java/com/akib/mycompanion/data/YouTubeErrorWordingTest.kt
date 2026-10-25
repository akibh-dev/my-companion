package com.akib.mycompanion.data

import com.akib.mycompanion.data.youtube.YouTubeException
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException

class YouTubeErrorWordingTest {

    private fun say(error: Exception) = YouTubeException.messageFor(error)

    @Test
    fun `the rest of the extractor errors read as sentences too`() {
        assertThat(say(PaidContentException("members only"))).contains("paid membership")
        assertThat(say(AccountTerminatedException("gone"))).contains("no longer exists")
        assertThat(say(ContentNotSupportedException("live"))).isEqualTo("This kind of video can't be played")
        assertThat(say(ReCaptchaException("captcha", "https://youtube.com"))).contains("not a bot")
        assertThat(say(UnsupportedContentInCountryException("geo"))).contains("your country")
        assertThat(say(IOException("reset"))).contains("internet connection")
        assertThat(say(IllegalStateException("weird"))).contains("Check the link")
    }

    @Test
    fun `a YouTube error keeps its sentence after crossing to the controller`() {
        // Media3 rebuilds the cause from its class name and message, so the plain constructor must work.
        val rebuilt = YouTubeException::class.java
            .getConstructor(String::class.java)
            .newInstance("This video is private")

        assertThat(rebuilt.message).isEqualTo("This video is private")
        assertThat(rebuilt).isInstanceOf(IOException::class.java)
    }
}
