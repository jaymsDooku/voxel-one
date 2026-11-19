#!/usr/bin/env python3
"""Bound the owned xcodebuild child and retain only fixed XCTest progress on timeout."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import signal
import subprocess

TAIL_BYTES = 65536
_TEST = re.compile(
    r"^Test Case '-\[VoxelOneUITests\.(NativeWorkflowTests|WorldRulesTests|GraphicsRulesTests) "
    r"(test[A-Za-z0-9_]{1,96})\]' (started|passed|failed)(?:[ .(]|$)"
)


def last_test(log):
    """Read a bounded tail; never return arbitrary test output, paths or account text."""
    with log.open('rb') as stream:
        stream.seek(0, os.SEEK_END)
        stream.seek(max(0, stream.tell() - TAIL_BYTES))
        tail = stream.read(TAIL_BYTES).decode('utf-8', errors='replace')
    found = [_TEST.match(line) for line in tail.splitlines()]
    found = [match for match in found if match]
    if not found:
        return 'unknown', 'unknown'
    match = found[-1]
    return match[1] + '.' + match[2], match[3]


def run(command, log, timeout):
    log.parent.mkdir(parents=True, exist_ok=True)
    # The child owns its new process group. Never signal the caller or another role.
    with log.open('wb') as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        try:
            return process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(process.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                process.wait(timeout=5)
    name, state = last_test(log)
    with log.open('a') as output:
        output.write('\nerror: native_test_deadline last_test=' + name +
                     ' last_test_id=' + hashlib.sha256(name.encode()).hexdigest()[:12] +
                     ' last_state=' + state + ' timeout_seconds=' + str(timeout) + '\n')
    return 124


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not 1 <= args.timeout <= 900 or not command:
        parser.error('timeout must be 1..900 seconds and a child command is required')
    root = Path(__file__).resolve().parents[1]
    return run(command, root / 'ios/build/native-tests.log', args.timeout)


if __name__ == '__main__':
    raise SystemExit(main())
