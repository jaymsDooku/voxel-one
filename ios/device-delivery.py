#!/usr/bin/env python3
"""Private exact-source device archive/export. Never uploads or installs an app."""
import argparse
import contextlib
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import plistlib
import re
import shutil
import signal
import stat
import struct
import subprocess
import sys
import zipfile

BUNDLE = 'dev.jayms.voxelone.ios'
MODES = ('unsigned', 'debugging', 'release-testing', 'app-store-connect')
MAX_FILES = 20000
MAX_BYTES = 1024 * 1024 * 1024


class DeliveryError(Exception):
    """Fixed public error code; command output/protected values stay private."""


def command(args, *, repo, log=None, timeout=60):
    try:
        with log.open('ab') if log is not None else contextlib.nullcontext() as stream:
            process = subprocess.Popen(args, cwd=repo, stdout=stream if log else subprocess.PIPE,
                                       stderr=subprocess.STDOUT if log else subprocess.DEVNULL,
                                       start_new_session=True)
            try:
                output, _ = process.communicate(timeout=timeout)
            except subprocess.TimeoutExpired:
                # Stop only this request's owned group, including compiler children.
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=5)
                if process.stdout is not None:
                    process.stdout.close()
                raise DeliveryError('build_or_signing_command_timed_out') from None
            if process.returncode:
                raise DeliveryError('build_or_signing_command_failed')
            if output is not None:
                if len(output) > 65536:
                    raise DeliveryError('command_response_oversized')
                return output.decode('utf-8').strip()
            return ''
    except (OSError, UnicodeError, subprocess.SubprocessError):
        raise DeliveryError('build_or_signing_command_failed') from None


def version_fields(version, build):
    if not re.fullmatch(r'(?:0|[1-9][0-9]{0,2})(?:\.(?:0|[1-9][0-9]{0,2})){2}', version):
        raise DeliveryError('invalid_release_version')
    if not re.fullmatch(r'[1-9][0-9]{0,3}', build):
        raise DeliveryError('invalid_build_number')


def device_binary(stream):
    header = stream.read(32)
    if len(header) != 32 or header[:4] != b'\xcf\xfa\xed\xfe':
        raise DeliveryError('device_arm64_binary_required')
    cpu, = struct.unpack_from('<I', header, 4)
    count, size = struct.unpack_from('<II', header, 16)
    if (cpu != 0x100000c or struct.unpack_from('<I', header, 12)[0] != 2
            or not 1 <= count <= 4096 or not 8 <= size <= 1024 * 1024):
        raise DeliveryError('device_arm64_binary_required')
    commands = stream.read(size)
    offset = 0
    found = False
    for _ in range(count):
        if offset + 8 > len(commands):
            raise DeliveryError('invalid_device_load_commands')
        kind, length = struct.unpack_from('<II', commands, offset)
        if length < 8 or length % 8 or offset + length > len(commands):
            raise DeliveryError('invalid_device_load_commands')
        if kind == 0x32:  # LC_BUILD_VERSION: platform2=iOS;7=iOS Simulator.
            if length < 24 or struct.unpack_from('<I', commands, offset + 8)[0] != 2:
                raise DeliveryError('simulator_binary_not_installable')
            found = True
        offset += length
    if not found or offset != size:
        raise DeliveryError('device_platform_command_required')


def app_metadata(info, version, build):
    if (not isinstance(info, dict) or info.get('CFBundleIdentifier') != BUNDLE or info.get('CFBundleExecutable') != 'VoxelOne'
            or info.get('CFBundleSupportedPlatforms') != ['iPhoneOS']
            or info.get('CFBundleShortVersionString') != version or info.get('CFBundleVersion') != build
            or not isinstance(info.get('DTSDKName'), str)
            or not re.fullmatch(r'iphoneos[0-9]+(?:\.[0-9]+)*', info['DTSDKName'])):
        raise DeliveryError('device_metadata_mismatch')
    minimum = info.get('MinimumOSVersion', '')
    if not isinstance(minimum, str) or not re.fullmatch(r'[0-9]+(?:\.[0-9]+)*', minimum) or int(minimum.split('.')[0]) < 16:
        raise DeliveryError('unsupported_device_os_target')
    return {'minimumOS': minimum, 'sdk': info['DTSDKName']}


