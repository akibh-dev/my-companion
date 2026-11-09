package com.akib.mycompanion.data.whatsapp

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.akib.mycompanion.di.IoDispatcher
import com.akib.mycompanion.domain.port.VoiceCallInspection
import com.akib.mycompanion.domain.port.VoiceCallLookup
import com.akib.mycompanion.domain.port.VoiceCallObservation
import com.akib.mycompanion.domain.port.VoiceCallTarget
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.port.WhatsAppCallKind
import com.akib.mycompanion.domain.port.WhatsAppGateway
import com.akib.mycompanion.domain.port.WhatsAppLaunchResult
import com.akib.mycompanion.domain.port.WhatsAppVariant
import com.akib.mycompanion.domain.whatsapp.WhatsAppNumberMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private WhatsApp contact rows are compatibility candidates, not a supported public calling API. */
class AndroidWhatsAppGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher = Dispatchers.IO,
) : WhatsAppGateway {
    override fun isInstalled(): Boolean = installedVariants().isNotEmpty()
    override fun installedVariants(): List<WhatsAppVariant> = WhatsAppVariant.entries.filter {
        isPackageInstalled(it.packageName)
    }

    override suspend fun resolveVoiceCall(digits: String): VoiceCallLookup = inspect(digits).result

    override suspend fun resolveCall(digits: String, kind: WhatsAppCallKind): VoiceCallLookup = inspect(
        digits,
        kind
    ).result

    suspend fun inspect(digits: String, kind: WhatsAppCallKind = WhatsAppCallKind.VOICE): VoiceCallInspection =
        withContext(io) { inspectBlocking(digits, kind) }

    /** Compatibility bridge only. Production presentation uses the suspending scoped lookup. */
    override fun findVoiceCallTarget(digits: String): VoiceCallTarget? =
        (inspectBlocking(digits).result as? VoiceCallLookup.Found)?.targets?.singleOrNull()

    private fun inspectBlocking(raw: String, kind: WhatsAppCallKind = WhatsAppCallKind.VOICE): VoiceCallInspection {
        fun unavailable(
            reason: WhatsAppCallFailure,
            matches: Int = 0,
            rows: Int = 0,
            observations: List<VoiceCallObservation> = emptyList()
        ) =
            VoiceCallInspection(VoiceCallLookup.Unavailable(reason), matches, rows, observations)
        val digits = WhatsAppNumberMatcher.normalize(raw) ?: return unavailable(WhatsAppCallFailure.INVALID_NUMBER)
        if (!hasContactsPermission()) return unavailable(WhatsAppCallFailure.PERMISSION_REQUIRED)
        var matches = 0
        var rowCount = 0
        val observations = mutableListOf<VoiceCallObservation>()
        return try {
            val contactIds = phonebookCandidates(digits)
            matches = contactIds.size
            if (contactIds.isEmpty()) return unavailable(WhatsAppCallFailure.NO_PHONEBOOK_MATCH)
            val projection = arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.RAW_CONTACT_ID
            )
            val placeholders = contactIds.joinToString(",") { "?" }
            val mimeTypes = if (kind == WhatsAppCallKind.VOICE) VOICE_CALL_MIME_TYPES else VIDEO_CALL_MIME_TYPES
            val mimePlaceholders = mimeTypes.joinToString(",") { "?" }
            val selection = "${ContactsContract.Data.CONTACT_ID} IN ($placeholders) AND " +
                "${ContactsContract.Data.MIMETYPE} IN ($mimePlaceholders)"
            val args = contactIds.map(Long::toString) + mimeTypes
            val targets = mutableListOf<VoiceCallTarget>()
            val accounts = mutableMapOf<Long, String?>()
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI, projection, selection,
                args.toTypedArray(), null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    rowCount++
                    val mime = cursor.getString(1)
                    val rawId = cursor.getLong(4)
                    val account = if (accounts.containsKey(rawId)) {
                        accounts[rawId]
                    } else {
                        accountType(rawId).also {
                            accounts[rawId] = it
                        }
                    }
                    val variant = candidateVariant(account, mime, kind)
                    val numberMatches = WhatsAppNumberMatcher.matches(
                        "+$digits", cursor.getString(2), cursor.getString(3)
                    )
                    val target = variant?.let {
                        VoiceCallTarget(cursor.getLong(0), mime, it, account.orEmpty(), "+$digits", kind)
                    }
                    val handler = target?.let(::canHandle) ?: false
                    observations += VoiceCallObservation(account.orEmpty(), mime, variant, numberMatches, handler)
                    if (numberMatches && target != null && variant in installedVariants()) targets += target
                }
            } ?: throw ProviderUnavailable()
            val unique = targets.distinctBy { it.dataId to it.variant }
            val problem = when {
                unique.isEmpty() -> WhatsAppCallFailure.NO_VOICE_ROW
                unique.groupBy { it.variant }.any { it.value.size > 1 } -> WhatsAppCallFailure.AMBIGUOUS
                else -> null
            }
            if (problem != null) {
                unavailable(problem, matches, rowCount, observations)
            } else {
                VoiceCallInspection(VoiceCallLookup.Found(unique), matches, rowCount, observations)
            }
        } catch (_: SecurityException) {
            unavailable(WhatsAppCallFailure.PROVIDER_DENIED, matches, rowCount, observations)
        } catch (_: Exception) {
            unavailable(WhatsAppCallFailure.PROVIDER_ERROR, matches, rowCount, observations)
        }
    }

    private fun phonebookCandidates(digits: String): List<Long> {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode("+$digits"))
        val ids = linkedSetOf<Long>()
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.NUMBER, ContactsContract.PhoneLookup.NORMALIZED_NUMBER
            ),
            null, null, null
        )
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val normalized = cursor.getString(2)?.takeIf(String::isNotBlank) ?: cursor.getString(1)
                    if (WhatsAppNumberMatcher.sameNumber("+$digits", normalized.orEmpty())) ids += cursor.getLong(0)
                }
            } ?: throw ProviderUnavailable()
        return ids.toList()
    }

    private fun accountType(rawId: Long): String? {
        val cursor = context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts._ID}=?", arrayOf(rawId.toString()), null,
        ) ?: throw ProviderUnavailable()
        return cursor.use {
            if (!it.moveToFirst()) null else it.getString(0).takeIf { _ -> !it.moveToNext() }
        }
    }

    override suspend fun launchVoiceCall(target: VoiceCallTarget): WhatsAppLaunchResult {
        if (needsCallPermission(
                target
            )
        ) {
            return WhatsAppLaunchResult.Failed(WhatsAppCallFailure.CALL_PERMISSION_REQUIRED)
        }
        val validity = withContext(io) { validateCurrentTarget(target) }
        if (validity != null) return WhatsAppLaunchResult.Failed(validity)
        return launch(callIntent(target), target.variant)
    }

    override fun startVoiceCall(target: VoiceCallTarget): Boolean =
        validateCurrentTarget(target) == null &&
            launch(callIntent(target), target.variant) == WhatsAppLaunchResult.HandedOff

    private fun validateCurrentTarget(target: VoiceCallTarget): WhatsAppCallFailure? {
        if (!hasContactsPermission()) return WhatsAppCallFailure.PERMISSION_REQUIRED
        if (!isPackageInstalled(target.packageName)) return WhatsAppCallFailure.APP_NOT_INSTALLED
        if (target.normalizedNumber.isEmpty()) return WhatsAppCallFailure.STALE_TARGET
        return try {
            val cursor = context.contentResolver.query(
                ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, target.dataId),
                arrayOf(
                    ContactsContract.Data._ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA3, ContactsContract.Data.RAW_CONTACT_ID
                ),
                null, null, null
            )
                ?: return WhatsAppCallFailure.STALE_TARGET
            cursor.use { cursor ->
                if (!cursor.moveToFirst()) return@use WhatsAppCallFailure.STALE_TARGET
                val valid = cursor.getLong(0) == target.dataId && cursor.getString(1) == target.mimeType &&
                    candidateVariant(
                        accountType(cursor.getLong(4)), cursor.getString(1), target.kind
                    ) == target.variant &&
                    WhatsAppNumberMatcher.matches(target.normalizedNumber, cursor.getString(2), cursor.getString(3))
                if (valid && !cursor.moveToNext()) null else WhatsAppCallFailure.STALE_TARGET
            }
        } catch (
            _: SecurityException
        ) {
            WhatsAppCallFailure.PROVIDER_DENIED
        } catch (_: Exception) {
            WhatsAppCallFailure.PROVIDER_ERROR
        }
    }

    override fun needsCallPermission(target: VoiceCallTarget): Boolean {
        val required = callIntent(target).resolveActivityInfo(context.packageManager, 0)?.permission
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
        return required == Manifest.permission.CALL_PHONE && granted != PackageManager.PERMISSION_GRANTED
    }

    override fun canHandle(target: VoiceCallTarget): Boolean =
        callIntent(target).resolveActivity(context.packageManager) != null

    override fun openChat(digits: String): Boolean {
        val variant = installedVariants().singleOrNull() ?: return false
        return launchChat(digits, variant) == WhatsAppLaunchResult.HandedOff
    }

    override fun launchChat(digits: String, variant: WhatsAppVariant): WhatsAppLaunchResult {
        val normalized = WhatsAppNumberMatcher.normalize(digits)
            ?: return WhatsAppLaunchResult.Failed(WhatsAppCallFailure.INVALID_NUMBER)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$normalized"))
            .setPackage(variant.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(intent, variant)
    }

    private fun callIntent(target: VoiceCallTarget): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, target.dataId), target.mimeType)
        .setPackage(target.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun launch(intent: Intent, variant: WhatsAppVariant): WhatsAppLaunchResult {
        if (!isPackageInstalled(
                variant.packageName
            )
        ) {
            return WhatsAppLaunchResult.Failed(WhatsAppCallFailure.APP_NOT_INSTALLED)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            return WhatsAppLaunchResult.Failed(
                WhatsAppCallFailure.NO_HANDLER
            )
        }
        return try {
            context.startActivity(intent)
            WhatsAppLaunchResult.HandedOff
        } catch (
            _: ActivityNotFoundException
        ) {
            WhatsAppLaunchResult.Failed(WhatsAppCallFailure.NO_HANDLER)
        } catch (
            _: SecurityException
        ) {
            WhatsAppLaunchResult.Failed(WhatsAppCallFailure.LAUNCH_DENIED)
        }
    }

    private fun hasContactsPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private class ProviderUnavailable : Exception()

    companion object {
        const val WHATSAPP = "com.whatsapp"
        const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
        val PACKAGES = listOf(WHATSAPP, WHATSAPP_BUSINESS)
        const val VOICE_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        const val BUSINESS_VOICE_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call"
        val VOICE_CALL_MIME_TYPES = listOf(VOICE_CALL_MIME, BUSINESS_VOICE_CALL_MIME)
        const val VIDEO_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
        const val BUSINESS_VIDEO_CALL_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.video.call"
        val VIDEO_CALL_MIME_TYPES = listOf(VIDEO_CALL_MIME, BUSINESS_VIDEO_CALL_MIME)

        /** Candidate private schemas only. Real installed-client behavior still needs user-led verification. */
        fun candidateVariant(
            accountType: String?,
            mime: String,
            kind: WhatsAppCallKind = WhatsAppCallKind.VOICE,
        ): WhatsAppVariant? {
            val mimeTypes = if (kind == WhatsAppCallKind.VOICE) VOICE_CALL_MIME_TYPES else VIDEO_CALL_MIME_TYPES
            return when {
                accountType == WHATSAPP && mime == mimeTypes.first() -> WhatsAppVariant.STANDARD
                accountType == WHATSAPP_BUSINESS && mime in mimeTypes -> WhatsAppVariant.BUSINESS
                else -> null
            }
        }
    }
}
