#!/usr/bin/env python3
"""Exact-source iPhone simulator readiness and native-client evidence checks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import plistlib
import re
import struct
import subprocess
import time
import uuid
import zlib


class CheckError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


def command(args, *, cwd, timeout=60, env=None):
    try:
        result = subprocess.run(args, cwd=cwd, env=env, capture_output=True,
                                text=True, timeout=timeout, check=True)
        return result.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        # Do not copy raw build output, environment or private runtime logs.
        raise CheckError('command_failed') from None


def validate_request(head, mode, request_id):
    if not isinstance(head, str) or not re.fullmatch(r'[0-9a-f]{40}', head):
        raise CheckError('invalid_expected_head')
    if mode not in ('preflight', 'client'):
        raise CheckError('invalid_mode')
    try:
        if str(uuid.UUID(request_id)) != request_id:
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        raise CheckError('invalid_request_id') from None


def select_iphone(inventory, max_sdk=None):
    """Choose a known available pair, then CREATE a new device of that type."""
    sdk_version = None
    if max_sdk is not None:
        if not isinstance(max_sdk, str) or not re.fullmatch(r'\d+(?:\.\d+)*', max_sdk):
            raise CheckError('simulator_sdk_version_invalid')
        sdk_version = (tuple(map(int, max_sdk.split('.'))) + (0,))[:2]
    types = {d['identifier']: d for d in inventory.get('devicetypes', [])
             if d.get('identifier', '').startswith('com.apple.CoreSimulator.SimDeviceType.iPhone-')
             and d.get('name', '').startswith('iPhone')}
    candidates = []
    for runtime in inventory.get('runtimes', []):
        identifier = runtime.get('identifier', '')
        version = runtime.get('version', '')
        if (runtime.get('isAvailable') is not True
                or not identifier.startswith('com.apple.CoreSimulator.SimRuntime.iOS-')
                or not re.fullmatch(r'\d+(?:\.\d+)*', version)):
            continue
        runtime_version = tuple(map(int, version.split('.')))
        if sdk_version is not None and (runtime_version + (0,))[:2] > sdk_version:
            continue
        for device in inventory.get('devices', {}).get(identifier, []):
            device_type = types.get(device.get('deviceTypeIdentifier'))
            if device.get('isAvailable') is True and device_type:
                candidates.append((runtime_version, device_type['identifier'],
                                   runtime, device_type))
    if not candidates:
        raise CheckError('compatible_iphone_runtime_missing' if max_sdk is not None else 'available_iphone_runtime_missing')
    _, _, runtime, device_type = max(candidates, key=lambda c: (c[0], c[1]))
    return ({k: runtime[k] for k in ('identifier', 'name', 'version')},
            {k: device_type[k] for k in ('identifier', 'name')})


def local_file(base, relative):
    if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
        raise CheckError('invalid_evidence_path')
    target = base / relative
    if target.is_symlink() or not target.resolve().is_relative_to(base.resolve()):
        raise CheckError('invalid_evidence_path')
    return target


def media_metadata(base, name):
    file = local_file(base, name)
    if not file.is_file() or file.stat().st_size > 6 * 1024 * 1024:
        raise CheckError('missing_or_oversized_media')
    data = file.read_bytes()
    if file.suffix == '.png':
        if len(data) < 45 or data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
            raise CheckError('invalid_screenshot')
        width, height = struct.unpack('>II', data[16:24])
        if width < 320 or height < 568 or zlib.crc32(data[12:29]) != struct.unpack('>I', data[29:33])[0] or b'IEND' not in data:
            raise CheckError('invalid_screenshot')
    elif file.suffix == '.mp4':
        if len(data) < 16 or data[4:8] != b'ftyp':
            raise CheckError('invalid_video')
    else:
        raise CheckError('invalid_evidence_path')
    return {'path': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def native_app(app, bundle_id):
    if app.is_symlink() or not app.is_dir() or app.suffix != '.app':
        raise CheckError('native_app_missing')
    try:
        info = plistlib.loads((app / 'Info.plist').read_bytes())
    except (OSError, ValueError, plistlib.InvalidFileException):
        raise CheckError('native_app_invalid') from None
    if not isinstance(info, dict):
        raise CheckError('native_app_invalid')
    executable = info.get('CFBundleExecutable', '')
    if (info.get('CFBundleIdentifier') != bundle_id or not isinstance(executable, str)
            or not re.fullmatch(r'[A-Za-z0-9_.-]+', executable)):
        raise CheckError('native_app_invalid')
    binary = local_file(app, executable)
    try:
        with binary.open('rb') as source:
            magic = source.read(4)
    except OSError:
        raise CheckError('native_app_invalid') from None
    if magic not in (b'\xcf\xfa\xed\xfe', b'\xfe\xed\xfa\xcf', b'\xce\xfa\xed\xfe', b'\xfe\xed\xfa\xce', b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca', b'\xca\xfe\xba\xbf', b'\xbf\xba\xfe\xca'):
        raise CheckError('native_app_invalid')


def client_receipt(repo, output, head, udid):
    try:
        receipt = json.loads(local_file(output, 'client-result.json').read_text())
    except (OSError, ValueError):
        raise CheckError('native_client_receipt_missing') from None
    if not isinstance(receipt, dict):
        raise CheckError('native_client_receipt_invalid')
    bundle_id = receipt.get('bundleId', '')
    tests = receipt.get('tests', [])
    if (receipt.get('sourceHead') != head or receipt.get('simulatorUdid') != udid
            or receipt.get('application') != 'voxel-one' or receipt.get('evidenceType') != 'native-client'
            or receipt.get('synthetic') is not False or receipt.get('status') != 'passed'
            or not isinstance(bundle_id, str) or not re.fullmatch(r'[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+', bundle_id)
            or 'voxel' not in bundle_id.lower()
            or not isinstance(tests, list) or not tests or len(tests) > 30
            or any(not isinstance(t, dict) or t.get('status') != 'passed'
                   or not isinstance(t.get('name'), str) or not 5 <= len(t['name']) <= 200 for t in tests)):
        raise CheckError('native_client_receipt_invalid')
    app = local_file(repo, receipt.get('appPath'))
    native_app(app, bundle_id)
    videos = receipt.get('videos', [])
    if not isinstance(videos, list) or len(videos) > 3 or any(not isinstance(n, str) or not re.fullmatch(r'[A-Za-z0-9_-]+\.mp4', n) for n in videos):
        raise CheckError('native_client_receipt_invalid')
    return receipt, [media_metadata(output, name) for name in videos]


def run_checks(repo, output, expected_head, mode, request_id, *, run_command=command,
               system_name=None, wait=time.sleep):
    report = {'schemaVersion': 1, 'expectedHead': expected_head, 'sourceHead': None,
              'requestId': request_id, 'mode': mode, 'status': 'failed',
              'environmentReady': False, 'clientChecked': False, 'evidence': [], 'phase': 'request'}
    udid = None
    created_output = False
    def run(args, **kwargs):
        return run_command(args, cwd=repo, **kwargs)
    try:
        validate_request(expected_head, mode, request_id)
        if output.exists() or output.is_symlink():
            raise CheckError('evidence_directory_not_fresh')
        output.mkdir(parents=True)
        created_output = True
        report['phase'] = 'source'
        report['sourceHead'] = run(['git', 'rev-parse', 'HEAD'])
        if report['sourceHead'] != expected_head:
            raise CheckError('source_head_mismatch')
        try:
            run(['git', 'diff', '--quiet', 'HEAD', '--'])
        except CheckError:
            raise CheckError('source_tree_changed') from None
        if (system_name or platform.system()) != 'Darwin':
            raise CheckError('macos_required')
        report['phase'] = 'environment'
        report['environment'] = {'macOS': run(['sw_vers', '-productVersion']),
                                 'xcode': run(['xcodebuild', '-version']),
                                 'simulatorSDK': run(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-version'])}
        report['phase'] = 'inventory'
        # Cold hosted Macs may start CoreSimulatorService on this first query.
        # Retry this read once, without retrying any device-creation mutation.
        for inventory_attempt in (1, 2):
            report['inventoryAttempts'] = inventory_attempt
            try:
                inventory_json = run(['xcrun', 'simctl', 'list', '--json'], timeout=180)
                break
            except CheckError:
                if inventory_attempt == 2:
                    raise
                wait(5)
        inventory = json.loads(inventory_json)
        report['phase'] = 'selection'
        runtime, device_type = select_iphone(inventory, max_sdk=report['environment']['simulatorSDK'])
        report.update(runtime=runtime, deviceType=device_type)
        report['phase'] = 'create'
        udid = run(['xcrun', 'simctl', 'create', 'Voxel iOS ' + request_id[:8], device_type['identifier'], runtime['identifier']], timeout=120)
        try:
            if str(uuid.UUID(udid)).upper() != udid.upper():
                raise ValueError()
        except ValueError:
            udid = None
            raise CheckError('simulator_creation_invalid') from None
        report['udid'] = udid
        report['phase'] = 'boot'
        run(['xcrun', 'simctl', 'boot', udid], timeout=120)
        report['phase'] = 'bootstatus'
        run(['xcrun', 'simctl', 'bootstatus', udid, '-b'], timeout=480)
        report['phase'] = 'boot_verify'
        booted = json.loads(run(['xcrun', 'simctl', 'list', 'devices', '--json']))
        if not any(d.get('udid') == udid and d.get('state') == 'Booted' and d.get('isAvailable') is True
                   for d in booted.get('devices', {}).get(runtime['identifier'], [])):
            raise CheckError('simulator_not_booted')
        wait(2)
        report['phase'] = 'screenshot'
        run(['xcrun', 'simctl', 'io', udid, 'screenshot', str(output / 'preflight.png')])
        report['evidence'].append({**media_metadata(output, 'preflight.png'), 'kind': 'image',
                                   'description': 'Fresh iPhone simulator home screen; environment preflight, not Voxel One client evidence.'})
        report['environmentReady'] = True
        if mode == 'client':
            report['phase'] = 'client_script'
            script = local_file(repo, 'ios/check-simulator.sh')
            if not script.is_file():
                raise CheckError('native_client_script_missing')
            client_env = os.environ.copy()
            client_env.update(IOS_SIMULATOR_UDID=udid, IOS_EVIDENCE_DIR=str(output), IOS_SOURCE_HEAD=expected_head,
                              IOS_RUNTIME_ID=runtime['identifier'])
            run(['bash', str(script), udid, str(output), expected_head], timeout=1200, env=client_env)
            report['phase'] = 'client_receipt'
            receipt, videos = client_receipt(repo, output, expected_head, udid)
            report['phase'] = 'client_install'
            installed = Path(run(['xcrun', 'simctl', 'get_app_container', udid, receipt['bundleId'], 'app']))
            if not installed.is_absolute():
                raise CheckError('native_app_not_installed')
            native_app(installed, receipt['bundleId'])
            report['phase'] = 'client_launch'
            launch = run(['xcrun', 'simctl', 'launch', udid, receipt['bundleId']])
            if not re.fullmatch(re.escape(receipt['bundleId']) + r': [1-9]\d*', launch):
                raise CheckError('native_client_launch_failed')
            wait(2)
            report['phase'] = 'client_screenshot'
            run(['xcrun', 'simctl', 'io', udid, 'screenshot', str(output / 'client.png')])
            report['evidence'].append({**media_metadata(output, 'client.png'), 'kind': 'image',
                                       'description': 'Actual launched native Voxel One simulator client; captured by the controller tooling.'})
            report['evidence'].extend({**v, 'kind': 'video'} for v in videos)
            report['client'] = {'bundleId': receipt['bundleId'], 'tests': receipt['tests']}
            report['clientChecked'] = True
        report['phase'] = 'source_verify'
        if run(['git', 'rev-parse', 'HEAD']) != expected_head:
            raise CheckError('source_head_changed')
        try:
            run(['git', 'diff', '--quiet', 'HEAD', '--'])
        except CheckError:
            raise CheckError('source_tree_changed') from None
        report['status'] = 'passed'
        report['phase'] = 'complete'
    except CheckError as error:
        report['errorCode'] = error.code
        report['errorStage'] = report['phase']
        report['clientChecked'] = False
    except (ValueError, KeyError, TypeError, AttributeError, OSError):
        report['errorCode'] = 'invalid_simulator_metadata'
        report['errorStage'] = report['phase']
        report['clientChecked'] = False
    finally:
        if udid:
            report['cleanup'] = 'passed'
            for operation in ('shutdown', 'delete'):
                try:
                    run(['xcrun', 'simctl', operation, udid])
                except CheckError:
                    report['cleanup'] = 'failed'
        if created_output:
            (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-head', required=True)
    parser.add_argument('--mode', choices=('preflight', 'client'), required=True)
    parser.add_argument('--request-id', required=True)
    parser.add_argument('--repo', default=str(Path(__file__).resolve().parents[2]),
                        help='Requested source checkout; verifier can live in a separate trusted checkout')
    parser.add_argument('--evidence-dir', default='ios-evidence')
    args = parser.parse_args(argv)
    repo = Path(args.repo).resolve()
    output = (repo / args.evidence_dir).resolve()
    if not output.is_relative_to(repo) or output == repo:
        raise SystemExit('Evidence directory must stay inside the source checkout')
    report = run_checks(repo, output, args.expected_head, args.mode, args.request_id)
    print(json.dumps({'status': report['status'], 'mode': args.mode, 'sourceHead': report['sourceHead'],
                      'clientChecked': report['clientChecked'], 'errorCode': report.get('errorCode'),
                      'errorStage': report.get('errorStage')}))
    return 0 if report['status'] == 'passed' else 1


if __name__ == '__main__':
    raise SystemExit(main())
