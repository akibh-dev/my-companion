package com.akib.mycompanion.context

import android.app.AppOpsManager
import android.app.Application
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.akib.mycompanion.context.device.InstalledApp
import com.akib.mycompanion.context.device.UsageStatsForegroundApps
import com.akib.mycompanion.context.device.hasUsageAccess
import com.akib.mycompanion.context.device.launchableApps
import com.akib.mycompanion.context.di.ContextModule
import com.akib.mycompanion.core.settings.Feature
import com.akib.mycompanion.core.time.Clock
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The usage-log reader behind the "App in use" trigger, against Robolectric's system services. */
@RunWith(AndroidJUnit4::class)
class UsageStatsForegroundAppsTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val appOps = shadowOf(app.getSystemService(AppOpsManager::class.java))
    private val usage = shadowOf(app.getSystemService(UsageStatsManager::class.java))
    private val power = shadowOf(app.getSystemService(PowerManager::class.java))
    private val clock = object : Clock {
        override fun nowMillis() = NOW
        override val zone: ZoneId = ZoneId.of("Asia/Dhaka")
    }
    private val apps = UsageStatsForegroundApps(app, clock)

    private fun allowUsageAccess(mode: Int = AppOpsManager.MODE_ALLOWED) =
        appOps.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), app.packageName, mode)

    @Test
    fun `usage access follows the app-op`() {
        allowUsageAccess(AppOpsManager.MODE_IGNORED)
        assertThat(hasUsageAccess(app)).isFalse()
        assertThat(apps.hasAccess()).isFalse()

        allowUsageAccess()
        assertThat(hasUsageAccess(app)).isTrue()
        assertThat(apps.hasAccess()).isTrue()
    }

    @Test
    fun `reports the app that came to the front last`() = runTest {
        allowUsageAccess()
        usage.addEvent("com.android.chrome", NOW - 60_000, UsageEvents.Event.ACTIVITY_RESUMED)
        usage.addEvent("com.android.chrome", NOW - 30_000, UsageEvents.Event.ACTIVITY_PAUSED)
        usage.addEvent("com.google.android.apps.maps", NOW - 29_000, UsageEvents.Event.ACTIVITY_RESUMED)

        assertThat(apps.current().first()).isEqualTo("com.google.android.apps.maps")
    }

    @Test
    fun `events older than the look-back are not used`() = runTest {
        allowUsageAccess()
        usage.addEvent(
            "com.android.chrome",
            NOW - UsageStatsForegroundApps.LOOK_BACK_MILLIS - 1,
            UsageEvents.Event.ACTIVITY_RESUMED,
        )

        assertThat(apps.current().first()).isNull()
    }

    @Test
    fun `nothing is reported without usage access`() = runTest {
        allowUsageAccess(AppOpsManager.MODE_ERRORED)
        usage.addEvent("com.android.chrome", NOW - 1_000, UsageEvents.Event.ACTIVITY_RESUMED)

        assertThat(apps.current().first()).isNull()
    }

    @Test
    fun `nothing is reported while the screen is off`() = runTest {
        allowUsageAccess()
        usage.addEvent("com.android.chrome", NOW - 1_000, UsageEvents.Event.ACTIVITY_RESUMED)
        power.setIsInteractive(false)

        assertThat(apps.current().first()).isNull()
    }

    @Test
    fun `the editor lists launchable apps by name, without this app`() {
        val pm = shadowOf(app.packageManager)
        val launcher = IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        listOf(
            ComponentName("org.zebra.notes", "org.zebra.notes.Main"),
            ComponentName("com.android.chrome", "com.android.chrome.Main"),
            ComponentName(app.packageName, "${app.packageName}.Main"),
        ).forEach { component ->
            pm.addActivityIfNotPresent(component)
            pm.addIntentFilterForActivity(component, launcher)
        }

        val listed = launchableApps(app).map(InstalledApp::packageName)

        assertThat(listed).containsAtLeast("com.android.chrome", "org.zebra.notes")
        assertThat(listed).doesNotContain(app.packageName)
    }

    @Test
    fun `the permission guide explains usage access`() {
        val requirement = ContextModule.usageAccess()
        allowUsageAccess(AppOpsManager.MODE_IGNORED)

        assertThat(requirement.id).isEqualTo("usage_access")
        assertThat(requirement.feature).isEqualTo(Feature.CONTEXT_RULES)
        assertThat(requirement.isGranted(app)).isFalse()
        assertThat(requirement.settingsIntent(app).action).isEqualTo(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        assertThat(requirement.settingsIntent(app).data.toString()).isEqualTo("package:${app.packageName}")

        allowUsageAccess()
        assertThat(requirement.isGranted(app)).isTrue()
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
