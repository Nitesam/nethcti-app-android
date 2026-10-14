#!/usr/bin/env python3
"""Check the synthetic RTP tone recorded by LocalCallInstrumentation -e audio true.
No real call recordings: 3 seconds each at 0, +6, +12, then 0 amplitude dB.
"""
import array
import json
import math
import sys
import wave


def verify(path):
    with wave.open(str(path)) as audio:
        assert audio.getsampwidth() == 2 and audio.getnchannels() == 1
        rate = audio.getframerate()
        samples = array.array('h', audio.readframes(audio.getnframes()))
        if sys.byteorder != 'little': samples.byteswap()
    assert len(samples) >= rate * 11, 'Incomplete tone recording'
    levels = []
    peaks = []
    for second in (1, 4, 7, 10):
        segment = samples[second * rate:(second + 1) * rate]
        levels.append(math.sqrt(sum(v * v for v in segment) / len(segment)))
        peaks.append(max(abs(v) for v in segment))
    assert levels[0] > 50, 'Missing reference tone'
    measured = [20 * math.log10(v / levels[0]) for v in levels]
    for actual, expected in zip(measured, (0, 6, 12, 0)):
        assert abs(actual - expected) <= 1.5, f'Gain error: {measured}'
    assert max(peaks) < 32760, 'Synthetic low-level tone clipped'
    return {'measured_amplitude_db': [round(v, 2) for v in measured], 'peaks': peaks,
            'result': 'PASS', 'scope': 'Digital receive path only, not physical loudspeaker loudness'}


if __name__ == '__main__':
    print(json.dumps(verify(sys.argv[1]), indent=2))
