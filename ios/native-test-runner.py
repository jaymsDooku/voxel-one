#!/usr/bin/env python3
"""Bound the owned xcodebuild child and retain only fixed XCTest progress on timeout."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import threading
import time

TAIL_BYTES = 65536
_CHECKPOINT_CODES = {"model_terrain", "camera_terrain", "model_place", "model_half_place", "model_collect", "graphics_open", "graphics_draft", "graphics_saved", "graphics_landscape", "graphics_reset_cancel", "graphics_high", "tools_sticks", "tools_craft", "tools_place", "tools_restart", "terrain_seed", "sky_create", "sky_horizon", "sky_sun_first", "sky_restart", "sky_sun_restart", "batch_open", "batch_query", "batch_quantity", "batch_craft", "batch_restart"}
_CHECKPOINT = re.compile(r"(?:error: )?native_workflow_checkpoint code=([a-z_]{1,32})$")
_ISSUE = re.compile(r"native_workflow_issue source=((?:NativeWorkflowTests|WorldRulesTests|GraphicsRulesTests)\.swift) line=([0-9]{1,5}) code=(assertion|execution)$")
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


class TestProgress:
    """Observe exact source identifiers with bounded reads and line storage."""
    def __init__(self, log, allowed):
        self.log = log
        self.allowed = set(allowed)
        self.started, self.passed, self.failed = set(), set(), set()
        self.states = {}
        self.checkpoints = set()
        self.last_checkpoint = None
        self.opened = {}
        self.durations = {}
        self.active = None
        self.issues = {}
        self.failure_steps = {}
        self.stop = threading.Event()
        self.lock = threading.Lock()
        self.thread = threading.Thread(target=self.observe, daemon=True)

    def observe(self):
        pending = b''
        discard = False
        with self.log.open('rb') as stream:
            while True:
                chunk = stream.read(4096)
                if not chunk:
                    if self.stop.is_set():
                        return
                    self.stop.wait(.1)
                    continue
                for fragment in chunk.splitlines(keepends=True):
                    if not discard and len(pending) + len(fragment) <= 1024:
                        pending += fragment
                    else:
                        pending = b''
                        discard = True
                    if fragment.endswith(b'\n'):
                        if not discard:
                            line=pending.decode('utf-8', errors='replace').rstrip('\r\n')
                            checkpoint=_CHECKPOINT.search(line)
                            if checkpoint and checkpoint[1] in _CHECKPOINT_CODES:
                                with self.lock:
                                    self.checkpoints.add(checkpoint[1])
                                    self.last_checkpoint = checkpoint[1]
                            issue=_ISSUE.search(line)
                            if issue and 1<=int(issue[2])<=99999:
                                with self.lock:
                                    if self.active in self.allowed:
                                        self.issues.setdefault(self.active,(issue[1],int(issue[2]),issue[3]))
                            match = _TEST.match(line)
                            if match and match[2] in self.allowed:
                                name, state = match[2], match[3]
                                with self.lock:
                                    self.states[name] = state
                                    now = time.monotonic()
                                    if state == 'started':
                                        self.last_checkpoint = None
                                        self.active = name
                                        self.opened[name] = now
                                    elif name in self.opened:
                                        self.durations[name] = min(900, max(0, now - self.opened.pop(name)))
                                    if state == 'failed' and self.last_checkpoint is not None:
                                        self.failure_steps[name]=self.last_checkpoint
                                    if state != 'started':
                                        self.active = None
                                    {'started': self.started, 'passed': self.passed, 'failed': self.failed}[state].add(name)
                        pending, discard = b'', False

    def finish(self):
        self.stop.set()
        self.thread.join(timeout=5)
        with self.lock:
            unfinished = sorted(name for name, state in self.states.items() if state == 'started')
            phase = 'test_open' if unfinished else 'no_test_open'
            if not self.started:
                phase = 'no_test_events'
            if self.thread.is_alive():
                phase = 'observer_incomplete'
            lines = ['error: native_test_progress started=' + str(len(self.started)) +
                     ' passed=' + str(len(self.passed)) + ' failed=' + str(len(self.failed)) +
                     ' unfinished=' + str(len(unfinished)) + ' expected=' + str(len(self.allowed)) +
                     ' phase=' + phase]
            for kind, names in [('unfinished', unfinished), ('failed', sorted(self.failed))]:
                for name in names[:3]:
                    suffix=''
                    if kind=='failed':
                        if name in self.issues:
                            source,line,code=self.issues[name]
                            suffix+=' source='+source+' line='+str(line)+' code='+code
                        if name in self.failure_steps:
                            suffix+=' checkpoint='+self.failure_steps[name]
                    lines.append('error: native_test_' + kind + '_event test_id=' + hashlib.sha256(name.encode()).hexdigest()[:12]+suffix)
            # At most two closed-test timings and one open-test age. These are
            # observer elapsed seconds, not frame-time/device performance claims.
            for name, seconds in sorted(self.durations.items(), key=lambda row: (-row[1], row[0]))[:2]:
                lines.append('error: native_test_timing test_id=' + hashlib.sha256(name.encode()).hexdigest()[:12] +
                             ' elapsed_seconds=' + str(int(seconds)) + ' state=closed')
            if unfinished:
                name=unfinished[0]
                now=time.monotonic()
                seconds=min(900,max(0,now-self.opened.get(name,now)))
                lines.append('error: native_test_timing test_id=' + hashlib.sha256(name.encode()).hexdigest()[:12] +
                             ' elapsed_seconds=' + str(int(seconds)) + ' state=open')
            if self.last_checkpoint is not None:
                lines.append('error: native_workflow_checkpoint code=' + self.last_checkpoint)
            return lines


def run(command, log, timeout, allowed=None, fail_fast=False):
    log.parent.mkdir(parents=True, exist_ok=True)
    if allowed is None:
        root = Path(__file__).resolve().parents[1]
        allowed = set()
        for source in (root / 'ios/VoxelOneUITests').glob('*.swift'):
            allowed.update(re.findall(r'func (test[A-Za-z0-9_]{1,96})\(', source.read_text()))
    # The child owns its new process group. Never signal the caller or another role.
    timed_out = False
    with log.open('wb') as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        progress = TestProgress(log, allowed)
        progress.thread.start()
        failed_early=False
        try:
            deadline=time.monotonic()+timeout
            while True:
                with progress.lock:
                    failed_early=fail_fast and bool(progress.failed)
                if failed_early:
                    # A failed case cannot qualify this source. Keep its bounded
                    # location before teardown instead of spending the full budget.
                    markers=progress.finish()
                    break
                remaining=deadline-time.monotonic()
                if remaining<=0:raise subprocess.TimeoutExpired(command,timeout)
                try:
                    result=process.wait(timeout=min(.2,remaining));break
                except subprocess.TimeoutExpired:
                    continue
        except subprocess.TimeoutExpired:
            timed_out = True
            # Preserve event counts before terminating the owned process group.
            markers = progress.finish()
        finally:
            if not timed_out and not failed_early:
                markers = progress.finish()
        if timed_out or failed_early:
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
            result = 124 if timed_out else 65
    if result != 0:
        with log.open('a') as output:
            if timed_out:
                name, state = last_test(log)
                output.write('\nerror: native_test_deadline last_test=' + name +
                             ' last_test_id=' + hashlib.sha256(name.encode()).hexdigest()[:12] +
                             ' last_state=' + state + ' timeout_seconds=' + str(timeout) + '\n')
            if failed_early:
                output.write('\nerror: native_test_stopped reason=first_failed_case\n')
            for marker in markers:
                output.write('\n' + marker + '\n')
    return result


SUMMARY_BYTES = 1024 * 1024


def failed_methods(summary, allowed):
    """Emit only source-declared test identifiers, never XCTest assertion payloads."""
    failures = summary.get('testFailures', []) if isinstance(summary, dict) else []
    if not isinstance(failures, list):
        return []
    found = set()
    for failure in failures[:128]:
        if not isinstance(failure, dict):
            continue
        for field in ('testName', 'testIdentifier', 'testIdentifierString'):
            value = failure.get(field)
            if isinstance(value, str) and len(value) <= 512:
                for name in re.findall(r'(?<![A-Za-z0-9_])test[A-Za-z0-9_]{1,96}(?![A-Za-z0-9_])', value):
                    if name in allowed:
                        found.add(name)
    return sorted(found)[:8]


def failure_locators(summary):
    """Fixed categories and source locations only; assertion values never leave JSON."""
    failures = summary.get('testFailures', []) if isinstance(summary, dict) else []
    if not isinstance(failures, list):
        return []
    result = []
    for failure in failures[:8]:
        if not isinstance(failure, dict):
            continue
        text = failure.get('failureText', '')
        if not isinstance(text, str) or len(text) > 8192:
            continue
        lower = text.lower()
        category = 'unknown'
        for needle, label in [('failed to tap', 'tap'), ('no matches', 'no_match'),
                              ('xctassert', 'assertion'), ('crash', 'app_exit'),
                              ('failed to get matching', 'snapshot')]:
            if needle in lower:
                category = label
                break
        location = re.search(r'(NativeWorkflowTests|WorldRulesTests|GraphicsRulesTests)\.swift:(\d{1,5})(?!\d)', text)
        suffix = ''
        for marker in ('craft_book_query_entered', 'craft_book_filtered_count', 'craft_book_empty_rows', 'craft_book_empty_message', 'craft_book_empty_results', 'craft_join_saved_count', 'craft_join_half_consumed', 'Unusable landscape Craft Done'):
            if marker in text:
                suffix = ' checkpoint=' + marker.lower().replace(' ', '_')
                break
        if location:
            suffix += ' source=' + location[1] + '.swift line=' + location[2]
        result.append('error: native_test_failure_category category=' + category + suffix)
    return result


def record_failure(log, root):
    """Recover bounded structured failure names before the hosted worker is deleted."""
    state = 'unavailable'
    names = []
    locators = []
    result = root / 'ios/build/NativeTests.xcresult'
    destination = root / 'ios/build/failure-summary.json'
    try:
        allowed = set()
        for source in (root / 'ios/VoxelOneUITests').glob('*.swift'):
            allowed.update(re.findall(r'func (test[A-Za-z0-9_]{1,96})\(', source.read_text()))
        if result.exists():
            with destination.open('wb') as output:
                completed = subprocess.run(
                    ['xcrun', 'xcresulttool', 'get', 'test-results', 'summary', '--path', str(result)],
                    stdout=output, stderr=subprocess.DEVNULL, timeout=30, check=False)
            if completed.returncode == 0 and destination.stat().st_size <= SUMMARY_BYTES:
                with destination.open('rb') as stream:
                    summary = json.loads(stream.read(SUMMARY_BYTES))
                names = failed_methods(summary, allowed)
                locators = failure_locators(summary)
                state = 'identified' if names else 'no_allowlisted_failure'
            else:
                state = 'summary_failed_or_oversized'
    except (OSError, ValueError, subprocess.TimeoutExpired):
        state = 'summary_unavailable'
    with log.open('a') as output:
        output.write('\nerror: native_test_failure_summary status=' + state + '\n')
        for name in names:
            output.write('error: native_test_failed test_id=' + hashlib.sha256(name.encode()).hexdigest()[:12] + '\n')
        for locator in locators:
            output.write(locator + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not 1 <= args.timeout <= 900 or not command:
        parser.error('timeout must be 1..900 seconds and a child command is required')
    root = Path(__file__).resolve().parents[1]
    log = root / 'ios/build/native-tests.log'
    result = run(command, log, args.timeout, fail_fast=True)
    if result != 0:
        record_failure(log, root)
    return result


if __name__ == '__main__':
    raise SystemExit(main())
