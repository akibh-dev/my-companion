#!/usr/bin/env bash
# Adds two phone-book contacts to a test device/emulator through the Contacts provider:
#   "Mum Test" +880 1711-123456 with a WhatsApp voice-call row, shaped like the one WhatsApp creates
#   "Dad Test" 01811-123456 without one (to test the wa.me chat fallback)
# Run once on a fresh emulator. Check with:
#   adb shell content query --uri content://com.android.contacts/data --projection _id:raw_contact_id:mimetype:data1
set -euo pipefail
ADB="${ADB:-${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb}"
DATA=content://com.android.contacts/data

new_raw_contact() {
    "$ADB" shell content insert --uri content://com.android.contacts/raw_contacts \
        --bind account_type:n: --bind account_name:n:
    "$ADB" shell content query --uri content://com.android.contacts/raw_contacts --projection _id \
        | tail -1 | sed -E 's/.*_id=([0-9]+).*/\1/' | tr -d '\r'
}
row() { "$ADB" shell "content insert --uri $DATA $*"; }

MUM=$(new_raw_contact)
row "--bind raw_contact_id:i:$MUM --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'Mum Test'"
row "--bind raw_contact_id:i:$MUM --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:'+880 1711-123456' --bind data2:i:2"
row "--bind raw_contact_id:i:$MUM --bind mimetype:s:vnd.android.cursor.item/vnd.com.whatsapp.voip.call --bind data1:s:8801711123456@s.whatsapp.net --bind data2:s:WhatsApp --bind data3:s:'Voice call +880 1711-123456'"

DAD=$(new_raw_contact)
row "--bind raw_contact_id:i:$DAD --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'Dad Test'"
row "--bind raw_contact_id:i:$DAD --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:'01811-123456' --bind data2:i:2"

echo "Added raw contacts $MUM (Mum Test, with WhatsApp row) and $DAD (Dad Test)."