def verify_app(app, version, build):
    if any(p.is_symlink() for p in (app, app.parent, app.parent.parent, app.parent.parent.parent,
                                   app / 'Info.plist', app / 'VoxelOne')):
        raise DeliveryError('artifact_symlink_rejected')
    if not (app / 'Info.plist').is_file() or (app / 'Info.plist').stat().st_size > 65536:
        raise DeliveryError('device_metadata_missing')
    info = plistlib.loads((app / 'Info.plist').read_bytes())
    metadata = app_metadata(info, version, build)
    with (app / 'VoxelOne').open('rb') as stream:
        device_binary(stream)
    return metadata


def verify_signature(app, run, repo, log):
    profile = app / 'embedded.mobileprovision'
    if profile.is_symlink() or not profile.is_file() or not 0 < profile.stat().st_size <= 1024 * 1024:
        raise DeliveryError('device_provisioning_profile_required')
    # Do not decode/export profile device IDs, team details or certificates.
    run(['codesign', '--verify', '--deep', '--strict', '-R', 'anchor apple generic', str(app)],
        repo=repo, log=log)


def archive_zip(archive, destination):
    files = []
    total = 0
    for item in archive.rglob('*'):
        if item.is_symlink():
            raise DeliveryError('artifact_symlink_rejected')
        if item.is_dir():
            continue
        if not stat.S_ISREG(item.stat().st_mode):
            raise DeliveryError('artifact_file_type_rejected')
        total += item.stat().st_size
        files.append(item)
        if len(files) > MAX_FILES or total > MAX_BYTES:
            raise DeliveryError('artifact_size_limit')
    with zipfile.ZipFile(destination, 'w', zipfile.ZIP_DEFLATED) as output:
        for item in sorted(files):
            output.write(item, item.relative_to(archive.parent).as_posix())


def unpack_ipa(ipa, destination):
    if ipa.is_symlink() or ipa.stat().st_size > MAX_BYTES:
        raise DeliveryError('unsafe_ipa_file')
    with zipfile.ZipFile(ipa) as source:
        entries = source.infolist()
        if len(entries) > MAX_FILES or sum(x.file_size for x in entries) > MAX_BYTES:
            raise DeliveryError('artifact_size_limit')
        names = set()
        for item in entries:
            name = PurePosixPath(item.filename)
            mode = item.external_attr >> 16
            if (name.as_posix() in names or item.filename.rstrip('/') != name.as_posix()
                    or name.is_absolute() or '..' in name.parts
                    or '\\' in item.filename or '\x00' in item.filename
                    or stat.S_IFMT(mode) not in (0, stat.S_IFREG, stat.S_IFDIR) or item.flag_bits & 1
                    or not name.parts or name.parts[0] not in ('Payload', 'SwiftSupport')):
                raise DeliveryError('unsafe_ipa_entry')
            names.add(name.as_posix())
        source.extractall(destination)
    return destination / 'Payload' / 'VoxelOne.app'


