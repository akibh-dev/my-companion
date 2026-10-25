#!/usr/bin/env bash
# Unit suites for every module, then real Android integration/UI tests on one explicit device.
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${ADB_SERIAL:?Set ADB_SERIAL to the test emulator serial (adb devices).}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ADB_BIN="${ADB:-$SDK/platform-tools/adb}"
RESULTS="out/feature-tests"
mkdir -p "$RESULTS"

"$ADB_BIN" -s "$ADB_SERIAL" get-state
./gradlew cleanTestDebugUnitTest testDebugUnitTest ktlintCheck lintDebug \
    :app:assembleDebug :app:assembleDebugAndroidTest \
    --no-build-cache --continue --max-workers=2 2>&1 | tee "$RESULTS/gradle.log"

"$ADB_BIN" -s "$ADB_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB_BIN" -s "$ADB_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
"$ADB_BIN" -s "$ADB_SERIAL" shell am instrument -w -r \
    com.akib.mycompanion.test/androidx.test.runner.AndroidJUnitRunner \
    2>&1 | tee "$RESULTS/instrumentation.txt"

# `am instrument` may exit zero even when a test fails; require JUnit's success footer.
python3 - "$RESULTS/instrumentation.txt" <<'PY'
import pathlib
import re
import sys

result = pathlib.Path(sys.argv[1]).read_text()
match = re.search(r"^OK \((\d+) tests\)$", result, re.MULTILINE)
if not match or "FAILURES!!!" in result or "INSTRUMENTATION_FAILED" in result:
    raise SystemExit("On-device tests failed or did not complete; inspect " + sys.argv[1])
print("Passed all " + match.group(1) + " on-device tests.")
PY
