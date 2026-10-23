package com.akib.mycompanion

import android.app.Application
import com.akib.mycompanion.data.notification.AndroidReminderNotifier
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidReminderNotifier.ensureChannel(this)
    }
}
