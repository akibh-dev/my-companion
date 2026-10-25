#!/usr/bin/env bash
# Usage: shot.sh NAME
# Saves the device screen to out/shots/NAME.png plus a 40% copy NAME-s.png (needs ImageMagick for the copy).
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$DIR/out}/shots"
ADB="${ADB:-${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb}"
mkdir -p "$OUT"
"$ADB" exec-out screencap -p > "$OUT/$1.png"
if command -v magick >/dev/null; then
    magick "$OUT/$1.png" -resize 40% "$OUT/$1-s.png"
fi
echo "$OUT/$1.png"
