#!/usr/bin/env python3
"""Read-only public-release CLI test using an isolated Linux packaged Hub.

Downloads one bounded, verified native artifact. Does not play the game, publish,
change accounts, or qualify physical hardware. No browser is involved.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

from publication_bundle import fetch


def properties(body):
    return dict(line.split('=', 1) for line in body.decode('ascii').splitlines()
                if line and not line.startswith('#') and '=' in line)


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def run(args):
    root = Path.cwd().resolve()
    executable = Path(args.executable).resolve()
    profile = Path(args.profile).resolve()
    output = Path(args.output).resolve()
    if any(not path.is_relative_to(root) for path in (executable, profile, output)):
        raise ValueError('Keep executable, synthetic profile and evidence in assigned checkout')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({'passed': False, 'observed': 'CLI validation started; no acceptance yet.'}, indent=2)+'\n')
    if sys.platform != 'linux' or not executable.is_file() or profile.exists():
        raise ValueError('Require Linux package and a fresh synthetic profile')
    profile.mkdir(parents=True)
    cache = profile/'.voxel-one/client'
    cache.mkdir(parents=True)
    # Prevent this test package from staging a different public launcher shell.
    (cache/'hub.properties').write_text('updates=false\nserver=127.0.0.1\n')
    catalog_bytes = fetch('https://raw.githubusercontent.com/jaymsDooku/voxel-one/experience-catalog/catalog.properties', 65536)
    catalog = properties(catalog_bytes)
    (cache/'catalog.properties').write_bytes(catalog_bytes)
    build = catalog['city.build']
    if not re.fullmatch(r'playtest-[1-9][0-9]{0,9}', build):
        raise ValueError('Expected published immutable native release')
    manifest = properties(fetch('https://github.com/jaymsDooku/voxel-one/releases/download/' + build + '/update.properties', 32768))
    if manifest['revision'] != catalog['city.source']:
        raise ValueError('Live catalog and immutable native manifest differ')
    size = int(manifest['linux.size'])
    if not 0 < size <= 100*1024*1024:
        raise ValueError('Published artifact exceeds bounded CLI test budget')
    for identity in ('city', 'sandbox', 'engine-editor'):
        if any(catalog[identity+'.'+key] != catalog['city.'+key] for key in ('build', 'source', 'artifact')):
            raise ValueError('Expected one shared native artifact')
    scratch = profile/'tmp'
    empty_path = profile/'empty-path'
    scratch.mkdir(); empty_path.mkdir()
    env = os.environ.copy()
    env.pop('JDK_JAVA_OPTIONS', None)
    env['JAVA_TOOL_OPTIONS'] = '-Duser.home=' + str(profile) + ' -Djava.io.tmpdir=' + str(scratch)
    env.update({key: str(scratch) for key in ('TMPDIR', 'TMP', 'TEMP')})
    env['PATH'] = str(empty_path)
    outcomes = []
    installed = None
    stamp = None
    for identity in ('city', 'sandbox', 'engine-editor', 'space-invaders', 'unknown-fixture-id'):
        command = [str(executable), '--experience', identity, '--update-only']
        result = subprocess.run(command, env=env, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=180)
        if len(result.stdout) > 1024*1024:
            raise ValueError('CLI output exceeds bound')
        native = identity in ('city', 'sandbox', 'engine-editor')
        if (result.returncode == 0) != native:
            raise AssertionError('Unexpected CLI selection outcome: ' + identity)
        if native:
            path = Path(result.stdout.decode().strip().splitlines()[-1]).resolve()
            if not path.is_relative_to(cache) or path.stat().st_size != size or sha256(path) != manifest['linux.sha256']:
                raise AssertionError('Verified installed artifact mismatch')
            if installed is not None and (path != installed or path.stat().st_mtime_ns != stamp):
                raise AssertionError('Shared native artifact was replaced')
            installed = path
            stamp = path.stat().st_mtime_ns
        elif installed.stat().st_mtime_ns != stamp or sha256(installed) != manifest['linux.sha256']:
            raise AssertionError('Refused selection changed verified installation')
        outcomes.append({'experience': identity, 'exitCode': result.returncode,
                         'verifiedNativeFileReused': native and identity != 'city'})
    if len(list((cache/'versions').rglob('*.jar'))) != 1:
        raise AssertionError('Duplicate native artifact')
    if (cache/'hub-shell/installed.properties').exists():
        raise AssertionError('Test changed launcher-shell selection')
    report = {'playtest': 'Playtest: native Linux package with empty PATH; live catalog; City, Sandbox and editor --update-only; web/unknown selection rejection.',
        'outcomes': outcomes, 'catalogVersion': catalog['version'], 'nativeBuild': build,
        'source': manifest['revision'], 'artifactSize': size, 'artifactSha256': manifest['linux.sha256'],
        'observed': 'Three native identities resolved to one verified immutable JAR with unchanged path, bytes and mtime; web and unknown IDs refused; no shell staged.',
        'passed': True, 'limitations': ['CLI and updater integration only, no gameplay or browser authentication.',
            'Cache reuse was observed through artifact identity/hash/mtime, not packet-level network instrumentation.',
            'No second public shell generation or physical qualification claim.']}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2)+'\n')
    print('Playtest: public native CLI selectors and shared verified artifact passed; web/unknown rejection passed.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--executable', required=True)
    parser.add_argument('--profile', required=True)
    parser.add_argument('--output', required=True)
    try:
        run(parser.parse_args())
    except Exception as error:
        print('Public CLI check failed: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
