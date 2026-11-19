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
        diagnostic = next(line for line in self.log.read_text().splitlines() if line.startswith('error: native_test_deadline'))
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
            self.assertIn('error: native_test_failed test_id=' + runner.hashlib.sha256(b'testKnown').hexdigest()[:12], lines)
            self.assertNotIn('private-fixture-marker', '\n'.join(lines))
            with patch.object(runner.subprocess, 'run', side_effect=subprocess.TimeoutExpired('synthetic', 30)):
                runner.record_failure(log, root)
            self.assertTrue(log.read_text().endswith('error: native_test_failure_summary status=summary_unavailable\n'))

    def test_failure_locators_never_emit_assertion_values(self):
        text = 'NativeWorkflowTests.swift:193: XCTAssertTrue failed: private-fixture-marker'
        self.assertEqual(runner.failure_locators({'testFailures':[{'failureText':text}]}),
                         ['error: native_test_failure_category category=assertion source=NativeWorkflowTests.swift line=193'])
        self.assertEqual(runner.failure_locators({'testFailures':[{'failureText':'private-fixture-marker'}]}),
                         ['error: native_test_failure_category category=unknown'])
        self.assertEqual(runner.failure_locators({'testFailures':[{'failureText':'x'*8193}]}), [])

    def test_long_method_hash_survives_existing_sanitizer(self):
        spec = importlib.util.spec_from_file_location('failure_diagnostics', ROOT / 'tools/ios/client_diagnostics.py')
        diagnostics = importlib.util.module_from_spec(spec);spec.loader.exec_module(diagnostics)
        method = 'testNativeRecipeSearchFractionalJoinAndBackground'
        marker = 'error: native_test_failed test_id=' + runner.hashlib.sha256(method.encode()).hexdigest()[:12]
        self.assertEqual(diagnostics.diagnostic_line(marker, ROOT)['text'], marker)

    def test_fixed_craft_assertion_checkpoint_is_retained(self):
        result=runner.failure_locators({'testFailures':[{'failureText':'XCTAssertEqual failed: private-fixture-marker craft_book_empty_results'}]})
        self.assertEqual(result,['error: native_test_failure_category category=assertion checkpoint=craft_book_empty_results'])


class NativeProgressCaptureTests(unittest.TestCase):
    def test_failed_middle_and_open_tests_survive_large_log_and_deadline(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as scratch:
            log=Path(scratch)/'test.log'
            lines=["Test Case '-[VoxelOneUITests.WorldRulesTests testFirst]' started.",
                   "Test Case '-[VoxelOneUITests.WorldRulesTests testFirst]' failed.",
                   "Test Case '-[VoxelOneUITests.WorldRulesTests testOpen]' started.",
                   'private-fixture-marker' * 5000,
                   "Test Case '-[PrivateAccount testSecret]' started."]
            code='import time;print('+repr('\n'.join(lines))+',flush=True);time.sleep(10)'
            self.assertEqual(runner.run([sys.executable,'-c',code],log,1,{'testFirst','testOpen'}),124)
            markers=[line for line in log.read_text().splitlines() if line.startswith('error: native_test_')]
            text='\n'.join(markers)
            self.assertIn('started=2 passed=0 failed=1 unfinished=1 expected=2 phase=test_open',text)
            self.assertIn('native_test_unfinished_event test_id='+runner.hashlib.sha256(b'testOpen').hexdigest()[:12],text)
            self.assertNotIn('private-fixture-marker',text)
            self.assertNotIn('testSecret',text)

    def test_closed_events_do_not_claim_result_finalization_pass(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as scratch:
            log=Path(scratch)/'test.log'
            text="Test Case '-[VoxelOneUITests.WorldRulesTests testDone]' started.\nTest Case '-[VoxelOneUITests.WorldRulesTests testDone]' passed."
            code='print('+repr(text)+');raise SystemExit(65)'
            self.assertEqual(runner.run([sys.executable,'-c',code],log,5,{'testDone'}),65)
            self.assertIn('started=1 passed=1 failed=0 unfinished=0 expected=1 phase=no_test_open',log.read_text())
