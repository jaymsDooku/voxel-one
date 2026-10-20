#!/usr/bin/env python3
"""Build an allowlisted, deterministic execution handoff; no publication or runtime data."""
import hashlib
import io
import json
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'target/structure-handoff'
REPORT = ROOT / 'dashboard/evidence/structure-execution-handoff.json'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    tools = sorted(p for p in (ROOT / 'tools/structures').iterdir() if p.suffix in ('.py', '.java'))
    files = tools + [ROOT / 'docs/structure-benchmark-preparation.md']
    for name in ('city', 'maximum'):
        fixture = ROOT / f'target/structure-fixtures/{name}-v1.vxbf.gz'
        manifest = ROOT / f'dashboard/evidence/structure-{name}-v1.json'
        expected = json.loads(manifest.read_text())
        if digest(fixture) != expected['compressed_sha256'] or fixture.stat().st_size != expected['compressed_bytes']:
            raise ValueError(f'Fixture checksum mismatch: {name}')
        files += [fixture, manifest]
    if any(p.is_symlink() or not p.is_file() for p in files):
        raise ValueError('Handoff members must be regular files')
    members = {str(p.relative_to(ROOT)): dict(bytes=p.stat().st_size, sha256=digest(p)) for p in files}
    production = sorted((ROOT / 'src/main/java').rglob('*.java')) + [ROOT / 'pom.xml']
    if any(p.is_symlink() for p in production):
        raise ValueError('Source symlink')
    source_hashes = {str(p.relative_to(ROOT)): digest(p) for p in production}
    handoff = dict(schema=1, qualification='NOT QUALIFIED', members=members,
                   required_checkout_source_sha256=source_hashes,
                   source_checkout='Use the controller-published assigned feature checkout; verify all source hashes before compiling',
                   scope='Fixtures and CPU primitive probe only; not a complete physical-GPU/network qualification harness',
                   runbook='docs/structure-benchmark-preparation.md',
                   external_requirement='Reference-machine execution remains pending; sanitized geometry/model-instance, rendering/server/network measurements are required before supported limits or stable rollout',
                   excluded='Runtime logs, credentials, account data, environments, runner configuration and private dashboard answers')
    archive = OUTPUT / 'structure-prerequisite-v1.tar'
    temporary = archive.with_suffix('.tar.tmp')
    with tarfile.open(temporary, 'w', format=tarfile.USTAR_FORMAT) as output:
        payload = json.dumps(handoff, sort_keys=True, indent=2).encode() + b'\n'
        info = tarfile.TarInfo('HANDOFF.json')
        info.size = len(payload)
        info.mode = 0o644
        output.addfile(info, io.BytesIO(payload))
        for path in sorted(files):
            info = tarfile.TarInfo(str(path.relative_to(ROOT)))
            info.size = path.stat().st_size
            info.mode = 0o644
            with path.open('rb') as stream:
                output.addfile(info, stream)
    temporary.replace(archive)
    handoff['archive'] = dict(path=str(archive.relative_to(ROOT)), bytes=archive.stat().st_size,
                              sha256=digest(archive), publication='Local only; controller owns artifact transfer/publication')
    REPORT.write_text(json.dumps(handoff, indent=2, sort_keys=True) + '\n')
    print(json.dumps(handoff['archive'], sort_keys=True))


if __name__ == '__main__':
    main()
