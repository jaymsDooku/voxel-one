#!/usr/bin/env python3
"""Plot actual fixture data. Diagnostic only, not a game screenshot or GPU test."""
import argparse
import gzip
import json
import struct
import zlib
from pathlib import Path
import fixtures


def scan(path, pixels, maximum):
    with gzip.open(path, 'rb') as source:
        if fixtures.exact(source, 8) != fixtures.MAGIC:
            raise ValueError('Wrong magic')
        length, = struct.unpack('>I', fixtures.exact(source, 4))
        if length > 4096:
            raise ValueError('Header limit')
        h = json.loads(fixtures.exact(source, length))
        for _ in range(h['models']):
            _, size, length = struct.unpack('>HBB', fixtures.exact(source, 4))
            fixtures.exact(source, length + size ** 3 * 4)
        for start in range(0, h['leaves'], 65536):
            data = fixtures.exact(source, min(65536, h['leaves'] - start) * fixtures.LEAF.size)
            for x, y, z, depth, ix, iy, iz, material, color in fixtures.LEAF.iter_unpack(data):
                if maximum:
                    if y != 2 or x >= 16 or z >= 16:
                        continue
                    px, pz = 512 + 32 * x + ix * 2, 32 * z + iz * 2
                    for dx in (0, 1):
                        for dz in (0, 1):
                            offset = ((pz + dz) * 1024 + px + dx) * 3
                            pixels[offset:offset + 3] = bytes((255, 180, 50))
                else:
                    # Records are ordered by height, so the topmost leaf wins.
                    offset = (z * 1024 + x) * 3
                    pixels[offset:offset + 3] = bytes((30 + y, 80 + y, 140))
        for _ in range(h['instances']):
            x, y, z, model, rotation = fixtures.INSTANCE.unpack(fixtures.exact(source, fixtures.INSTANCE.size))
            if not maximum:
                offset = (z * 1024 + x) * 3
                pixels[offset:offset + 3] = bytes((230, 90, 100))
        if source.read(1):
            raise ValueError('Trailing fixture data')


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=fixtures.ROOT / 'target/structure-fixtures')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    pixels = bytearray(bytes((15, 20, 30)) * (1024 * 512))
    scan(args.input / 'city-v1.vxbf.gz', pixels, False)
    scan(args.input / 'maximum-v1.vxbf.gz', pixels, True)
    raw = b''.join(b'\0' + pixels[y * 3072:(y + 1) * 3072] for y in range(512))
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', 1024, 512, 8, 2, 0, 0, 0))
    png += chunk(b'tEXt', b'Description\0Fixture CLI diagnostic. Left: city top view; pink=model instances. Right: maximum 16x16-cell zoom at y=2; orange=isolated fractional leaves. Not game rendering or qualification.')
    png += chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
    args.output.write_bytes(png)
    print('Playtest: scanned both generated fixture streams; wrote diagnostic PNG, not a game screenshot.')


if __name__ == '__main__':
    main()
