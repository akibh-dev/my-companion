package com.akib.mycompanion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.akib.mycompanion.debug.BanglishDemoEntryPoint
import com.akib.mycompanion.debug.BanglishNotificationDemo
import com.akib.mycompanion.messaging.MessageListener
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Posts an actual notification: Android listener → real model → offline phone TTS callback. */
@RunWith(AndroidJUnit4::class)
class BanglishNotificationSpeechDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun notificationIsConvertedAndSpokenAloud() = runBlocking<Unit> {
        postAndVerify(BanglishNotificationDemo.MESSAGE, "আমি বাংলা বলি", 1, "banglish-notification-speech.txt")
    }

    @Test
    fun mixedNotificationIsReadWithOneVoice() = runBlocking<Unit> {
        postAndVerify(
            BanglishNotificationDemo.MIXED_MESSAGE,
            "আমি এখন বাসায়। কাল দেখা হবে. Please call me later.",
            1,
            "mixed-notification-speech.txt",
        )
    }

    @Test
    fun mixedNotificationKeepsOneVoiceAndReusesTheLoadedModel() = runBlocking<Unit> {
        val first = postAndVerify(
            BanglishNotificationDemo.MIXED_MESSAGE,
            "আমি এখন বাসায়। কাল দেখা হবে. Please call me later.",
            1,
            "single-voice-mixed-first.txt",
        )
        val second = postAndVerify(
            BanglishNotificationDemo.MIXED_MESSAGE,
            first.converted,
            1,
            "single-voice-mixed-warm.txt",
        )
        assertThat(second.voiceName).isEqualTo(first.voiceName)
        assertThat(second.voiceName).isNotEmpty()
        assertThat(second.modelLoadMillis).isEqualTo(0)
    }

    private suspend fun postAndVerify(
        message: String,
        expected: String,
        segments: Int,
        report: String,
    ): BanglishNotificationDemo.State {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertThat(manager.isNotificationListenerAccessGranted(ComponentName(context, MessageListener::class.java)))
            .isTrue()
        val demo = EntryPointAccessors.fromApplication(context, BanglishDemoEntryPoint::class.java).demo()
        withTimeout(20_000) { demo.listenerConnected.first { it } }
        demo.reset()
        manager.createNotificationChannel(
            NotificationChannel(
                BanglishNotificationDemo.CHANNEL,
                "Banglish read-aloud test",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { setSound(null, null) },
        )
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You (test)").build())
            .addMessage(
                message,
                System.currentTimeMillis(),
                Person.Builder().setName("My Companion test").build(),
            )
        val notification = NotificationCompat.Builder(context, BanglishNotificationDemo.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .build()
        // This is the only delivery mechanism: never call the observer or TTS directly here.
        manager.notify(BanglishNotificationDemo.ID, notification)
        val result = withTimeout(90_000) {
            demo.state.first { it.stage == "completed" || it.stage == "failed" }
        }
        File(context.filesDir, report).writeText(
            "source=$message\nconverted=${result.converted}\n" +
                "stage=${result.stage}\ncompleted_segments=${result.completedSegments}\ndetail=${result.detail}\n" +
                "voice_name=${result.voiceName}\nmodel_load_ms=${result.modelLoadMillis}\n" +
                "conversion_ms=${result.conversionMillis}\ntts_queue_to_start_ms=${result.ttsStartMillis}\n",
        )
        assertThat(result.converted).isEqualTo(expected)
        assertWithMessage(result.detail).that(result.stage).isEqualTo("completed")
        assertThat(result.completedSegments).isEqualTo(segments)
        return result
    }
}
