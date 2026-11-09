package com.akib.mycompanion.debug

import com.akib.mycompanion.domain.port.WhatsAppCallFailure
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WhatsAppDiagnosticReportTest {
    private fun report(lookup: DiagnosticLookup) = WhatsAppDiagnosticReport(
        14,
        true,
        36,
        "16",
        "0.2.0",
        2,
        listOf(DiagnosticClient("com.whatsapp", "2.25.1.2", 200)),
        lookup,
    )

    @Test fun `report contains sanitized compatibility metadata and explicitly no handoff`() {
        val text = report(
            DiagnosticLookup(
                DiagnosticLookupStatus.READY,
                1,
                1,
                listOf(
                    DiagnosticCallRow(
                        "com.whatsapp",
                        "vnd.android.cursor.item/vnd.com.whatsapp.voip.call",
                        "com.whatsapp",
                    )
                ),
                "com.whatsapp",
                true,
            )
        ).text()
        assertThat(text).contains("app_contact_id=14")
        assertThat(text).contains("account_type=com.whatsapp")
        assertThat(text).contains("matched_call_rows=1")
        assertThat(text).contains("handler=available")
        assertThat(text).contains("handoff=not_attempted")
        assertThat(text).contains("call_outcome=not_observed")
    }

    @Test fun `unexpected provider values are never copied to a report`() {
        val sensitive = listOf(
            "Private Person",
            "8801711123456@s.whatsapp.net",
            "+8801711123456",
            "private_account@example.com",
        )
        val text = report(
            DiagnosticLookup(
                DiagnosticLookupStatus.PROVIDER_ERROR,
                rows = sensitive.map { DiagnosticCallRow(it, it, it) },
                selectedPackage = sensitive.last(),
            )
        ).text()
        sensitive.forEach { assertThat(text).doesNotContain(it) }
        assertThat(text).contains("unrecognized")
        assertThat(text).doesNotContain("data_id=")
    }

    @Test fun `all typed lookup failures preserve their diagnostic meaning`() {
        val expected = mapOf(
            WhatsAppCallFailure.CALL_PERMISSION_REQUIRED to DiagnosticLookupStatus.PERMISSION_MISSING,
            WhatsAppCallFailure.PERMISSION_REQUIRED to DiagnosticLookupStatus.PERMISSION_MISSING,
            WhatsAppCallFailure.PROVIDER_DENIED to DiagnosticLookupStatus.PROVIDER_DENIED,
            WhatsAppCallFailure.PROVIDER_ERROR to DiagnosticLookupStatus.PROVIDER_ERROR,
            WhatsAppCallFailure.NO_PHONEBOOK_MATCH to DiagnosticLookupStatus.NO_PHONE_BOOK_MATCH,
            WhatsAppCallFailure.NO_VOICE_ROW to DiagnosticLookupStatus.NO_CALL_ROW,
            WhatsAppCallFailure.AMBIGUOUS to DiagnosticLookupStatus.AMBIGUOUS,
            WhatsAppCallFailure.INVALID_NUMBER to DiagnosticLookupStatus.INVALID_NUMBER,
            WhatsAppCallFailure.NO_HANDLER to DiagnosticLookupStatus.NO_HANDLER,
            WhatsAppCallFailure.LAUNCH_DENIED to DiagnosticLookupStatus.LAUNCH_DENIED,
            WhatsAppCallFailure.STALE_TARGET to DiagnosticLookupStatus.STALE_TARGET,
            WhatsAppCallFailure.APP_NOT_INSTALLED to DiagnosticLookupStatus.NO_INSTALLED_VARIANT,
        )
        assertThat(expected.keys).containsExactlyElementsIn(WhatsAppCallFailure.entries)
        expected.forEach { (failure, status) -> assertThat(failure.diagnosticStatus()).isEqualTo(status) }
    }
}
