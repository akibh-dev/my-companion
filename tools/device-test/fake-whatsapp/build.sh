#!/usr/bin/env bash
# Builds build/fake-whatsapp.apk without Gradle: aapt2 (manifest) -> javac -> d8 (dex) -> zipalign -> apksigner.
# Needs: ANDROID_HOME (default ~/Android/Sdk) with platforms/android-34 and any build-tools,
#        a JDK 17 (JAVA_HOME, or javac on PATH), and the debug keystore (~/.android/debug.keystore).
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
JAR="$SDK/platforms/android-34/android.jar"
BT="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"

rm -rf build && mkdir -p build/classes
"$BT/aapt2" link --manifest AndroidManifest.xml -I "$JAR" -o build/unsigned.apk
"$JAVAC" --release 8 -nowarn -cp "$JAR" -d build/classes src/com/whatsapp/FakeActivity.java 2>/dev/null
"$BT/d8" --lib "$JAR" --min-api 24 --output build build/classes/com/whatsapp/FakeActivity.class
cp build/unsigned.apk build/with-dex.apk
(cd build && zip -q with-dex.apk classes.dex)
"$BT/zipalign" -f 4 build/with-dex.apk build/aligned.apk
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out build/fake-whatsapp.apk build/aligned.apk 2>/dev/null
echo "built $(pwd)/build/fake-whatsapp.apk"
