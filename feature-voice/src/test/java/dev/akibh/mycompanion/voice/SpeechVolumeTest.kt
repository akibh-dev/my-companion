package dev.akibh.mycompanion.voice

import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.akibh.mycompanion.ai.SpeechAudio
import dev.akibh.mycompanion.voice.system.AudioWatcher
import org.junit.Test
import org.junit.runner.RunWith

/** Issue #19: spoken messages must follow the phone's music volume, not a separate assistant volume. */
@RunWith(AndroidJUnit4::class)
class SpeechVolumeTest {

    @Test
    fun speechPlaysAsMediaOnTheMusicVolume() {
        assertThat(SpeechAudio.ATTRIBUTES.usage).isEqualTo(AudioAttributes.USAGE_MEDIA)
        assertThat(SpeechAudio.ATTRIBUTES.contentType).isEqualTo(AudioAttributes.CONTENT_TYPE_SPEECH)
        assertThat(SpeechAudio.ATTRIBUTES.volumeControlStream).isEqualTo(AudioManager.STREAM_MUSIC)
    }

    @Test
    fun audioFocusAndRoutingUseTheSameAttributesAsTheVoice() {
        assertThat(AudioWatcher.ATTRIBUTES).isSameInstanceAs(SpeechAudio.ATTRIBUTES)
    }

    @Test
    fun boostIsAudibleButModest() {
        assertThat(SpeechAudio.BOOST_MILLIBELS).isIn(300..1200)
    }
}
