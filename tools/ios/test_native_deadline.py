"""Synthetic child-process checks; these do not execute Xcode or the native app."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
import json
import subprocess
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('native_deadline', ROOT / 'ios/native-test-runner.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class NativeDeadlineTests(unittest.TestCase):
    def setUp(self):
        (ROOT / 'target').mkdir(exist_ok=True)
        self.scratch = tempfile.TemporaryDirectory(dir=ROOT / 'target')
        self.addCleanup(self.scratch.cleanup)
        self.log = Path(self.scratch.name) / 'test.log'

    def test_success_and_failure_preserve_child_exit_status(self):
        self.assertEqual(runner.run([sys.executable, '-c', 'print("synthetic success")'], self.log, 5), 0)
        self.assertEqual(runner.run([sys.executable, '-c', 'raise SystemExit(7)'], self.log, 5), 7)

    def test_timeout_has_only_allowlisted_progress(self):
        line = "Test Case '-[VoxelOneUITests.WorldRulesTests testBoundedMeshPagingCancellationAndGeometry]' started."
        code = 'import time;print(' + repr(line) + ',flush=True);print("private-fixture-marker",flush=True);time.sleep(10)'
        self.assertEqual(runner.run([sys.executable, '-c', code], self.log, 1), 124)
        diagnostic = self.log.read_text().splitlines()[-1]
        self.assertIn('last_test=WorldRulesTests.testBoundedMeshPagingCancellationAndGeometry', diagnostic)
        self.assertIn('last_state=started', diagnostic)
        self.assertNotIn('private-fixture-marker', diagnostic)

    def test_progress_reader_is_bounded_and_rejects_arbitrary_names(self):
        self.log.write_text("Test Case '-[VoxelOneUITests.WorldRulesTests testOld]' passed.\n" + 'x' * 70000)
        self.assertEqual(runner.last_test(self.log), ('unknown', 'unknown'))
        self.log.write_text("Test Case '-[PrivateAccount private-fixture-marker]' started.\n")
        self.assertEqual(runner.last_test(self.log), ('unknown', 'unknown'))


class NativeFailureSummaryTests(unittest.TestCase):
    def test_only_known_failure_method_names_leave_summary(self):
        summary = {'testFailures': [
            {'testName': 'NativeWorkflowTests.testKnown()', 'failureText': 'private-fixture-marker'},
            {'testIdentifier': 'WorldRulesTests/testSecond()', 'testName': 'testUnknown'},
            {'testName': 'testKnown()'}, {'testName': 'private-fixture-marker'}]}
        self.assertEqual(runner.failed_methods(summary, {'testKnown', 'testSecond'}), ['testKnown', 'testSecond'])
        self.assertEqual(runner.failed_methods({'testFailures': 'malformed'}, {'testKnown'}), [])
        self.assertEqual(runner.failed_methods(None, {'testKnown'}), [])

    def test_structured_failure_is_appended_as_fixed_diagnostics(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as scratch:
            root = Path(scratch)
            build = root / 'ios/build';build.mkdir(parents=True)
            (build / 'NativeTests.xcresult').mkdir()
            source = root / 'ios/VoxelOneUITests';source.mkdir()
            (source / 'NativeWorkflowTests.swift').write_text('func testKnown() {}')
            log = build / 'native-tests.log';log.write_text('private-fixture-marker\n')
            def capture(command, **kwargs):
                self.assertEqual(kwargs['timeout'], 30)
                kwargs['stdout'].write(json.dumps({'testFailures': [{'testName': 'NativeWorkflowTests.testKnown()', 'failureText': 'private-fixture-marker'}]}).encode())
                return type('Result', (), {'returncode': 0})()
            with patch.object(runner.subprocess, 'run', side_effect=capture):
                runner.record_failure(log, root)
            lines = log.read_text().splitlines()[1:]
            self.assertIn('error: native_test_failed test=testKnown', lines)
            self.assertNotIn('private-fixture-marker', '\n'.join(lines))
            with patch.object(runner.subprocess, 'run', side_effect=subprocess.TimeoutExpired('synthetic', 30)):
                runner.record_failure(log, root)
            self.assertTrue(log.read_text().endswith('error: native_test_failure_summary status=summary_unavailable\n'))
