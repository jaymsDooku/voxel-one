#!/usr/bin/env python3
"""Verify a trusted handoff checksum, every member and exact checkout; never extract."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import tarfile

ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def safe_member(name):
    path = PurePosixPath(name)
    return (str(path) == name and not path.is_absolute() and '..' not in path.parts
            and (name == 'docs/structure-benchmark-preparation.md'
                 or name in ('target/structure-fixtures/city-v1.vxbf.gz',
                             'target/structure-fixtures/maximum-v1.vxbf.gz',
                             'dashboard/evidence/structure-city-v1.json',
                             'dashboard/evidence/structure-maximum-v1.json')
                 or (len(path.parts) == 3 and path.parts[:2] == ('tools', 'structures')
                     and path.suffix in ('.java', '.py'))))


def verify(archive, manifest, checkout):
    expected = json.loads(manifest.read_text())['archive']
    if archive.stat().st_size != expected['bytes'] or digest(archive) != expected['sha256']:
        raise ValueError('Archive checksum mismatch')
    with tarfile.open(archive, 'r:') as source:
        first = source.next()
        if first is None or first.name != 'HANDOFF.json' or not first.isfile() or first.size > 1024 * 1024:
            raise ValueError('Invalid handoff header')
        handoff = json.load(source.extractfile(first))
        members = handoff['members']
        if not all(safe_member(name) and 0 <= value['bytes'] <= 64 * 1024 * 1024 for name, value in members.items()):
            raise ValueError('Invalid member allowlist')
        seen = set()
        for member in source:
            if member.name == 'HANDOFF.json' and not seen:
                # tarfile iteration starts at the cached first member.
                seen.add('HANDOFF.json')
                continue
            if (member.name in seen or member.name not in members or not member.isfile()
                    or member.sparse or member.pax_headers):
                raise ValueError('Unexpected, duplicate or nonregular member')
            expected_member = members[member.name]
            if member.size != expected_member['bytes']:
                raise ValueError('Member size mismatch')
            stream = source.extractfile(member)
            if hashlib.file_digest(stream, 'sha256').hexdigest() != expected_member['sha256']:
                raise ValueError('Member checksum mismatch')
            seen.add(member.name)
        if seen != set(members) | {'HANDOFF.json'}:
            raise ValueError('Missing member')
    hashes = handoff['required_checkout_source_sha256']
    actual_paths = {str(p.relative_to(checkout)) for p in (checkout / 'src/main/java').rglob('*.java')}
    actual_paths.add('pom.xml')
    if actual_paths != set(hashes):
        raise ValueError('Checkout source set differs; rebuild handoff after integration')
    for name, sha256 in hashes.items():
        path = checkout / name
        if path.is_symlink() or not path.resolve().is_relative_to(checkout.resolve()) or digest(path) != sha256:
            raise ValueError('Checkout source checksum mismatch')
    return dict(archive_sha256=expected['sha256'], verified_members=len(members),
                verified_checkout_sources=len(hashes), qualification='NOT QUALIFIED')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--manifest', type=Path, required=True, help='Trusted controller-published external manifest')
    parser.add_argument('--checkout', type=Path, default=ROOT)
    args = parser.parse_args()
    print(json.dumps(verify(args.archive, args.manifest, args.checkout), sort_keys=True))


if __name__ == '__main__':
    main()
