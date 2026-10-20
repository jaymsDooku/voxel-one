#!/usr/bin/env python3
"""Generate original synthetic MP3 test fixtures with the system LAME library.
Only fixture generation needs LAME; runtime and Maven tests do not.
"""
import ctypes as c
import ctypes.util
import math
from pathlib import Path

lib = c.CDLL(ctypes.util.find_library('mp3lame'))
lib.lame_init.restype = c.c_void_p
for name in ('lame_set_in_samplerate', 'lame_set_num_channels', 'lame_set_brate',
             'lame_set_VBR', 'lame_set_VBR_q', 'lame_init_params', 'lame_close'):
    getattr(lib, name).argtypes = [c.c_void_p] if name in ('lame_init_params', 'lame_close') else [c.c_void_p, c.c_int]
lib.lame_encode_buffer.argtypes = [c.c_void_p, c.POINTER(c.c_short), c.POINTER(c.c_short), c.c_int, c.POINTER(c.c_ubyte), c.c_int]
lib.lame_encode_flush.argtypes = [c.c_void_p, c.POINTER(c.c_ubyte), c.c_int]
lib.lame_get_lametag_frame.argtypes = [c.c_void_p, c.POINTER(c.c_ubyte), c.c_size_t]
lib.lame_get_lametag_frame.restype = c.c_size_t
root = Path(__file__).resolve().parents[1] / 'src/test/resources/music'
root.mkdir(parents=True, exist_ok=True)
rate = 44100
samples = (c.c_short * (rate * 6))()
for i in range(len(samples)):
    hz = 100 if i < rate * 3 else 1000
    samples[i] = int(0.45 * 32767 * math.sin(2 * math.pi * hz * i / rate))
for name, channels, vbr in [('cbr-stereo.mp3', 2, False), ('vbr-mono.mp3', 1, True)]:
    encoder = lib.lame_init()
    try:
        for setting, value in [('in_samplerate', rate), ('num_channels', channels), ('brate', 128), ('VBR', 4 if vbr else 0), ('VBR_q', 4)]:
            assert getattr(lib, 'lame_set_' + setting)(encoder, value) == 0
        assert lib.lame_init_params(encoder) == 0
        output = (c.c_ubyte * (len(samples) * 2 + 7200))()
        count = lib.lame_encode_buffer(encoder, samples, samples, len(samples), output, len(output))
        assert count > 0
        data = bytes(output[:count])
        count = lib.lame_encode_flush(encoder, output, len(output))
        assert count >= 0
        data += bytes(output[:count])
        tag_size = lib.lame_get_lametag_frame(encoder, output, len(output))
        assert tag_size <= len(output)
        if tag_size:
            data = bytes(output[:tag_size]) + data[tag_size:]
        (root / name).write_bytes(data)
        print(name, len(data), 'bytes')
    finally:
        lib.lame_close(encoder)
