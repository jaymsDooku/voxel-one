"""Narrow, bounded public compiler diagnostics; never export a raw client log."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import tempfile
import uuid

LOG_NAMES = ('java-build.log', 'native-build.log', 'native-tests.log')
MAX_READ_BYTES = 128 * 1024
MAX_LINES_PER_LOG = 8
MAX_LINE_BYTES = 320
MAX_ARTIFACT_BYTES = 16 * 1024
_ANSI = re.compile(r'\x1b\[[0-?]*[ -/]*[@-~]|\x1b\][^\x07]*(?:\x07|\x1b\\)')
_PRIVATE = re.compile(
    r'password|passwd|passphrase|secret|credentials?|authorization|bearer|cookie|'
    r'(?:api|agent)[ _-]?key|private[ _-]?(?:key|prompt)|owner[ _-]?answers?|'
    r'account[ _-]?(?:database|profile)|environment[ _-]?dump|'
    r'(?:access|refresh|auth|github|deepseek|oai|siwc)[ _-]?token|\btoken\s*[:=]|'
    r'\b[A-Z][A-Z0-9_]*(?:TOKEN|SECRET|PASSWORD|ACCESS_KEY|API_KEY|PRIVATE_KEY)[A-Z0-9_]*\b|'
    r'\b(?:sk-[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9_]{8,}|github_pat_[A-Za-z0-9_]+)|'
    r'\beyJ[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+){2}|-----BEGIN ', re.I)
_URL = re.compile(r'https?://[^\s<>\"]+', re.I)
_ABSOLUTE = re.compile(r'(?<![\w.-])(?:[A-Za-z]:)?/(?:[^\s<>\"\':;,()]+)')
_LOCATION_ERROR = re.compile(r'\.(?:java|swift|m|mm|c|cpp|h|hpp|kt|rs|cs|xml|gradle)(?::\d+|:\[\d+).*?(?:fatal error|error|cannot find symbol|incompatible types|cannot access|does not exist)', re.I)
_BUILD_ERROR = re.compile(
    r'^(?:fatal error:|error:|(?:clang|swift|javac|ld|xcodebuild):\s*(?:error:)?|CMake Error|'
    r'Undefined symbols for architecture|(?:BUILD|TEST) FAILURE|COMPILATION ERROR|'
    r'\*\* (?:BUILD|TEST) FAILED \*\*|Testing failed:|'
    r'Failed to (?:execute goal|collect dependencies|resolve|compile|build)|'
    r'Could not (?:resolve|find artifact|find or load main class)|'
    r'Compilation failure|(?:Unable|Failed) to locate (?:a Java Runtime|Java|JDK)|'
    r'No Java runtime present|Error opening (?:zip|archive)|'
    r'Process completed with exit code [1-9]\d*|(?:Command|Process) .*?exited (?:with )?(?:code |status )?[1-9]\d*)', re.I)
_MISSING_COMMAND = re.compile(r'(?:^|:\s*)(?:mvn|maven|java|javac|xcodebuild|clang|swift|cmake|ninja): (?:command )?not found$', re.I)
_CONTEXT = re.compile(r'^(?:symbol:|location:|reason:)', re.I)
_TEST_FAILURE = re.compile(r"^(?:FAIL(?:ED)?:|AssertionError:|Test Case .*failed|.*XCTAssert[A-Za-z]+.*failed)", re.I)


def diagnostic_line(line, repo):
    line = _ANSI.sub('', line)
    line = ''.join(c for c in line if c >= ' ' or c == '\t').strip()
    if not line or _PRIVATE.search(line):
        return None
    line = _URL.sub('[url]', line)
    line = line.replace(str(repo.resolve()) + '/', '').replace('\\', '/')
    line = _ABSOLUTE.sub(lambda m: '[external]/' + m.group(0).rstrip('/').split('/')[-1], line)
    # Maven prefixes are metadata, not license to export arbitrary log messages.
    text = re.sub(r'^\[ERROR\]\s*', '', line)
    if _LOCATION_ERROR.search(text) or _BUILD_ERROR.search(text) or _MISSING_COMMAND.search(text):
        severity = 'error'
    elif _TEST_FAILURE.search(text):
        severity = 'failure'
    elif _CONTEXT.search(text):
        severity = 'info'
    else:
        return None
    # Opaque credential-shaped strings cannot become arbitrary compiler payloads.
    text = re.sub(r'\b[A-Za-z0-9_-]{48,}\b', '[redacted]', text)
    text = text.encode('utf-8')[:MAX_LINE_BYTES].decode('utf-8', 'ignore')
    return {'severity': severity, 'text': text}


def _read_windows(repo, name):
    """Open each allowlisted path component via no-follow descriptors."""
    flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_DIRECTORY
    descriptors = []
    try:
        root = os.open(repo, flags)
        descriptors.append(root)
        ios = os.open('ios', flags, dir_fd=root)
        descriptors.append(ios)
        build = os.open('build', flags, dir_fd=ios)
        descriptors.append(build)
        file = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=build)
        descriptors.append(file)
        info = os.fstat(file)
        if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
            return {'status': 'unsafe', 'bytesRead': 0, 'truncated': False}, []
        half = MAX_READ_BYTES // 2
        if info.st_size <= MAX_READ_BYTES:
            data = os.read(file, MAX_READ_BYTES)
            return {'status': 'read', 'bytesRead': len(data), 'truncated': False}, [('head', data)]
        head = os.read(file, half)
        os.lseek(file, max(0, info.st_size - half), os.SEEK_SET)
        tail = os.read(file, half)
        bytes_read = len(head) + len(tail)
        # Remove boundary fragments so truncated words cannot hide secret markers.
        head = head.rsplit(b'\n', 1)[0] if b'\n' in head else b''
        tail = tail.split(b'\n', 1)[1] if b'\n' in tail else b''
        return {'status': 'read', 'bytesRead': bytes_read, 'truncated': True}, [('tail', tail), ('head', head)]
    except FileNotFoundError:
        return {'status': 'missing', 'bytesRead': 0, 'truncated': False}, []
    except OSError:
        return {'status': 'unsafe', 'bytesRead': 0, 'truncated': False}, []
    finally:
        for descriptor in reversed(descriptors):
            os.close(descriptor)


def _write_artifact(output, name, data):
    # Reserved trusted output names overwrite a client-supplied file/symlink
    # atomically; never follow it into another file or append raw client content.
    directory = os.open(output, os.O_RDONLY | os.O_NOFOLLOW | os.O_DIRECTORY)
    tmp = '.diagnostic-' + str(uuid.uuid4()) + '.tmp'
    try:
        fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=directory)
        with os.fdopen(fd, 'wb') as file:
            file.write(data)
        os.replace(tmp, name, src_dir_fd=directory, dst_dir_fd=directory)
    finally:
        try:
            os.unlink(tmp, dir_fd=directory)
        except FileNotFoundError:
            pass
        os.close(directory)


def collect_client_diagnostics(repo, output, report):
    summary = {'schemaVersion': 1, 'sourceHead': report['sourceHead'], 'requestId': report['requestId'],
               'mode': 'client', 'errorStage': report['errorStage'], 'errorCode': report['errorCode'],
               'sources': [], 'lines': [], 'truncated': False,
               'limits': {'readBytesPerLog': MAX_READ_BYTES, 'linesPerLog': MAX_LINES_PER_LOG,
                          'lineBytes': MAX_LINE_BYTES, 'artifactBytes': MAX_ARTIFACT_BYTES}}
    for key in ('exitCode', 'timedOut'):
        if key in report:
            summary[key] = report[key]
    for name in LOG_NAMES:
        source, windows = _read_windows(repo, name)
        source['log'] = 'ios/build/' + name
        summary['sources'].append(source)
        summary['truncated'] |= source['truncated']
        found = []
        seen = set()
        for window, data in windows:
            private_block = False
            for number, line in enumerate(data.decode('utf-8', 'replace').splitlines(), 1):
                if '-----BEGIN ' in _ANSI.sub('', line):
                    private_block = True
                if private_block:
                    if '-----END ' in _ANSI.sub('', line):
                        private_block = False
                    continue
                diagnostic = diagnostic_line(line, repo)
                if diagnostic and diagnostic['text'] not in seen:
                    if len(found) >= MAX_LINES_PER_LOG:
                        summary['truncated'] = True
                        continue
                    seen.add(diagnostic['text'])
                    found.append({'log': name, 'window': window, 'lineInWindow': number, **diagnostic})
        summary['lines'].extend(found)
    def encode():
        return (json.dumps(summary, ensure_ascii=False, indent=2) + '\n').encode('utf-8')
    while len(encode()) > MAX_ARTIFACT_BYTES and summary['lines']:
        summary['lines'].pop()
        summary['truncated'] = True
    json_data = encode()
    header = f"Source: {summary['sourceHead']}\nRequest: {summary['requestId']}\nStage: {summary['errorStage']}\nError: {summary['errorCode']}\n"
    if 'exitCode' in summary:
        header += f"Exit code: {summary['exitCode']}\n"
    if summary.get('timedOut'):
        header += 'Command timed out.\n'
    text = header + '\n'.join(f"{line['log']} ({line['window']}:{line['lineInWindow']}) [{line['severity']}] {line['text']}" for line in summary['lines']) + '\n'
    text_data = text.encode('utf-8')[:MAX_ARTIFACT_BYTES]
    _write_artifact(output, 'client-diagnostics.json', json_data)
    _write_artifact(output, 'client-diagnostics.txt', text_data)
    return {'path': 'client-diagnostics.json', 'textPath': 'client-diagnostics.txt',
            'bytes': len(json_data), 'sha256': hashlib.sha256(json_data).hexdigest(),
            'textBytes': len(text_data), 'textSha256': hashlib.sha256(text_data).hexdigest(),
            'lineCount': len(summary['lines']), 'truncated': summary['truncated']}


def stage_artifacts(repo, output, report, artifact_root, *, collect=False):
    """Finalize an allowlisted receipt artifact outside the client checkout."""
    repo, artifact_root = repo.resolve(), artifact_root.resolve()
    if artifact_root.is_relative_to(repo):
        raise ValueError('artifact_root_inside_source')
    stage = Path(tempfile.mkdtemp(prefix='voxel-ios-evidence-', dir=artifact_root))
    try:
        if collect:
            try:
                report['diagnostics'] = collect_client_diagnostics(repo, stage, report)
            except (OSError, ValueError, KeyError):
                # A partial collector output must never become an upload artifact.
                shutil.rmtree(stage)
                stage = Path(tempfile.mkdtemp(prefix='voxel-ios-evidence-', dir=artifact_root))
                report.pop('diagnostics', None)
                report['diagnosticsStatus'] = 'unavailable'
        for receipt in report['evidence']:
            name = receipt['path']
            if Path(name).name != name or not re.fullmatch(r'[A-Za-z0-9_-]+\.(?:png|mp4)', name):
                raise ValueError('unsafe_evidence_name')
            fd = os.open(output / name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            with os.fdopen(fd, 'rb') as file:
                info = os.fstat(file.fileno())
                if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or info.st_size > 6 * 1024 * 1024:
                    raise ValueError('unsafe_evidence_file')
                data = file.read(6 * 1024 * 1024 + 1)
            if len(data) != receipt['bytes'] or hashlib.sha256(data).hexdigest() != receipt['sha256']:
                raise ValueError('evidence_changed')
            _write_artifact(stage, name, data)
        _write_artifact(stage, 'report.json', (json.dumps(report, indent=2) + '\n').encode('utf-8'))
        return stage
    except Exception:
        shutil.rmtree(stage)
        raise
