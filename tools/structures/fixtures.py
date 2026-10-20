#!/usr/bin/env python3
"""Deterministic benchmark inputs, NOT a player structure package or qualification."""
import argparse
import gzip
import hashlib
import json
import struct
import sys
import zlib
from pathlib import Path

MAGIC = b'VXBF0001'
LEAF = struct.Struct('>HHHBBBBHI')
INSTANCE = struct.Struct('>HHHHB')
SEED = 0x5317C17
VERSION = 1
ROOT = Path(__file__).resolve().parents[2]
BRANCH = 'feature/queue-38386339653031612d356232622d343634362d616262332d383538623938346339346437'
SOURCE_URL = f'https://github.com/jaymsDooku/voxel-one/blob/{BRANCH}/tools/structures/fixtures.py'


def mix(n):
    n = (n ^ SEED) & 0xffffffff
    n = ((n ^ (n >> 16)) * 0x7feb352d) & 0xffffffff
    n = ((n ^ (n >> 15)) * 0x846ca68b) & 0xffffffff
    return (n ^ (n >> 16)) & 0xffffff


def full_city(x, y, z):
    if not (0 <= x < 512 and 0 <= z < 512):
        return False
    if y == 0:
        return True
    if not 4 <= y <= 19:
        return False
    bx, lx = divmod(x - 16, 40)
    bz, lz = divmod(z - 16, 40)
    return (0 <= bx < 11 and 0 <= bz < 11 and lx < 32 and lz < 32
            and (y % 4 == 0 or lx in (0, 31) or lz in (0, 31)))


