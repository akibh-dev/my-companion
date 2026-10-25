#!/usr/bin/env bash
# Usage: db.sh "SELECT ...; SELECT ..."
# Copies My Companion's Room database (with its -wal/-shm files, so recent writes are included) off a
# debug build via `run-as`, and prints each query's rows. trigger_at columns also show local time.
# Uses Python's sqlite3 module, so no sqlite3 binary is needed on the device or the computer.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$DIR/out}/db"
ADB="${ADB:-${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb}"
mkdir -p "$OUT" && rm -f "$OUT"/companion.db*
for f in companion.db companion.db-wal companion.db-shm; do
    "$ADB" exec-out run-as com.akib.mycompanion cat "databases/$f" > "$OUT/$f" 2>/dev/null || rm -f "$OUT/$f"
done
python3 -I - "$OUT/companion.db" "$1" <<'PY'
import datetime, sqlite3, sys
con = sqlite3.connect(sys.argv[1])
for sql in [s for s in sys.argv[2].split(";") if s.strip()]:
    cur = con.execute(sql)
    cols = [d[0] for d in cur.description]
    print("> " + sql.strip())
    print("  " + " | ".join(cols))
    for row in cur.fetchall():
        cells = []
        for col, value in zip(cols, row):
            if col == "trigger_at" and isinstance(value, int):
                value = f"{value} ({datetime.datetime.fromtimestamp(value / 1000):%Y-%m-%d %H:%M:%S})"
            cells.append(str(value))
        print("  " + " | ".join(cells))
PY
