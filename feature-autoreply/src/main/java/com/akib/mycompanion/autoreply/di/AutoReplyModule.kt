package com.akib.mycompanion.autoreply.di

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.akib.mycompanion.autoreply.data.AutoReplyDao
import com.akib.mycompanion.autoreply.data.AutoReplyDatabase
import com.akib.mycompanion.autoreply.system.AutoReplyConsumer
import com.akib.mycompanion.core.crypto.FieldCipher
import com.akib.mycompanion.core.permissions.Requirement
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.messaging.MessageConsumer
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
abstract class AutoReplyModule {
    @Binds @IntoSet
    abstract fun consumer(impl: AutoReplyConsumer): MessageConsumer

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context) = AutoReplyDatabase.create(context)

        @Provides
        fun dao(db: AutoReplyDatabase): AutoReplyDao = db.dao()

        /** The reply log's key: no unlock needed, so replies can be logged in the background. */
        @Provides
        @Singleton
        fun cipher(): FieldCipher = FieldCipher.keystore("autoreply_log")

        @Provides @IntoSet
        fun contacts() = Requirement(
            id = "contacts",
            title = "Contacts",
            why = "Tells saved contacts from strangers, for \"saved contacts only\" and missed-call texts.",
            feature = Feature.AUTO_REPLY,
            isGranted = { granted(it, Manifest.permission.READ_CONTACTS) },
            settingsIntent = ::appDetails,
            runtimePermissions = listOf(Manifest.permission.READ_CONTACTS),
        )

        @Provides @IntoSet
        fun missedCalls() = Requirement(
            id = "missed_calls",
            title = "Phone, call log and SMS",
            why = "Notices missed calls, finds the number and texts back.",
            feature = Feature.MISSED_CALL_SMS,
            isGranted = { c -> PHONE_PERMISSIONS.all { granted(c, it) } },
            settingsIntent = ::appDetails,
            steps = listOf("If Android blocks it for sideloaded apps: App info → ⋮ → \"Allow restricted settings\""),
            runtimePermissions = PHONE_PERMISSIONS,
        )

        private val PHONE_PERMISSIONS = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
        )

        private fun granted(context: Context, permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        private fun appDetails(context: Context) =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
}
