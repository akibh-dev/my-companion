package com.akib.mycompanion.capture.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.akib.mycompanion.capture.R
import com.akib.mycompanion.capture.data.CaptureFiles
import com.akib.mycompanion.capture.data.CaptureProcessor
import com.akib.mycompanion.capture.data.CaptureRepository
import com.akib.mycompanion.capture.domain.CaptureType
import com.akib.mycompanion.core.log.AppLog
import com.akib.mycompanion.core.time.Clock
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Records a voice note (FR-4.2) to AAC in an .m4a file under `filesDir/captures/`. A foreground
 * service of type `microphone`, so recording goes on when the panel closes or the screen turns off;
 * its notification has a Stop button. After saving, the note is handed to speech to text (FR-4.4).
 *
 * Started only from the visible panel, after RECORD_AUDIO was granted: Android allows a microphone
 * foreground service only while the app is in front.
 */
@AndroidEntryPoint
class CaptureRecordingService : Service() {

    @Inject lateinit var repository: CaptureRepository

    @Inject lateinit var files: CaptureFiles

    @Inject lateinit var processor: CaptureProcessor

    @Inject lateinit var status: RecordingStatus

    @Inject lateinit var clock: Clock

    @Inject lateinit var log: AppLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> finish(save = true)
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO checked at the top
    private fun start() {
        if (recorder != null) return
        val allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val inForeground = allowed && runCatching {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        }.onFailure { log.w(TAG, "couldn't start recording in the foreground", it) }.isSuccess
        if (!inForeground) {
            status.set(RecorderState.Failed("Recording needs the microphone permission"))
            stopSelf()
            return
        }
        val file = files.newFile(CaptureType.VOICE, clock.nowMillis())
        val started = runCatching {
            MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(BIT_RATE)
                setAudioSamplingRate(SAMPLE_RATE)
                setMaxDuration(MAX_DURATION_MILLIS)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) finish(save = true)
                }
                setOutputFile(file)
                prepare()
                start()
            }
        }.onFailure { log.e(TAG, "couldn't start the recorder", it) }.getOrNull()
        if (started == null) {
            file.delete()
            status.set(RecorderState.Failed("Couldn't start recording. Is another app using the microphone?"))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        recorder = started
        output = file
        status.set(RecorderState.Recording(clock.nowMillis()))
        log.i(TAG, "recording")
    }

    private fun finish(save: Boolean) {
        val current = recorder
        if (current == null) {
            stopSelf()
            return
        }
        recorder = null
        // stop() throws when almost nothing was recorded; the file is useless then.
        val complete = runCatching { current.stop() }.isSuccess
        current.release()
        val file = output
        output = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (save && complete && file != null && file.length() > 0) {
            scope.launch {
                val id = repository.addFile(CaptureType.VOICE, file)
                status.set(RecorderState.Saved(id))
                processor.transcribeLater(id)
                stopSelf()
            }
        } else {
            file?.delete()
            status.set(if (save) RecorderState.Failed("Too short to keep") else RecorderState.Idle)
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Killed mid-recording (rare): drop the half-written file rather than keep a broken one.
        recorder?.let { runCatching { it.stop() } }
        recorder?.release()
        recorder = null
        output?.delete()
        output = null
        if (status.isRecording) status.set(RecorderState.Idle)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Voice capture", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a voice note is being recorded"
            },
        )
        val stop = PendingIntent.getService(this, 0, stopIntent(this), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capture)
            .setContentTitle("Recording a voice note")
            .setContentText("Tap Stop to save it to your captures")
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(clock.nowMillis())
            .setShowWhen(true)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val TAG = "CaptureRecorder"
        private const val CHANNEL_ID = "capture_recording"
        private const val NOTIFICATION_ID = 44
        private const val ACTION_START = "com.akib.mycompanion.capture.action.START_RECORDING"
        private const val ACTION_STOP = "com.akib.mycompanion.capture.action.STOP_RECORDING"
        private const val BIT_RATE = 96_000
        private const val SAMPLE_RATE = 44_100
        private const val MAX_DURATION_MILLIS = 30 * 60 * 1000

        /** For `ContextCompat.startForegroundService`, from the visible panel only. */
        fun startIntent(context: Context): Intent =
            Intent(context, CaptureRecordingService::class.java).setAction(ACTION_START)

        /** For `startService`: stops and saves. */
        fun stopIntent(context: Context): Intent =
            Intent(context, CaptureRecordingService::class.java).setAction(ACTION_STOP)
    }
}
