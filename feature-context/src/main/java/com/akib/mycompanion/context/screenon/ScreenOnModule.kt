package com.akib.mycompanion.context.screenon

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.Feature
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Keep-screen-on's bindings, background component and permissions (FR-3.5, FR-3.10). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScreenOnModule {
    @Binds abstract fun sensors(impl: AndroidScreenOnSensors): ScreenOnSensors

    @Binds abstract fun outside(impl: AndroidOutsideDetector): OutsideDetector

    @Binds abstract fun keepAwake(impl: OverlayKeepAwake): KeepAwake

    @Binds abstract fun preferences(impl: DataStoreScreenOnPreferences): ScreenOnPreferences

    @Binds @IntoSet
    abstract fun screenOnComponent(impl: ScreenOnComponent): HostedComponent

    companion object {
        @Provides @IntoSet
        fun drawOverApps() = Requirement(
            id = "draw_over_apps",
            title = "Display over other apps",
            why = "Keeps the screen on with an invisible window while you hold the phone up outdoors.",
            feature = Feature.KEEP_SCREEN_ON,
            isGranted = OverlayPermission::granted,
            settingsIntent = OverlayPermission::settingsIntent,
            steps = listOf("Switch on \"Allow display over other apps\""),
        )

        @Provides @IntoSet
        fun screenOnLocation() = Requirement(
            id = "screen_on_location",
            title = "Location, all the time",
            why = "Tells saved places and their Wi-Fi apart from outdoors, so the screen only stays on outside.",
            feature = Feature.KEEP_SCREEN_ON,
            isGranted = {
                granted(it, Manifest.permission.ACCESS_FINE_LOCATION) &&
                    granted(it, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            settingsIntent = {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", it.packageName, null))
            },
            steps = listOf(
                "Open Permissions → Location",
                "Choose \"Allow all the time\" and keep \"Use precise location\" on",
            ),
            runtimePermissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
