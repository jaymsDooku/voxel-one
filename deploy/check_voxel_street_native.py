#!/usr/bin/env python3
"""Exact-source graphical regressions with an allowlisted, synthetic-only artifact."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
RESULTS = {
    'catalog': 'music-catalog.txt',
    'sandbox': 'voxel-sandbox-smoke-result.txt',
    'city': 'voxel-city-entry-result.txt',
}
# These captures are produced only by the isolated test harness. Never glob profiles/logs.
CAPTURES = {
    'sandbox': ['voxel-sandbox-first-launch.png', 'voxel-sandbox-craft.png',
                'voxel-sandbox-sky.png', 'voxel-sandbox-walking.png'],
    'city': ['voxel-city-planning-entry.png', 'voxel-city-walking-entry.png'],
}


def collect(output, destination):
    """Copy known result files only; success requires all three actual workflows."""
    workflows = []
    for label, name in RESULTS.items():
        source = output / label / name
        # Do not publish failure text or runtime logs. Emit a fixed failure summary instead.
        passed = source.is_file() and source.read_text().startswith('Playtest: PASS.')
        workflows.append({'workflow': label, 'passed': passed})
        if passed:
            shutil.copyfile(source, destination / ('voxel-street-native-' + name))
        for name in CAPTURES.get(label, []):
            source = output / label / name
            if source.is_file():
                shutil.copyfile(source, destination / ('voxel-street-native-' + name))
    return workflows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--display', required=True)
    parser.add_argument('--expected-head', required=True)
    args = parser.parse_args()
    if not args.display or args.display != os.environ.get('DISPLAY'):
        parser.error('Inherited assigned DISPLAY required')
    if not re.fullmatch(r'[0-9a-f]{40}', args.expected_head):
        parser.error('Expected exact 40-character source SHA')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if head != args.expected_head:
        parser.error('Checkout differs from expected source')
    destination = ROOT / 'target/voxel-street-native-evidence'
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    # Existing runner writes three historical evidence files. Preserve their bytes.
    historical = [ROOT / 'dashboard/evidence' / ('music-regression-' + name)
                  for name in RESULTS.values()]
    original = {p: p.read_bytes() if p.exists() else None for p in historical}
    output = ROOT / 'target/music-regressions'
    if output.exists():
        shutil.rmtree(output)
    runtime = ROOT / 'target/voxel-street-native-private.txt'
    failure = None
    try:
        with runtime.open('w') as stream:
            result = subprocess.run(
                ['python3', 'deploy/run_music_regressions.py', '--display', args.display],
                cwd=ROOT, stdout=stream, stderr=stream, timeout=1350, check=False)
        exit_code = result.returncode
    except subprocess.TimeoutExpired:
        exit_code = 124
        failure = 'Native regression exceeded 1350 seconds.'
    finally:
        for path, data in original.items():
            if data is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(data)
    workflows = collect(ROOT / 'target/music-regressions', destination)
    report = {
        'sourceHead': head,
        'command': 'python3 deploy/check_voxel_street_native.py --display "$DISPLAY" --expected-head "$EXPECTED_HEAD"',
        'underlyingCommand': 'python3 deploy/run_music_regressions.py --display "$DISPLAY"',
        'platform': 'Linux X11 / Mesa software OpenGL / isolated synthetic profiles',
        'exitCode': exit_code,
        'workflows': workflows,
        'passed': exit_code == 0 and all(w['passed'] for w in workflows),
        'failure': failure,
        'playtest': 'Playtest: actual Main music selection/launch/Escape/relaunch; Sandbox break/pickup/place/craft/invalid craft and camera/HUD; City entry/road tool/cancel/walking. Per-workflow results identify observed outcomes.',
        'limitations': 'Software rendering only; no physical GPU or speaker qualification. Raw runtime logs and profiles are excluded.',
        'artifacts': {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                      for p in sorted(destination.iterdir()) if p.is_file()},
    }
    (destination / 'voxel-street-native-graphical.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'sourceHead': head, 'passed': report['passed'], 'workflows': workflows}))
    return 0 if report['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