def build(repo, *, source_head, version, build_number, mode='unsigned', signing_config=None,
          export_options=None, run=command, system=None):
    version_fields(version, build_number)
    if mode not in MODES or not re.fullmatch('[0-9a-f]{40}', source_head):
        raise DeliveryError('invalid_delivery_request')
    if (system or platform.system()) != 'Darwin':
        raise DeliveryError('mac_xcode_required')
    repo = repo.resolve()
    if run(['git', 'rev-parse', 'HEAD'], repo=repo) != source_head or run(['git', 'status', '--porcelain'], repo=repo):
        raise DeliveryError('exact_clean_source_required')
    signed = mode != 'unsigned'
    if signed != bool(signing_config and export_options) or (not signed and (signing_config or export_options)):
        raise DeliveryError('protected_signing_inputs_required')
    options = None
    if signed:
        for item in (signing_config, export_options):
            if item.is_symlink() or not item.is_file() or item.stat().st_size > 65536:
                raise DeliveryError('invalid_protected_signing_input')
        options = plistlib.loads(export_options.read_bytes())
        if not isinstance(options, dict):
            raise DeliveryError('invalid_protected_signing_input')
        signing_config = signing_config.resolve()
        # Local export only. No provisioning updates, upload or build-number mutation.
        options.update(method=mode, destination='export', uploadSymbols=False,
                       manageAppVersionAndBuildNumber=False, thinning='<none>')
    stem = f'VoxelOne-{version}-{build_number}-{source_head[:12]}'
    output = repo / 'ios' / 'build' / 'delivery' / stem
    if (any(p.is_symlink() for p in (repo / 'ios', repo / 'ios/build', repo / 'ios/build/delivery'))
            or not output.resolve().is_relative_to(repo)):
        raise DeliveryError('delivery_output_outside_source')
    run(['git', 'check-ignore', '--quiet', str(output)], repo=repo)
    if output.exists() or output.is_symlink():
        raise DeliveryError('delivery_output_already_exists')
    output.mkdir(parents=True, mode=0o700)
    log = output / 'build.private.log'
    archive = output / 'VoxelOne.xcarchive'
    run(['python3', 'ios/make-project.py'], repo=repo, log=log)
    args = ['xcodebuild', 'archive', '-project', 'ios/build/VoxelOne.xcodeproj', '-scheme', 'VoxelOne',
            '-configuration', 'Release', '-sdk', 'iphoneos', '-destination', 'generic/platform=iOS',
            '-archivePath', str(archive), '-derivedDataPath', str(output / 'DerivedData')]
    if signed:
        args += ['-xcconfig', str(signing_config)]
    args += [f'CODE_SIGNING_ALLOWED={"YES" if signed else "NO"}',
             f'CODE_SIGNING_REQUIRED={"YES" if signed else "NO"}', 'SKIP_INSTALL=NO', 'ARCHS=arm64',
             f'MARKETING_VERSION={version}', f'CURRENT_PROJECT_VERSION={build_number}']
    run(args, repo=repo, log=log, timeout=600)
    app = archive / 'Products' / 'Applications' / 'VoxelOne.app'
    metadata = verify_app(app, version, build_number)
    if signed:
        verify_signature(app, run, repo, log)
        private_options = output / 'ExportOptions.private.plist'
        private_options.write_bytes(plistlib.dumps(options))
        private_options.chmod(0o600)
        exported = output / 'exported'
        run(['xcodebuild', '-exportArchive', '-archivePath', str(archive), '-exportPath', str(exported),
             '-exportOptionsPlist', str(private_options)], repo=repo, log=log, timeout=180)
        ipas = list(exported.glob('*.ipa'))
        if len(ipas) != 1:
            raise DeliveryError('one_exported_ipa_required')
        extracted = output / 'verify-export'
        exported_app = unpack_ipa(ipas[0], extracted)
        verify_app(exported_app, version, build_number)
        verify_signature(exported_app, run, repo, log)
        artifact = output / (stem + '.ipa')
        shutil.copyfile(ipas[0], artifact)
    else:
        artifact = output / (stem + '-unsigned.xcarchive.zip')
        archive_zip(archive, artifact)
    if run(['git', 'rev-parse', 'HEAD'], repo=repo) != source_head or run(['git', 'status', '--porcelain'], repo=repo):
        raise DeliveryError('source_changed_during_build')
    checksum = hashlib.sha256()
    with artifact.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            checksum.update(chunk)
    digest = checksum.hexdigest()
    receipt = dict(schemaVersion=1, sourceHead=source_head, version=version, build=build_number,
                   bundleId=BUNDLE, platform='iphoneos-arm64', **metadata, mode=mode,
                   artifact=artifact.name, bytes=artifact.stat().st_size, sha256=digest,
                   signatureVerified=signed, installStatus='not_attempted' if signed else 'not_installable_unsigned',
                   uploadStatus='not_attempted', physicalQualification='pending')
    (output / 'SHA256SUMS').write_text(f'{digest}  {artifact.name}\n')
    (output / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    return output, receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-head', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--build', required=True)
    parser.add_argument('--mode', choices=MODES, default='unsigned')
    parser.add_argument('--signing-config', type=Path)
    parser.add_argument('--export-options', type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    try:
        _, receipt = build(Path(__file__).resolve().parents[1], source_head=args.source_head,
                           version=args.version, build_number=args.build, mode=args.mode,
                           signing_config=args.signing_config, export_options=args.export_options)
        print(json.dumps(receipt))
        return 0
    except DeliveryError as error:
        print('Device delivery failed: ' + str(error), file=sys.stderr)
    except (OSError, ValueError, zipfile.BadZipFile):
        print('Device delivery failed: invalid_or_unavailable_artifact', file=sys.stderr)
    return 1


if __name__ == '__main__':
    raise SystemExit(main())
