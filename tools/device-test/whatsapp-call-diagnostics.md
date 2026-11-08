# Non-calling WhatsApp lookup diagnostics

The debug APK contains `WhatsAppDiagnosticsActivity`, a non-exported diagnostic screen, and
`WhatsAppCallDiagnostics`. Neither starts a voice call, opens chat or invokes the call use case.
The release APK must contain neither these classes nor the diagnostic activity manifest entry.

For one deliberately chosen **My Companion app contact ID**, run this opt-in fixture after
installing the current debug and test APKs:

```bash
adb -s DEVICE shell am instrument -w \
  -e class 'com.akib.mycompanion.WhatsAppDiagnosticDeviceTest#inspectChosenContactWithoutLaunchingAnything' \
  -e diagnostic_contact_id APP_CONTACT_ID \
  com.akib.mycompanion.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE exec-out run-as com.akib.mycompanion cat files/whatsapp-call-diagnostics.txt
```

The ID refers to My Companion's saved contact, not a phone number or Contacts provider row.
The fixture skips when no positive ID was explicitly supplied. It does not automatically grant
contacts permission. Grant that permission manually in Android app settings if a provider
lookup is desired; otherwise the report records missing permission without querying call rows.
The non-exported screen is intentionally not available to another app or an `am start` command.
Trusted instrumentation can open it with `WhatsAppDiagnosticsActivity.intent(context, id)`.
Its permission button requests access for a lookup only; granting it cannot trigger a call.

The text report contains Android/app/client versions, installed variants, permission state,
candidate/matched-row counts, allowlisted account/MIME/package compatibility observations,
selected package and handler availability. It always states `handoff=not_attempted` and
`call_outcome=not_observed`. Names, phones, JIDs, account names, provider row IDs and exception
messages are not serialized. Unknown account/MIME/package strings are redacted as
`unrecognized`; this deliberately limits discovery rather than copying arbitrary provider data.

Other fixtures in `WhatsAppDiagnosticDeviceTest` check the non-exported debug host and saved
ID across recreation, a synthetic denied-permission context whose `startActivity` fails the
test, and recreation of the real production recovery host for a verified missing app contact.
They cannot demonstrate real WhatsApp ringing or the OS contacts permission dialog on a phone.
Production permission-state consumption is additionally covered by the call-flow unit tests.
The existing fake WhatsApp APK/provider tests establish intent routing only.

Start real-client checks on the owner's phone with this dry run, then have the user explicitly
initiate a live call to a deliberately selected recipient. A handler resolving or accepting an
intent does not prove that WhatsApp rings or connects the call.
