package dev.akibh.mycompanion.library.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.akibh.mycompanion.library.domain.Availability
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether a linked book's file is still there and readable. Cheap: one provider query per book. */
@Singleton
class FileAvailability @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun check(uri: String): Availability = withContext(Dispatchers.IO) { checkNow(Uri.parse(uri)) }

    suspend fun checkAll(uris: Collection<String>): Map<String, Availability> =
        withContext(Dispatchers.IO) { uris.associateWith { checkNow(Uri.parse(it)) } }

    private fun checkNow(uri: Uri): Availability {
        if (uri.scheme == "content") {
            val granted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
            if (!granted) return Availability.NO_ACCESS
        }
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) Availability.OK else Availability.MISSING }
                ?: Availability.MISSING
        } catch (_: SecurityException) {
            Availability.NO_ACCESS
        } catch (_: Exception) {
            // Providers report a deleted document as FileNotFoundException or IllegalArgumentException.
            Availability.MISSING
        }
    }
}
