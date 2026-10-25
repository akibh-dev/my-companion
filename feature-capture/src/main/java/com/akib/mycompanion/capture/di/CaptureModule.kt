package com.akib.mycompanion.capture.di

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.akib.mycompanion.capture.data.CaptureCipher
import com.akib.mycompanion.capture.data.CaptureDao
import com.akib.mycompanion.capture.data.CaptureDatabase
import com.akib.mycompanion.capture.data.CaptureFiles
import com.akib.mycompanion.capture.system.captureServiceEnabled
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.Feature
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CaptureDatabase = CaptureDatabase.create(context)

    @Provides
    fun dao(db: CaptureDatabase): CaptureDao = db.dao()

    /** Plan §11: Capture is encrypted, with its own Keystore key. */
    @Provides
    @Singleton
    @CaptureCipher
    fun cipher(): FieldCipher = FieldCipher.keystore("captures")

    @Provides
    @Singleton
    fun files(@ApplicationContext context: Context): CaptureFiles = CaptureFiles(File(context.filesDir, "captures"))

    // ---- What quick capture needs (permissions & health screen) ----

    /** Sideloaded apps must first "Allow restricted settings" before an accessibility service can be switched on. */
    @Provides @IntoSet
    fun accessibility() = Requirement(
        id = "capture_accessibility",
        title = "Accessibility: quick capture",
        why = "Notices both volume keys held for a second to open the capture panel, and takes screenshots. " +
            "It doesn't read the screen and never blocks the volume keys.",
        feature = Feature.CAPTURE,
        isGranted = ::accessibilityOn,
        settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        steps = listOf(
            "If it's greyed out: open App info → ⋮ (top right) → \"Allow restricted settings\", then come back",
            "Open Installed apps (or Downloaded apps) → My Companion: quick capture",
            "Switch it on and allow",
        ),
    )

    @Provides @IntoSet
    fun microphone() = Requirement(
        id = "capture_microphone",
        title = "Microphone",
        why = "Records voice notes from the capture panel, only while you're recording.",
        feature = Feature.CAPTURE,
        isGranted = { granted(it, Manifest.permission.RECORD_AUDIO) },
        settingsIntent = ::appDetails,
        steps = listOf("Open Permissions → Microphone", "Choose \"Allow only while using the app\""),
        runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO),
    )

    /** Photos go through the camera app, so no camera permission is needed: only a camera app. */
    @Provides @IntoSet
    fun camera() = Requirement(
        id = "capture_camera_app",
        title = "A camera app",
        why = "Photo captures are taken with your camera app; My Companion needs no camera permission.",
        feature = Feature.CAPTURE,
        isGranted = { Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(it.packageManager) != null },
        settingsIntent = ::appDetails,
        steps = listOf("Install or enable a camera app"),
    )

    private fun accessibilityOn(context: Context): Boolean = captureServiceEnabled(context)

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