def geometry(kind):
    if kind == 'maximum':
        # Each occupied cell is separated by an empty cell on every axis.
        # Two opposite 1/16 cubes cannot touch or merge, even with equal colours.
        for i in range(16384, 4_000_000):
            x, y, z = 2 * (i % 512), 2 * (i // 262144), 2 * ((i // 512) % 512)
            for sub in (0, 15):
                yield (x, y, z, 4, sub, sub, sub, 180, mix(i * 2 + sub))
    else:
        for y in range(128):
            if y == 0:
                for z in range(512):
                    for x in range(512):
                        # Road grid has pavement and bright lane marks.
                        yield (x, y, z, 0, 0, 0, 0,
                               188 if x % 40 == 8 else 187, 0)
            elif 4 <= y <= 19:
                for bz in range(11):
                    for bx in range(11):
                        for lz in range(32):
                            for lx in range(32):
                                if y % 4 == 0 or lx in (0, 31) or lz in (0, 31):
                                    x, z = 16 + bx * 40 + lx, 16 + bz * 40 + lz
                                    yield (x, y, z, 0, 0, 0, 0, 180,
                                           mix(x + 512 * z + 262144 * y))
            if y >= 2 and y % 2 == 0:
                for z in range(16, 496, 8):
                    for x in range(480, 512, 4):
                        yield (x, y, z, 1, 0, 0, 0, 180, mix(x + z * 512 + y))


def instances(kind):
    if kind == 'maximum':
        for i in range(16384):
            yield (2 * (i % 512), 0, 2 * (i // 512), i % 512, i % 4)
    else:
        i = 0
        for y in (5, 9, 13, 17):
            for bz in range(11):
                for bx in range(11):
                    for z in range(1, 31):
                        for x in range(1, 31):
                            if i == 10240:
                                return
                            yield (16 + bx * 40 + x, y, 16 + bz * 40 + z, i % 256, i % 4)
                            i += 1


def header(kind):
    return dict(format='VXBF', version=VERSION, fixture=kind, seed=SEED,
                bounds=[512, 128, 512] if kind == 'city' else [1024, 256, 1024],
                models=256 if kind == 'city' else 512,
                model_resolution=32 if kind == 'city' else 16,
                leaves=968048 if kind == 'city' else 2 * (4_000_000 - 16384),
                instances=10240 if kind == 'city' else 16384)


def generate(kind, output):
    output.mkdir(parents=True, exist_ok=True)
    path = output / f'{kind}-v1.vxbf.gz'
    h = header(kind)
    with path.open('wb') as raw, gzip.GzipFile(filename='', fileobj=raw, mode='wb', mtime=0) as out:
        data = json.dumps(h, sort_keys=True, separators=(',', ':')).encode()
        out.write(MAGIC + struct.pack('>I', len(data)) + data)
        for model in range(h['models']):
            name = f'fixture-{model:03d}'.encode()
            out.write(struct.pack('>HBB', model, h['model_resolution'], len(name)) + name)
            # Dense, opaque, unique colours inhibit model surface merging too.
            for voxel in range(h['model_resolution'] ** 3):
                out.write(struct.pack('>I', 0xff000000 | mix(model * 32768 + voxel)))
        for leaf in geometry(kind):
            out.write(LEAF.pack(*leaf))
        for instance in instances(kind):
            out.write(INSTANCE.pack(*instance))
    return path


def exact(stream, count):
    data = stream.read(count)
    if len(data) != count:
        raise ValueError('Truncated fixture')
    return data


def verify(path):
    """Stream parse independently; reject overlaps with a bounded cell bitset."""
    if path.stat().st_size > 64 * 1024 * 1024:
        raise ValueError('Compressed limit')
    with path.open('rb') as raw:
        compressed_hash = hashlib.file_digest(raw, 'sha256').hexdigest()
    with gzip.open(path, 'rb') as source:
        decoded = 0
        digest = hashlib.sha256()

        def read(n):
            nonlocal decoded
            decoded += n
            if decoded > 512 * 1024 * 1024:
                raise ValueError('Decoded limit')
            data = exact(source, n)
            digest.update(data)
            return data

        if read(8) != MAGIC:
            raise ValueError('Wrong magic')
        length, = struct.unpack('>I', read(4))
        if length > 4096:
            raise ValueError('Header limit')
        h = json.loads(read(length))
        if not isinstance(h, dict) or h.get('fixture') not in ('city', 'maximum') or h != header(h['fixture']):
            raise ValueError('Unexpected fixture header')
        sx, sy, sz = h['bounds']
        cells = bytearray((sx * sy * sz + 7) // 8)
        occupied = 0

        def mark(x, y, z):
            nonlocal occupied
            if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
                raise ValueError('Out of bounds')
            index = x + sx * (z + sz * y)
            byte, bit = index // 8, 1 << (index % 8)
            if cells[byte] & bit:
                return False
            cells[byte] |= bit
            occupied += 1
            return True

        dependency_start = decoded
        model_hashes = set()
        for expected_id in range(h['models']):
            identity, size, n = struct.unpack('>HBB', read(4))
            if identity != expected_id or size != h['model_resolution'] or not 1 <= n <= 32:
                raise ValueError('Invalid dependency')
            name = read(n)
            voxels = read(size ** 3 * 4)
            if any(voxels[i] != 255 for i in range(0, len(voxels), 4)):
                raise ValueError('Nonopaque model')
            model_hashes.add(hashlib.sha256(name + voxels).digest())
        dependency_bytes = decoded - dependency_start
        fractional = quads = 0
        last_cell = None
        last_sub = None
        for _ in range(h['leaves']):
            x, y, z, depth, ix, iy, iz, material, color = LEAF.unpack(read(LEAF.size))
            if depth not in (0, 1, 4) or max(ix, iy, iz) >= 1 << depth or material not in (180, 187, 188) or color > 0xffffff:
                raise ValueError('Invalid geometry')
            cell = (x, y, z)
            fresh = mark(x, y, z)
            if not fresh and not (h['fixture'] == 'maximum' and cell == last_cell
                                  and last_sub == 0 and depth == 4 and (ix, iy, iz) == (15, 15, 15)):
                raise ValueError('Overlapping geometry')
            if h['fixture'] == 'maximum' and (depth != 4 or (ix, iy, iz) not in ((0, 0, 0), (15, 15, 15))):
                raise ValueError('Fragmentation invariant')
            fractional += depth != 0
            if depth:
                quads += 6  # All fractional leaves are separated by air.
            else:
                quads += sum(not full_city(x + dx, y + dy, z + dz)
                             for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0),
                                               (0, -1, 0), (0, 0, 1), (0, 0, -1)))
            last_cell, last_sub = cell, ix
        for _ in range(h['instances']):
            x, y, z, model, rotation = INSTANCE.unpack(read(INSTANCE.size))
            if model >= h['models'] or rotation > 3 or not mark(x, y, z):
                raise ValueError('Invalid or overlapping instance')
        if source.read(1):
            raise ValueError('Trailing fixture data')
        expected = 978288 if h['fixture'] == 'city' else 4_000_000
        if occupied != expected or len(model_hashes) != h['models']:
            raise ValueError(f'Count mismatch: occupied={occupied}, expected={expected}')
    return dict(**h, occupied_cells=occupied, fractional_leaf_count=fractional,
                surface_quads=quads, surface_quad_metric='exposed geometry leaf faces before greedy merging; excludes model surfaces',
                model_surface_unit_faces=h['models'] * 6 * h['model_resolution'] ** 2,
                dependency_bytes=dependency_bytes, decoded_bytes=decoded,
                compressed_bytes=path.stat().st_size, compressed_sha256=compressed_hash,
                decoded_sha256=digest.hexdigest(), artifact=path.name,
                source_url=SOURCE_URL, publication='Pending controller publication; regenerate binary from versioned source',
                generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                python_version=sys.version.split()[0], zlib_version=zlib.ZLIB_RUNTIME_VERSION,
                qualification='Unqualified intermediate benchmark input; not a player package')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('generate', 'verify'))
    parser.add_argument('fixture', choices=('city', 'maximum'))
    parser.add_argument('--output', type=Path, default=ROOT / 'target/structure-fixtures')
    parser.add_argument('--manifest', type=Path)
    args = parser.parse_args()
    path = (generate(args.fixture, args.output) if args.action == 'generate'
            else args.output / f'{args.fixture}-v1.vxbf.gz')
    result = verify(path)
    if args.manifest:
        args.manifest.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()
