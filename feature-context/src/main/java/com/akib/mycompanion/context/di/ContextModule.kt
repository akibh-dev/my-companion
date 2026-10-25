package com.akib.mycompanion.context.di

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.akib.mycompanion.context.data.ContextDao
import com.akib.mycompanion.context.data.ContextDatabase
import com.akib.mycompanion.context.data.ContextRepository
import com.akib.mycompanion.context.device.AndroidContextSensors
import com.akib.mycompanion.context.device.AndroidDeviceSettings
import com.akib.mycompanion.context.device.ContextSensors
import com.akib.mycompanion.context.device.DeviceSettings
import com.akib.mycompanion.context.device.ForegroundApps
import com.akib.mycompanion.context.device.RulesComponent
import com.akib.mycompanion.context.device.RunRecorder
import com.akib.mycompanion.context.device.StoredTakenOver
import com.akib.mycompanion.context.device.TakenOverStorage
import com.akib.mycompanion.context.device.UsageStatsForegroundApps
import com.akib.mycompanion.context.device.hasUsageAccess
import com.akib.mycompanion.core.host.HostedComponent
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.Feature
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ContextModule {
    @Binds abstract fun deviceSettings(impl: AndroidDeviceSettings): DeviceSettings

    @Binds abstract fun sensors(impl: AndroidContextSensors): ContextSensors

    @Binds abstract fun takenOver(impl: StoredTakenOver): TakenOverStorage

    @Binds abstract fun foregroundApps(impl: UsageStatsForegroundApps): ForegroundApps

    @Binds @IntoSet
    abstract fun rulesComponent(impl: RulesComponent): HostedComponent

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): ContextDatabase = ContextDatabase.create(context)

        @Provides
        fun dao(db: ContextDatabase): ContextDao = db.dao()

        @Provides
        fun runs(repository: ContextRepository) = RunRecorder(repository::recordRun)

        @Provides @IntoSet
        fun dndAccess() = Requirement(
            id = "dnd_access",
            title = "Do Not Disturb access",
            why = "Lets rules set silent mode and Do Not Disturb.",
            feature = Feature.CONTEXT_RULES,
            isGranted = { it.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted },
            settingsIntent = { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
            steps = listOf("Find My Companion and switch it on"),
        )

        @Provides @IntoSet
        fun location() = Requirement(
            id = "location",
            title = "Location, all the time",
            why = "Detects arriving at and leaving saved places, and reads the Wi-Fi network name.",
            feature = Feature.CONTEXT_RULES,
            isGranted = {
                granted(it, Manifest.permission.ACCESS_FINE_LOCATION) &&
                    granted(it, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            settingsIntent = ::appDetails,
            steps = listOf(
                "Open Permissions → Location",
                "Choose \"Allow all the time\" and keep \"Use precise location\" on"
            ),
            runtimePermissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
        )

        @Provides @IntoSet
        fun bluetooth() = Requirement(
            id = "bluetooth_connect",
            title = "Nearby devices",
            why = "Needed to see which Bluetooth device (car, headphones) is connected.",
            feature = Feature.CONTEXT_RULES,
            isGranted = { granted(it, Manifest.permission.BLUETOOTH_CONNECT) },
            settingsIntent = ::appDetails,
            runtimePermissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
        )

        @Provides @IntoSet
        fun writeSettings() = Requirement(
            id = "write_settings",
            title = "Modify system settings",
            why = "Only needed for rules that change screen brightness.",
            feature = Feature.CONTEXT_RULES,
            isGranted = { Settings.System.canWrite(it) },
            settingsIntent = {
                Intent(
                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.fromParts("package", it.packageName, null)
                )
            },
        )

        @Provides @IntoSet
        fun usageAccess() = Requirement(
            id = "usage_access",
            title = "Usage access",
            why = "Only needed for rules that start when a certain app is open.",
            feature = Feature.CONTEXT_RULES,
            isGranted = ::hasUsageAccess,
            settingsIntent = {
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", it.packageName, null))
            },
            // Android 17 words it "Permit access to app usage data"; older versions "Permit usage access".
            steps = listOf("Switch on \"Permit access to app usage data\" (or \"Permit usage access\")"),
        )

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        private fun appDetails(context: Context) =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
}
