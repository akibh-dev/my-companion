package com.akib.mycompanion.data.youtube

import java.io.IOException
import org.schabi.newpipe.extractor.exceptions.AccountTerminatedException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.UnsupportedContentInCountryException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException

/**
 * A YouTube problem worded for a person ("This video is private"); the extractor's own message
 * ("JSON response is too short") stays in [cause] for logs.
 *
 * Keeps a public (String) constructor: Media3 rebuilds an error's cause from its class name and
 * message when it reaches the MediaController, so the player screen can recognise it.
 */
class YouTubeException(message: String) : IOException(message) {

    companion object {
        fun from(error: Exception): YouTubeException = YouTubeException(messageFor(error)).apply { initCause(error) }

        /** Most specific first: most of these extend ContentNotAvailableException, which extends ParsingException. */
        fun messageFor(error: Exception): String = when (error) {
            is AgeRestrictedContentException -> "This video is age-restricted and needs a YouTube account"
            is PrivateContentException -> "This video is private"
            is GeographicRestrictionException, is UnsupportedContentInCountryException ->
                "This video isn't available in your country"
            is PaidContentException, is YoutubeMusicPremiumContentException ->
                "This video needs a paid membership or YouTube Premium"
            is AccountTerminatedException -> "The channel that posted this video no longer exists"
            is ContentNotAvailableException -> "This video isn't available. It may have been removed"
            is ContentNotSupportedException -> "This kind of video can't be played"
            is SignInConfirmNotBotException, is ReCaptchaException ->
                "YouTube wants to check that you're not a bot. Try again later"
            is IOException -> "Couldn't reach YouTube. Check your internet connection"
            // ParsingException and anything else from the extractor: the link or YouTube's pages changed.
            else ->
                "Couldn't read this video from YouTube. Check the link; if it keeps happening, the app needs an update"
        }
    }
}
