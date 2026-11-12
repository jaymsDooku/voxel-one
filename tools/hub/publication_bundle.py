#!/usr/bin/env python3
"""Prepare a controller publication bundle and verify public delivery. Never writes Git or a service."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

from publish_catalog import assemble as catalog
from shell_manifest import assemble as shell

ROOT = 'https://raw.githubusercontent.com/jaymsDooku/voxel-one/experience-catalog/'
MAX_SHELL = 100 * 1024 * 1024


def digest(data):
    return hashlib.sha256(data).hexdigest()


def preview_tag(value):
    match = re.fullmatch(r'hub-preview-([1-9][0-9]{0,4})', value)
    return match is not None and int(match[1]) <= 65535


def prepare(native, web, jar, source, tag, expected_head, output):
    if not re.fullmatch('[0-9a-f]{40}', source) or not preview_tag(tag):
        raise ValueError('Require full feature source and immutable hub-preview generation')
    if expected_head != 'absent' and not re.fullmatch('[0-9a-f]{40}', expected_head):
        raise ValueError('Require expected catalog branch head or absent')
    checkout = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], text=True).strip()).resolve()
    if checkout != Path.cwd().resolve():
        raise ValueError('Run publication preparation from the checkout root')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    if head != source:
        raise ValueError('Feature source does not match checkout')
    dirty = bool(subprocess.check_output(['git', 'status', '--porcelain', '--', 'src/main', 'tools/hub', '.github/workflows']))
    root = Path(output)
    if root.exists() and any(root.iterdir()):
        raise ValueError('Use an empty output directory; do not replace an existing generation')
    root.mkdir(parents=True, exist_ok=True)
    catalog(native, web, root / 'catalog.properties')
    shell(jar, source, tag, root)
    files = {name: digest((root / name).read_bytes()) for name in ('catalog.properties', 'shell.properties', 'voxel-one-shell.jar')}
    plan = {
        'format': 1, 'repository': 'jaymsDooku/voxel-one', 'sourceSha': source,
        'sourceKind': 'working-tree' if dirty else 'exact-commit',
        'controllerMustRegenerateFromCommittedSource': dirty,
        'destinationBranch': 'experience-catalog', 'expectedDestinationHead': expected_head,
        'releaseTag': tag, 'releaseKind': 'opt-in preview; never mark latest or stable',
        'filesSha256': files,
        'releaseAssets': ['voxel-one-shell.jar'], 'branchFiles': ['catalog.properties', 'shell.properties'],
        'sequence': [
            'Verify independent review and exact-source qualification scope; keep stable rollout gated.',
            'Recheck sourceSha and expectedDestinationHead through the fenced controller; stop on a mismatch.',
            'Publish the immutable preview shell asset first; never clobber an existing release asset.',
            'Verify the published shell SHA-256 and size before exposing shell.properties.',
            'Controller atomically promotes both metadata files with compare-and-swap against expectedDestinationHead; preserve other branch files.',
            'Run publication_bundle.py verify and actual packaged bootstrap playtests against public metadata.',
            'Repeat with a second reviewed published generation; retain both release assets and report the actual upgrade.',
        ],
        'stableRolloutQualified': False,
        'pending': ['physical GPU/DPI qualification', 'two actually published shell generations', 'current-source packaged Windows/macOS gameplay'],
    }
    (root / 'publication-plan.json').write_text(json.dumps(plan, indent=2) + '\n')
    return plan


class Redirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        parsed = urllib.parse.urlsplit(newurl)
        if parsed.scheme != 'https' or parsed.hostname not in {
            'github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com', 'raw.githubusercontent.com'
        } or parsed.username is not None or parsed.password is not None or parsed.port not in (None, 443):
            raise ValueError('Untrusted publication redirect')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch(url, maximum):
    request = urllib.request.Request(url, headers={'Cache-Control': 'no-cache', 'User-Agent': 'Voxel-One-Hub-Publication-Check/1'})
    deadline = time.monotonic() + 30
    with urllib.request.build_opener(Redirect()).open(request, timeout=10) as response:
        data = bytearray()
        while True:
            chunk = response.read1(min(65536, maximum + 1 - len(data)))
            data.extend(chunk)
            if len(data) > maximum:
                raise ValueError('Publication body exceeds bound')
            if time.monotonic() > deadline:
                raise TimeoutError('Publication transfer deadline')
            if not chunk:
                return bytes(data)


def verify(bundle, report, reader=fetch):
    root = Path(bundle)
    plan = json.loads((root / 'publication-plan.json').read_text())
    if plan.get('repository') != 'jaymsDooku/voxel-one' or plan.get('destinationBranch') != 'experience-catalog':
        raise ValueError('Unexpected publication destination')
    tag = plan.get('releaseTag', '')
    if not preview_tag(tag):
        raise ValueError('Invalid preview generation')
    if not re.fullmatch('[0-9a-f]{40}', plan.get('sourceSha', '')):
        raise ValueError('Invalid publication source')
    if set(plan.get('filesSha256', {})) != {'catalog.properties', 'shell.properties', 'voxel-one-shell.jar'} or any(not re.fullmatch('[0-9a-f]{64}', value) for value in plan['filesSha256'].values()):
        raise ValueError('Invalid publication file digests')
    checks = []
    urls = {'catalog.properties': (ROOT + 'catalog.properties', 65536),
            'shell.properties': (ROOT + 'shell.properties', 32768),
            'voxel-one-shell.jar': ('https://github.com/jaymsDooku/voxel-one/releases/download/' + tag + '/voxel-one-shell.jar', MAX_SHELL)}
    for name, (url, bound) in urls.items():
        expected = plan['filesSha256'][name]
        if digest((root / name).read_bytes()) != expected:
            raise ValueError('Local publication bundle changed')
        check = {'file': name, 'url': url, 'expectedSha256': expected}
        try:
            actual = reader(url, bound)
            check.update(observedSha256=digest(actual), status='passed' if digest(actual) == expected else 'failed', bytes=len(actual))
        except urllib.error.HTTPError as error:
            check.update(status='failed', httpStatus=error.code)
        except (OSError, ValueError, TimeoutError):
            check.update(status='failed', error='Publication unavailable, timed out, oversized or untrusted')
        checks.append(check)
    result = {'sourceSha': plan['sourceSha'], 'sourceKind': plan.get('sourceKind', 'unknown'), 'generation': tag, 'checks': checks,
              'passed': all(c['status'] == 'passed' for c in checks), 'stableRolloutQualified': False,
              'Playtest': 'Read-only HTTPS metadata and shell-artifact integration; no browser UI is involved. Physical GPU/DPI and published generation upgrade playtests are separate release gates.'}
    Path(report).parent.mkdir(parents=True, exist_ok=True)
    Path(report).write_text(json.dumps(result, indent=2) + '\n')
    return result['passed']


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='command', required=True)
    prep = sub.add_parser('prepare')
    for name in ('native-receipt', 'web-receipt', 'jar', 'source-sha', 'tag', 'expected-catalog-head', 'output'):
        prep.add_argument('--' + name, required=True)
    check = sub.add_parser('verify'); check.add_argument('--bundle', required=True); check.add_argument('--report', required=True)
    a = p.parse_args()
    if a.command == 'prepare':
        prepare(a.native_receipt, a.web_receipt, a.jar, a.source_sha, a.tag, a.expected_catalog_head, a.output)
        print('Prepared controller-only preview publication bundle; no Git/service changes made.')
    else:
        raise SystemExit(0 if verify(a.bundle, a.report) else 1)
