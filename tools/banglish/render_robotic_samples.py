#!/usr/bin/env python3
"""Render style auditions locally with installed eSpeak NG; does not install a phone TTS engine."""

from pathlib import Path
import shutil
import subprocess
import wave
import xml.sax.saxutils


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "out/robotic-voice"


def main():
    executable = shutil.which("espeak-ng")
    if executable is None:
        raise SystemExit("Install eSpeak NG before rendering these offline samples")
    # Copy installed engine data into the ignored output directory; system files are never edited.
    source = Path("/usr/share/espeak-ng-data")
    if not source.is_dir():
        raise SystemExit("This renderer expects installed eSpeak NG data at /usr/share/espeak-ng-data")
    data = OUTPUT / "espeak-ng-data"
    shutil.copytree(source, data, dirs_exist_ok=True)
    for label, pitch in [("c", "155 175"), ("d", "140 160")]:
        variant = f"announcer-{label}"
        (data / "voices/!v" / variant).write_text(
            "language variant\n"
            f"name {variant}\n"
            "gender female\n"
            f"pitch {pitch}\n"
            "flutter 0\nroughness 0\nvoicing 100\nconsonants 112 110\n"
            "formant 0 104 95 120\n"
            "formant 1 113 95 120\n"
            "formant 2 123 90 120 -100\n"
            "formant 3 116 100 115\n"
            "formant 4 120 105 110\n"
            "formant 5 120 100 110\n"
            "formant 6 114 85 110\n"
            "formant 7 110 80 110\n"
            "formant 8 110 80 110\n"
            "tone 150 110 600 115 1500 130 3200 155 5000 95\n",
            encoding="utf-8",
        )
        english = (
            f"Voice {label.upper()}. Mister X just subscribed to your channel. "
            "Mister Beast just donated one dollar. Ha ha ha."
        )
        parts = [
            ("en-us", english),
            ("bn", "আমি এখন বাসায়। কাল দেখা হবে।"),
            ("en-us", "Please call me later."),
        ]
        markup = "<speak>" + " ".join(
            f'<voice name="{language}+{variant}">{xml.sax.saxutils.escape(text)}</voice>'
            for language, text in parts
        ) + "</speak>"
        script = OUTPUT / f"robotic-{label}.ssml"
        script.write_text(markup, encoding="utf-8")
        wav = OUTPUT / f"robotic-{label}.wav"
        subprocess.run(
            [executable, f"--path={OUTPUT}", "-m", "-v", f"en-us+{variant}", "-s", "170",
             "-g", "0", "-w", str(wav), "-f", str(script)],
            check=True,
        )
        with wave.open(str(wav), "rb") as audio:
            duration = audio.getnframes() / audio.getframerate()
            print(f"{label.upper()}: {duration:.2f}s, {audio.getframerate()} Hz, {wav}")


if __name__ == "__main__":
    main()
