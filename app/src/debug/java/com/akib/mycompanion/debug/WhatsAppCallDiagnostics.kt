package com.akib.mycompanion.debug

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.akib.mycompanion.data.whatsapp.AndroidWhatsAppGateway
import com.akib.mycompanion.domain.port.VoiceCallLookup
import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.akib.mycompanion.domain.repository.ContactRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Intentionally has no dependency on the call use case and never invokes any launch method. */
class WhatsAppCallDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactRepository,
    private val gateway: AndroidWhatsAppGateway,
) {
    suspend fun inspect(contactId: Long): WhatsAppDiagnosticReport = withContext(Dispatchers.IO) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val clients = listOf("com.whatsapp", "com.whatsapp.w4b").mapNotNull { packageName ->
            packageVersion(packageName)?.let { DiagnosticClient(packageName, it.first, it.second) }
        }
        val app = packageVersion(context.packageName)
        val chosen = if (contactId > 0) contacts.get(contactId) else null
        val lookup = when {
            chosen == null -> DiagnosticLookup(DiagnosticLookupStatus.CONTACT_MISSING)
            !permission -> DiagnosticLookup(DiagnosticLookupStatus.PERMISSION_MISSING)
            else -> {
                try {
                    val inspection = gateway.inspect(chosen.digits)
                    val result = inspection.result
                    val targets = (result as? VoiceCallLookup.Found)?.targets.orEmpty()
                    val selected = targets.singleOrNull()
                    val status = when (result) {
                        is VoiceCallLookup.Found -> DiagnosticLookupStatus.READY
                        is VoiceCallLookup.Unavailable -> result.reason.diagnosticStatus()
                    }
                    DiagnosticLookup(
                        status = status,
                        phoneBookCandidates = inspection.phonebookMatches,
                        matchedCallRows = inspection.observations.count { it.numberMatches },
                        candidateRows = inspection.candidateRows,
                        rows = inspection.observations.map {
                            DiagnosticCallRow(it.accountType, it.mimeType, it.variant?.packageName.orEmpty())
                        },
                        selectedPackage = selected?.packageName,
                        handlerAvailable = selected?.let(gateway::canHandle),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SecurityException) {
                    DiagnosticLookup(DiagnosticLookupStatus.PROVIDER_DENIED)
                } catch (_: Exception) {
                    DiagnosticLookup(DiagnosticLookupStatus.PROVIDER_ERROR)
                }
            }
        }
        WhatsAppDiagnosticReport(
            contactId,
            permission,
            Build.VERSION.SDK_INT,
            Build.VERSION.RELEASE,
            app?.first,
            app?.second,
            clients,
            lookup
        )
    }

    @Suppress("DEPRECATION")
    private fun packageVersion(packageName: String): Pair<String?, Long>? = try {
        context.packageManager.getPackageInfo(packageName, 0).let { it.versionName to it.longVersionCode }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WhatsAppDiagnosticEntryPoint {
    fun whatsAppDiagnostics(): WhatsAppCallDiagnostics
    fun diagnosticContacts(): ContactRepository
}

/** Exhaustive so a new production failure cannot silently become an unrelated diagnostic result. */
internal fun WhatsAppCallFailure.diagnosticStatus(): DiagnosticLookupStatus = when (this) {
    WhatsAppCallFailure.PERMISSION_REQUIRED -> DiagnosticLookupStatus.PERMISSION_MISSING
    WhatsAppCallFailure.PROVIDER_DENIED -> DiagnosticLookupStatus.PROVIDER_DENIED
    WhatsAppCallFailure.PROVIDER_ERROR -> DiagnosticLookupStatus.PROVIDER_ERROR
    WhatsAppCallFailure.NO_PHONEBOOK_MATCH -> DiagnosticLookupStatus.NO_PHONE_BOOK_MATCH
    WhatsAppCallFailure.NO_VOICE_ROW -> DiagnosticLookupStatus.NO_CALL_ROW
    WhatsAppCallFailure.AMBIGUOUS -> DiagnosticLookupStatus.AMBIGUOUS
    WhatsAppCallFailure.INVALID_NUMBER -> DiagnosticLookupStatus.INVALID_NUMBER
    WhatsAppCallFailure.NO_HANDLER -> DiagnosticLookupStatus.NO_HANDLER
    WhatsAppCallFailure.LAUNCH_DENIED -> DiagnosticLookupStatus.LAUNCH_DENIED
    WhatsAppCallFailure.STALE_TARGET -> DiagnosticLookupStatus.STALE_TARGET
    WhatsAppCallFailure.APP_NOT_INSTALLED -> DiagnosticLookupStatus.NO_INSTALLED_VARIANT
}
