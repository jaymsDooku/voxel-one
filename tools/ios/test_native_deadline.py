"""Synthetic child-process checks; these do not execute Xcode or the native app."""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest
import json
import subprocess
import time
import io
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('native_deadline', ROOT / 'ios/native-test-runner.py')
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def setUpModule():
    # Every class needs this ignored parent, including classes ordered before
    # NativeDeadlineTests on a fresh checkout with no prior Maven/test output.
    (ROOT / 'target').mkdir(exist_ok=True)


class NativeDeadlineTests(unittest.TestCase):
    def test_cli_bounded_budget_accepts_endpoints_and_rejects_overrun(self):
        base=['native-test-runner.py','--simulator','C6832151-D858-43BF-B90D-05F63089554B']
        for seconds in (1,1500):
            with patch.object(sys,'argv',base+['--timeout',str(seconds),'--','mock-native-command']),patch.object(runner,'run',return_value=0) as child:
                self.assertEqual(runner.main(),0)
                self.assertEqual(child.call_args.args[2],seconds)
                self.assertTrue(child.call_args.kwargs['fail_fast'])
        for seconds in (0,1501):
            with patch.object(sys,'argv',base+['--timeout',str(seconds),'--','mock-native-command']),patch.object(runner,'run') as child,patch.object(sys,'stderr',io.StringIO()):
                with self.assertRaises(SystemExit) as error:runner.main()
                self.assertEqual(error.exception.code,2);child.assert_not_called()
        with patch.object(sys,'argv',base+['--','mock-native-command']),patch.object(runner,'run',return_value=0) as child:
            self.assertEqual(runner.main(),0);self.assertEqual(child.call_args.args[2],1500)

    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory(dir=ROOT / 'target')
        self.addCleanup(self.scratch.cleanup)
        self.log = Path(self.scratch.name) / 'test.log'

    def test_success_and_failure_preserve_child_exit_status(self):
        self.assertEqual(runner.run([sys.executable, '-c', 'print("synthetic success")'], self.log, 5), 0)
        self.assertEqual(runner.run([sys.executable, '-c', 'raise SystemExit(7)'], self.log, 5), 7)

    def recorder(self, signals):
        ready=self.log.parent/('ready-'+str(time.monotonic_ns()))
        code=('import signal,time,sys;from pathlib import Path;'+signals+
              ';Path('+repr(str(ready))+').write_text("ready");time.sleep(10)')
        process=subprocess.Popen([sys.executable,'-c',code],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
        def cleanup():
            if process.poll() is None:process.kill()
            process.wait(timeout=2)
        self.addCleanup(cleanup)
        deadline=time.monotonic()+2
        while not ready.exists() and process.poll() is None and time.monotonic()<deadline:time.sleep(.01)
        self.assertTrue(ready.exists())
        return process

    def test_recorder_graceful_interrupt_is_reaped(self):
        process=self.recorder('signal.signal(signal.SIGINT,lambda *args:sys.exit(0))')
        self.assertTrue(runner.stop_recording(process,grace=1,terminate_grace=.2))
        self.assertEqual(process.poll(),0)

    def test_recorder_ignoring_interrupt_and_term_is_killed_without_signalling_other_child(self):
        other=self.recorder('signal.signal(signal.SIGINT,signal.SIG_IGN)')
        process=self.recorder('signal.signal(signal.SIGINT,signal.SIG_IGN);signal.signal(signal.SIGTERM,signal.SIG_IGN)')
        started=time.monotonic()
        self.assertFalse(runner.stop_recording(process,grace=.1,terminate_grace=.1))
        self.assertLess(time.monotonic()-started,2)
        self.assertIsNotNone(process.poll())
        self.assertIsNone(other.poll(),"Only the owned recorder is signalled")

    def test_failed_recorder_cannot_qualify_successful_native_child(self):
        self.assertEqual(runner.run([sys.executable,'-c','import time;time.sleep(.2)'],self.log,5,
                         recording_command=[sys.executable,'-c','raise SystemExit(3)']),74)
        self.assertIn('error: native_video_finalize status=failed',self.log.read_text())

    def test_fixture_shutdown_uses_owned_shell_job_and_leaves_external_child_running(self):
        source=(ROOT/'ios/check-simulator.sh').read_text()
        helpers='fixture_running() {'+source.split('fixture_running() {',1)[1].split('\ntrap cleanup EXIT',1)[0]
        # Exercise the actual Bash cleanup functions, without starting Java/Xcode.
        command=helpers+'\nsleep 10 &\nfixture_pid=$!\nfixture_running || exit 99\ncleanup\n'
        result=subprocess.run(['bash','-c',command],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=2)
        self.assertEqual(result.returncode,0)
        other=self.recorder('signal.signal(signal.SIGINT,signal.SIG_IGN)')
        command=helpers+'\nfixture_pid="$1"\ncleanup\n'
        result=subprocess.run(['bash','-c',command,'fixture-scope-test',str(other.pid)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=2)
        self.assertEqual(result.returncode,0)
        self.assertIsNone(other.poll(),"A PID outside the shell job table is never signalled")

    def test_live_progress_and_success_counts_survive_later_failure_without_private_payloads(self):
        name='testKnown'
        start="Test Case '-[VoxelOneUITests.NativeWorkflowTests testKnown]' started."
        passed=start.replace('started.','passed.')
        code='import time;print('+repr(start)+',flush=True);print("private-fixture-marker",flush=True);time.sleep(.4);print('+repr(passed)+',flush=True)'
        self.assertEqual(runner.run([sys.executable,'-c',code],self.log,5,{name},progress_interval=.05),0)
        lines=self.log.read_text().splitlines()
        live=[line for line in lines if line.startswith('error: native_test_live')]
        self.assertTrue(live)
        self.assertTrue(any('started=1 passed=0 failed=0 expected=1' in line for line in live))
        self.assertNotIn('private',str(live));self.assertNotIn(name,str(live))
        self.assertTrue(any('native_test_progress started=1 passed=1 failed=0 unfinished=0 expected=1' in line for line in lines))
        spec=importlib.util.spec_from_file_location('live_diagnostics',ROOT/'tools/ios/client_diagnostics.py')
        diagnostics=importlib.util.module_from_spec(spec);spec.loader.exec_module(diagnostics)
        for line in live:self.assertEqual(diagnostics.diagnostic_line(line,ROOT)['text'],line)

    def test_video_window_is_bound_to_passing_offline_case_and_numeric_only(self):
        name='testNativeTouchSettingsDraftBindingsLookAndRestart'
        timing=self.log.parent/'timing.json'
        start="Test Case '-[VoxelOneUITests.NativeWorkflowTests "+name+"]' started."
        passed=start.replace('started.','passed.')
        code='import time;print('+repr(start)+',flush=True);print("native_workflow_checkpoint code=controls_ready",flush=True);print("private-fixture-marker",flush=True);time.sleep(1.2);print('+repr(passed)+',flush=True)'
        self.assertEqual(runner.run([sys.executable,'-c',code],self.log,5,{name},video_timing=timing),0)
        data=json.loads(timing.read_text())
        self.assertEqual(set(data),{'offsetSeconds','durationSeconds'})
        self.assertGreaterEqual(data['offsetSeconds'],0)
        self.assertGreaterEqual(data['durationSeconds'],1)
        self.assertLessEqual(data['durationSeconds'],40)
        self.assertNotIn('private',timing.read_text())
        # An unsuccessful command must remove a previous run's timing.
        self.assertEqual(runner.run([sys.executable,'-c','raise SystemExit(7)'],self.log,5,{name},video_timing=timing),7)
        self.assertFalse(timing.exists())

    def test_video_window_rejects_wrong_test_class_and_unfinished_case(self):
        name='testNativeTouchSettingsDraftBindingsLookAndRestart'
        timing=self.log.parent/'timing.json'
        for kind,close,ready in [('WorldRulesTests',True,True),('NativeWorkflowTests',False,True),('NativeWorkflowTests',True,False)]:
            start="Test Case '-[VoxelOneUITests."+kind+' '+name+"]' started."
            code='import time;print('+repr(start)+',flush=True)'
            if ready:code+=';print("native_workflow_checkpoint code=controls_ready",flush=True)'
            code+=';time.sleep(1.2)'
            if close:code+=';print('+repr(start.replace('started.','passed.'))+',flush=True)'
            self.assertEqual(runner.run([sys.executable,'-c',code],self.log,5,{name},video_timing=timing),0)
            self.assertFalse(timing.exists())

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
                   'error: native_workflow_checkpoint code=model_place',
                   'error: native_workflow_checkpoint code=private_fixture',
                   'private-fixture-marker' * 5000,
                   "Test Case '-[PrivateAccount testSecret]' started."]
            code='import time;print('+repr('\n'.join(lines))+',flush=True);time.sleep(10)'
            self.assertEqual(runner.run([sys.executable,'-c',code],log,1,{'testFirst','testOpen'}),124)
            lines=log.read_text().splitlines()
            first=next(i for i,line in enumerate(lines) if line.startswith('error: native_test_deadline'))
            markers=lines[first:]
            text='\n'.join(markers)
            self.assertIn('started=2 passed=0 failed=1 unfinished=1 expected=2 phase=test_open',text)
            self.assertIn('native_test_unfinished_event test_id='+runner.hashlib.sha256(b'testOpen').hexdigest()[:12],text)
            self.assertNotIn('private-fixture-marker',text)
            self.assertNotIn('testSecret',text)
            self.assertIn('error: native_workflow_checkpoint code=model_place',text)
            self.assertNotIn('private_fixture',text)

    def test_closed_events_do_not_claim_result_finalization_pass(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as scratch:
            log=Path(scratch)/'test.log'
            text="Test Case '-[VoxelOneUITests.WorldRulesTests testDone]' started.\nTest Case '-[VoxelOneUITests.WorldRulesTests testDone]' passed."
            code='print('+repr(text)+');raise SystemExit(65)'
            self.assertEqual(runner.run([sys.executable,'-c',code],log,5,{'testDone'}),65)
            self.assertIn('started=1 passed=1 failed=0 unfinished=0 expected=1 phase=no_test_open',log.read_text())


class NativeTimingCaptureTests(unittest.TestCase):
    def test_timings_and_last_checkpoint_are_bounded_and_allowlisted(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as scratch:
            log=Path(scratch)/'test.log'
            messages=[
                "Test Case '-[VoxelOneUITests.WorldRulesTests testClosed]' started.",
                "Test Case '-[VoxelOneUITests.WorldRulesTests testClosed]' passed.",
                "Test Case '-[VoxelOneUITests.NativeWorkflowTests testOpen]' started.",
                'error: native_workflow_checkpoint code=sky_create',
                'error: native_workflow_checkpoint code=sky_sun_first',
                'error: native_workflow_checkpoint code=private_fixture',
                "Test Case '-[VoxelOneUITests.NativeWorkflowTests testPrivate]' started.",
                'private-fixture-marker' * 5000]
            log.write_text('\n'.join(messages)+'\n')
            observer=runner.TestProgress(log,{'testClosed','testOpen'})
            observer.stop.set()
            with patch.object(runner.time,'monotonic',side_effect=[10,14,20,25]):
                observer.observe()
                # finish() joins a thread only when the asynchronous observer was started.
                with patch.object(observer.thread,'join'),patch.object(observer.thread,'is_alive',return_value=False):
                    markers=observer.finish()
            closed=runner.hashlib.sha256(b'testClosed').hexdigest()[:12]
            opened=runner.hashlib.sha256(b'testOpen').hexdigest()[:12]
            self.assertIn('error: native_test_timing test_id='+closed+' elapsed_seconds=4 state=closed',markers)
            self.assertIn('error: native_test_timing test_id='+opened+' elapsed_seconds=5 state=open',markers)
            self.assertEqual([line for line in markers if 'native_workflow_checkpoint' in line],
                             ['error: native_workflow_checkpoint code=sky_sun_first'])
            self.assertNotIn('private', '\n'.join(markers))
            self.assertLessEqual(len(markers),8)

    def test_only_two_slowest_timings_are_exported_without_source_text(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as scratch:
            observer=runner.TestProgress(Path(scratch)/'unused',{'testOne','testTwo','testThree'})
            observer.started={'testOne','testTwo','testThree'}
            observer.passed=set(observer.started)
            observer.states={name:'passed' for name in observer.started}
            observer.durations={'testOne':50,'testTwo':20,'testThree':30}
            with patch.object(observer.thread,'join'),patch.object(observer.thread,'is_alive',return_value=False):
                timings=[line for line in observer.finish() if 'native_test_timing' in line]
            self.assertEqual(len(timings),2)
            self.assertIn('elapsed_seconds=50',timings[0]);self.assertIn('elapsed_seconds=30',timings[1])
            self.assertNotIn('testOne','\n'.join(timings))


class NativeDeadlineDiagnosticIntegrationTests(unittest.TestCase):
    def test_routine_steps_do_not_displace_deadline_and_timing_diagnostics(self):
        diagnostics_spec=importlib.util.spec_from_file_location('deadline_diagnostics',ROOT/'tools/ios/client_diagnostics.py')
        diagnostics=importlib.util.module_from_spec(diagnostics_spec);diagnostics_spec.loader.exec_module(diagnostics)
        with tempfile.TemporaryDirectory(dir=ROOT/'target') as scratch:
            repo=Path(scratch);build=repo/'ios/build';build.mkdir(parents=True)
            output=repo/'evidence';output.mkdir()
            source=repo/'ios/VoxelOneUITests';source.mkdir()
            (source/'NativeWorkflowTests.swift').write_text('func testClosed() {}\nfunc testOpen() {}')
            log=build/'native-tests.log'
            messages=["Test Case '-[VoxelOneUITests.NativeWorkflowTests testClosed]' started.",
                      "Test Case '-[VoxelOneUITests.NativeWorkflowTests testClosed]' passed."]
            messages += ['native_workflow_checkpoint code='+code for code in sorted(runner._CHECKPOINT_CODES)]
            messages += ["Test Case '-[VoxelOneUITests.NativeWorkflowTests testOpen]' started.",
                         'native_workflow_checkpoint code=sky_create']
            code='import time;print('+repr('\n'.join(messages))+',flush=True);print("x"*140000,flush=True);time.sleep(10)'
            self.assertEqual(runner.run([sys.executable,'-c',code],log,1,{'testClosed','testOpen'}),124)
            runner.record_failure(log,repo)
            diagnostics.collect_client_diagnostics(repo,output,{'sourceHead':'a'*40,'requestId':'synthetic-request',
                'errorStage':'client_script','errorCode':'command_failed','exitCode':124})
            result=json.loads((output/'client-diagnostics.json').read_text())
            exported=[line['text'] for line in result['lines']]
            self.assertTrue(any('native_test_deadline' in line for line in exported))
            self.assertTrue(any('native_test_progress started=2 passed=1 failed=0 unfinished=1 expected=2' in line for line in exported))
            self.assertEqual(sum('native_test_timing' in line for line in exported),2)
            self.assertIn('error: native_workflow_checkpoint code=sky_create',exported)
            self.assertIn('error: native_test_failure_summary status=unavailable',exported)
            self.assertLessEqual(len(exported),8)


class NativeFirstFailureTests(unittest.TestCase):
    def test_first_failed_case_stops_owned_child_with_source_location_only(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'target') as scratch:
            log=Path(scratch)/'test.log'
            messages=["Test Case '-[VoxelOneUITests.NativeWorkflowTests testBatch]' started.",
                      'native_workflow_checkpoint code=batch_craft',
                      'native_workflow_issue source=NativeWorkflowTests.swift line=271 code=assertion',
                      'native_workflow_issue source=PrivateAccount.swift line=7 code=assertion',
                      'private-fixture-marker',
                      "Test Case '-[VoxelOneUITests.NativeWorkflowTests testBatch]' failed."]
            code='import time;print('+repr('\n'.join(messages))+',flush=True);time.sleep(10)'
            self.assertEqual(runner.run([sys.executable,'-c',code],log,5,{'testBatch'},fail_fast=True),65)
            markers=log.read_text().split('error: native_test_stopped',1)[1]
            self.assertIn('reason=first_failed_case',markers)
            self.assertIn('source=NativeWorkflowTests.swift line=271 code=assertion checkpoint=batch_craft',markers)
            self.assertIn('started=1 passed=0 failed=1 unfinished=0 expected=1',markers)
            self.assertNotIn('private',markers);self.assertNotIn('PrivateAccount',markers)
            self.assertNotIn('native_test_deadline',markers)
            spec=importlib.util.spec_from_file_location('first_failure_diagnostics',ROOT/'tools/ios/client_diagnostics.py')
            diagnostics=importlib.util.module_from_spec(spec);spec.loader.exec_module(diagnostics)
            failed=next(line for line in log.read_text().splitlines() if line.startswith('error: native_test_failed_event'))
            self.assertEqual(diagnostics.diagnostic_line(failed,ROOT)['text'],failed)

    def test_issue_is_bound_to_open_declared_case_and_line_bounds(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'target') as scratch:
            log=Path(scratch)/'test.log'
            log.write_text('\n'.join([
                'native_workflow_issue source=NativeWorkflowTests.swift line=4 code=assertion',
                "Test Case '-[VoxelOneUITests.NativeWorkflowTests testKnown]' started.",
                'native_workflow_issue source=NativeWorkflowTests.swift line=0 code=assertion',
                'native_workflow_issue source=NativeWorkflowTests.swift line=100000 code=assertion',
                'native_workflow_issue source=NativeWorkflowTests.swift line=8 code=private_fixture',
                'native_workflow_issue source=NativeWorkflowTests.swift line=42 code=execution',
                "Test Case '-[VoxelOneUITests.NativeWorkflowTests testKnown]' failed.",
                'native_workflow_issue source=NativeWorkflowTests.swift line=99 code=assertion'])+'\n')
            observer=runner.TestProgress(log,{'testKnown'});observer.stop.set();observer.observe()
            self.assertEqual(observer.issues,{'testKnown':('NativeWorkflowTests.swift',42,'execution')})

    def test_fail_fast_does_not_change_success_or_unrelated_child_exit(self):
        with tempfile.TemporaryDirectory(dir=ROOT/'target') as scratch:
            log=Path(scratch)/'test.log'
            lines="Test Case '-[VoxelOneUITests.NativeWorkflowTests testKnown]' started.\nTest Case '-[VoxelOneUITests.NativeWorkflowTests testKnown]' passed."
            self.assertEqual(runner.run([sys.executable,'-c','print('+repr(lines)+')'],log,5,{'testKnown'},fail_fast=True),0)
            self.assertEqual(runner.run([sys.executable,'-c','raise SystemExit(7)'],log,5,{'testKnown'},fail_fast=True),7)
