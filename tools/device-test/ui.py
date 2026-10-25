#!/usr/bin/env python3
"""Tiny UI driver for an emulator/device, built on `uiautomator dump` (no test APK needed).

Finds elements by their accessibility text or content description (what TalkBack reads), so it
works with Compose without test tags. Always re-reads the screen, because the keyboard moves things.

  ui.py dump                -> list visible nodes that have text or a content description
  ui.py tap  "Text or desc" -> tap the first node whose text or content-desc equals it
  ui.py tap~ "substring"    -> same, substring match (case-insensitive)
  ui.py has  "substring"    -> exit 0 if any node contains it, 1 if not
  ui.py type "some text"    -> type into the focused field (wait ~1 s after focusing it first)

Uses $ADB, or $ANDROID_HOME/platform-tools/adb, or adb on PATH. Pass -s <serial> via $ADB_SERIAL.
"""
import html
import os
import re
import shutil
import subprocess
import sys
import time


def adb_cmd():
    if os.environ.get("ADB"):
        return [os.environ["ADB"]]
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Android/Sdk")
    path = os.path.join(sdk, "platform-tools", "adb")
    cmd = [path if os.path.exists(path) else shutil.which("adb") or "adb"]
    if os.environ.get("ADB_SERIAL"):
        cmd += ["-s", os.environ["ADB_SERIAL"]]
    return cmd


ADB = adb_cmd()


def adb(*args, check=True):
    return subprocess.run([*ADB, *args], capture_output=True, text=True, check=check).stdout


def nodes():
    xml = ""
    for _ in range(3):
        xml = adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
        if "<hierarchy" in xml:
            break
        time.sleep(0.5)
    out = []
    for match in re.finditer(r"<node [^>]*>", xml):
        node = match.group(0)

        def attr(name):
            # uiautomator switches to single quotes when the value itself contains double quotes.
            found = re.search(r"\b" + name + r"""=(?:"([^"]*)"|'([^']*)')""", node)
            return html.unescape(found.group(1) if found.group(1) is not None else found.group(2)) if found else ""

        bounds = re.findall(r"\d+", attr("bounds"))
        if len(bounds) != 4:
            continue
        x1, y1, x2, y2 = map(int, bounds)
        out.append(dict(
            text=attr("text"), desc=attr("content-desc"), cls=attr("class").split(".")[-1],
            clickable=attr("clickable"), enabled=attr("enabled"), checked=attr("checked"),
            x=(x1 + x2) // 2, y=(y1 + y2) // 2,
        ))
    return out


def find(label, exact=True):
    for node in nodes():
        for value in (node["text"], node["desc"]):
            if value and ((value == label) if exact else (label.lower() in value.lower())):
                return node
    return None


def main():
    cmd, arg = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else "")
    if cmd == "dump":
        for n in nodes():
            if n["text"] or n["desc"]:
                flags = ("C" if n["clickable"] == "true" else "-")
                flags += "" if n["enabled"] == "true" else " DISABLED"
                flags += " CHECKED" if n["checked"] == "true" else ""
                print(f'{n["cls"]:<14} {flags:<10} y={n["y"]:<5} x={n["x"]:<5} text="{n["text"]}" desc="{n["desc"]}"')
    elif cmd in ("tap", "tap~"):
        n = find(arg, exact=(cmd == "tap"))
        if not n:
            print(f"NOT FOUND: {arg}")
            sys.exit(1)
        adb("shell", "input", "tap", str(n["x"]), str(n["y"]))
        print(f'tapped "{arg}" at {n["x"]},{n["y"]}')
    elif cmd == "has":
        n = find(arg, exact=False)
        print("FOUND" if n else "MISSING", arg)
        sys.exit(0 if n else 1)
    elif cmd == "type":
        # `input text` needs spaces as %s
        adb("shell", "input", "text", arg.replace(" ", "%s").replace("'", "\\'"))
        print(f'typed "{arg}"')
    else:
        print(__doc__)
        sys.exit(2)


if __name__ == "__main__":
    main()
