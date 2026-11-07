"""Reproduce original CC0 notification tones; no external recordings or sound libraries."""
from pathlib import Path
import math
import struct
import wave

DESTINATION = Path(__file__).resolve().parents[2] / 'app/src/main/res/raw'
RATE = 22050
for name, seconds in [('bell', 1.3), ('chime', 1.5), ('beep', 0.3)]:
    frames = []
    for i in range(int(RATE * seconds)):
        t = i / RATE
        if name == 'bell':
            value = sum(math.sin(2 * math.pi * 660 * ratio * t) * math.exp(-t * decay) * volume
                        for ratio, decay, volume in [(1, 4, .45), (2.76, 7, .18), (5.4, 10, .1)])
        elif name == 'chime':
            value = sum(.35 * math.sin(2 * math.pi * freq * (t - start)) * math.exp(-5 * (t - start))
                        for start, freq in [(0, 523.25), (.25, 659.25), (.5, 783.99)] if t >= start)
        else:
            value = .45 * math.sin(2 * math.pi * 880 * t)
        fade = min(1, t / .01, (seconds - t) / .02)
        frames.append(struct.pack('<h', int(max(-1, min(1, value * fade)) * 32767)))
    with wave.open(str(DESTINATION / f'reminder_{name}.wav'), 'wb') as output:
        output.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        output.writeframes(b''.join(frames))
