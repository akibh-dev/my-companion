package dev.akibh.mycompanion.messaging

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** An app the user can pick, e.g. to mute in voice mode. */
data class InstalledApp(val packageName: String, val label: String)

/** App names for spoken notifications and pickers. Needs the launcher `<queries>` entry in the manifest. */
@Singleton
class InstalledApps @Inject constructor(@ApplicationContext private val context: Context) {
    private val labels = ConcurrentHashMap<String, String>()

    /** The app's name as the launcher shows it; a readable guess from the package if it's hidden. */
    fun label(packageName: String): String = labels.getOrPut(packageName) {
        ChatApp.of(packageName)?.title
            ?: runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)))
                    .toString()
            }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: guess(packageName)
    }

    /** Apps with a launcher icon, by name, without this one. */
    fun launchable(): List<InstalledApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = context.packageManager
        return pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    companion object {
        /** "com.google.android.gm" → "Gm"; better than nothing for a hidden app. */
        internal fun guess(packageName: String): String =
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
