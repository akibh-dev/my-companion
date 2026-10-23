package com.akib.mycompanion.data.phonebook

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.port.PhonebookSource
import com.akib.mycompanion.domain.port.PickedPhone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads the phone number the user picked with ACTION_PICK on Phone.CONTENT_URI.
 * The picker grants temporary read access to that one row, so READ_CONTACTS isn't needed here.
 */
class AndroidPhonebookSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : PhonebookSource {

    override suspend fun read(uri: String): PickedPhone? = withContext(io) {
        try {
            context.contentResolver.query(
                Uri.parse(uri),
                arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val number = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: return@use null
                PickedPhone(name = cursor.getString(0).orEmpty(), number = number)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
