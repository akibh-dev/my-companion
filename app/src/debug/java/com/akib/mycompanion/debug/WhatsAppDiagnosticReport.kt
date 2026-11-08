package com.akib.mycompanion.debug

/** Only these categorical values can enter the diagnostic output. Never serialize a target/result. */
enum class DiagnosticLookupStatus {
    NOT_REQUESTED,
    CONTACT_MISSING,
    PERMISSION_MISSING,
    NO_INSTALLED_VARIANT,
    PROVIDER_DENIED,
    PROVIDER_ERROR,
    NO_PHONE_BOOK_MATCH,
    NO_CALL_ROW,
    AMBIGUOUS,
    READY,
    INVALID_NUMBER,
    NO_HANDLER,
    LAUNCH_DENIED,
    STALE_TARGET,
}

data class DiagnosticCallRow(val accountType: String, val mimeType: String, val packageName: String)

data class DiagnosticLookup(
    val status: DiagnosticLookupStatus,
    val phoneBookCandidates: Int? = null,
    val matchedCallRows: Int? = null,
    val rows: List<DiagnosticCallRow> = emptyList(),
    val selectedPackage: String? = null,
    val handlerAvailable: Boolean? = null,
    val candidateRows: Int? = null,
)

data class DiagnosticClient(val packageName: String, val versionName: String?, val versionCode: Long?)

/** A deliberately narrow report schema: no names, phones, row IDs, JIDs or account names. */
data class WhatsAppDiagnosticReport(
    val contactId: Long,
    val contactsPermission: Boolean,
    val androidSdk: Int,
    val androidRelease: String,
    val appVersion: String?,
    val appVersionCode: Long?,
    val clients: List<DiagnosticClient>,
    val lookup: DiagnosticLookup,
) {
    fun text(): String = buildString {
        appendLine("WhatsApp direct-call lookup — non-calling diagnostic")
        appendLine("app_contact_id=$contactId")
        appendLine("contacts_permission=${if (contactsPermission) "granted" else "missing"}")
        appendLine("android_sdk=$androidSdk android_release=${version(androidRelease)}")
        appendLine("app_version=${version(appVersion)} app_version_code=${appVersionCode ?: "unknown"}")
        appendLine("installed_variants=${clients.size}")
        clients.forEach {
            appendLine(
                "client=${safePackage(it.packageName)} version=${version(it.versionName)} " +
                    "code=${it.versionCode ?: "unknown"}"
            )
        }
        appendLine("lookup=${lookup.status.name.lowercase()}")
        appendLine("phone_book_candidates=${lookup.phoneBookCandidates ?: "unknown"}")
        appendLine("candidate_call_rows=${lookup.candidateRows ?: "unknown"}")
        appendLine("matched_call_rows=${lookup.matchedCallRows ?: "unknown"}")
        lookup.rows.distinct().forEach {
            appendLine(
                "account_type=${safePackage(it.accountType)} mime=${safeMime(it.mimeType)} " +
                    "package=${safePackage(it.packageName)}"
            )
        }
        appendLine("selected_package=${lookup.selectedPackage?.let(::safePackage) ?: "none_or_choice_required"}")
        val handler = lookup.handlerAvailable?.let { if (it) "available" else "unavailable" } ?: "not_checked"
        appendLine("handler=$handler")
        appendLine("handoff=not_attempted")
        appendLine("call_outcome=not_observed")
    }

    private fun version(value: String?): String = value?.takeIf {
        it.length in 1..80 && it.matches(Regex("[A-Za-z0-9._+\\-]+"))
    } ?: "unknown"

    private fun safePackage(value: String) = value.takeIf { it in PACKAGES } ?: "unrecognized"
    private fun safeMime(value: String) = value.takeIf { it in MIME_TYPES } ?: "unrecognized"

    private companion object {
        val PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        val MIME_TYPES = setOf(
            "vnd.android.cursor.item/vnd.com.whatsapp.voip.call",
            "vnd.android.cursor.item/vnd.com.whatsapp.w4b.voip.call",
        )
    }
}
