"""Synthetic log fixtures: test bounded diagnostics without private data."""
import hashlib
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

import client_diagnostics as diagnostics
import simulator_checks as checks
from test_simulator_checks import FakeAppleCommands, HEAD, REQUEST


class DiagnosticSecurityTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='ios-safe-diagnostics-')
        self.repo = Path(self.temp.name) / 'source'
        self.logs = self.repo / 'ios/build'
        self.logs.mkdir(parents=True)
        self.output = self.repo / 'ios-evidence'
        self.output.mkdir()
        self.report = {'sourceHead': HEAD, 'requestId': REQUEST, 'errorStage': 'client_script',
                       'errorCode': 'command_failed', 'exitCode': 1}

    def tearDown(self):
        self.temp.cleanup()

    def collect(self):
        result = diagnostics.collect_client_diagnostics(self.repo, self.output, self.report)
        body = json.loads((self.output / result['path']).read_text())
        text = (self.output / result['textPath']).read_text()
        encoded = (self.output / result['path']).read_bytes()
        self.assertEqual(result['bytes'], len(encoded))
        self.assertEqual(result['sha256'], hashlib.sha256(encoded).hexdigest())
        return result, body, text

    def test_actual_compiler_shapes_and_missing_commands_survive_without_raw_output(self):
        (self.logs / 'java-build.log').write_text(
            '[INFO] raw build setup not exported\n'
            f'[ERROR] {self.repo}/ios/java/MobileBridge.java:[23,7] cannot find symbol\n'
            '[ERROR] symbol: class MobileTouchInput\n'
            '[ERROR] location: class dev.jayms.platform.MobileBridge\n'
            '[ERROR] BUILD FAILURE\n')
        (self.logs / 'native-build.log').write_text(
            f'{self.repo}/ios/native/ViewController.swift:42:3: error: cannot convert value of type Double to Float\n'
            '/Applications/Xcode.app/Contents/Developer/SDKs/iPhoneSimulator.sdk/usr/include/jni.h:5:2: fatal error: module not found\n')
        (self.logs / 'native-tests.log').write_text('** TEST FAILED **\nTest Case VoxelUITests.testTouchMovement failed\n')
        result, body, text = self.collect()
        self.assertEqual(body['sourceHead'], HEAD)
        self.assertEqual(body['requestId'], REQUEST)
        self.assertIn('ios/java/MobileBridge.java:[23,7] cannot find symbol', text)
        self.assertIn('ios/native/ViewController.swift:42:3: error:', text)
        self.assertIn('symbol: class MobileTouchInput', text)
        self.assertIn('TEST FAILED', text)
        self.assertNotIn(str(self.repo), text)
        self.assertNotIn('/Applications/', text)
        self.assertNotIn('raw build setup', text)
        self.assertGreater(result['lineCount'], 4)
        self.assertTrue(all(line['log'] in diagnostics.LOG_NAMES and line['severity'] in ('error', 'failure', 'info') for line in body['lines']))
        self.assertEqual(diagnostics.diagnostic_line('ios/check-simulator.sh: line 22: mvn: command not found', self.repo)['severity'], 'error')

    def test_secret_markers_credentials_private_blocks_and_environment_are_not_exported(self):
        markers = ['secret_payload_001', 'owner_payload_002', 'key_payload_003', 'pem_payload_004', 'cookie_payload_005', 'control_payload_006']
        (self.logs / 'native-build.log').write_text(
            f'error: PASSWORD={markers[0]}\nerror: owner_answer={markers[1]}\n'
            f'error: Authorization: Bearer {markers[2]}\n'
            f'error: -----BEGIN PRIVATE KEY-----\nerror: {markers[3]}\n-----END PRIVATE KEY-----\n'
            f'error: Cookie=session={markers[4]}\nerror: pass\x1b[31mword={markers[5]}\n'
            'error: API_KEY=sk-testkeyfixture1234567890\n'
            'error: github_pat_fixturexxxxxxxxxxxxxxxx\n'
            'error: eyJfixture.eyJfixture.fixtureSignature\n'
            '[ERROR] environment_dump: private text\n'
            'error: public compiler declaration missing\n')
        _, body, text = self.collect()
        for marker in markers + ['sk-testkeyfixture', 'github_pat_', 'eyJfixture', 'private text']:
            self.assertNotIn(marker, text)
            self.assertNotIn(marker, json.dumps(body))
        self.assertIn('public compiler declaration missing', text)

    def test_checkout_symlink_alias_and_canonical_prefixes_preserve_relative_paths(self):
        alias = Path(self.temp.name) / 'checkout-alias'
        alias.symlink_to(self.repo, target_is_directory=True)
        for root in (alias, self.repo.resolve()):
            line = diagnostics.diagnostic_line(f'{root}/ios/native/Client.swift:12:3: error: unknown type', alias)
            self.assertEqual(line['text'], 'ios/native/Client.swift:12:3: error: unknown type')
        for outside in (str(alias) + '-sibling/Owner.swift', '/outside' + str(alias) + '/Owner.swift'):
            line = diagnostics.diagnostic_line(f'{outside}:4:2: error: unknown type', alias)
            self.assertEqual(line['text'], '[external]/Owner.swift:4:2: error: unknown type')
            self.assertNotIn(str(alias), line['text'])

    def test_url_credentials_queries_and_external_paths_are_removed(self):
        (self.logs / 'java-build.log').write_text(
            '[ERROR] Could not resolve artifact fixture:library:1 from https://user:opaqueValue@host.test/repo?access=opaqueQuery\n'
            '/Users/fixture-person/private-build/Native.swift:7:2: error: missing type\n')
        _, _, text = self.collect()
        self.assertNotIn('opaqueValue', text)
        self.assertNotIn('opaqueQuery', text)
        self.assertNotIn('fixture-person', text)
        self.assertNotIn('host.test', text)
        self.assertIn('Could not resolve artifact fixture:library:1', text)
        self.assertIn('[external]/Native.swift:7:2: error: missing type', text)

    def test_fixture_runtime_profile_auth_and_unknown_logs_are_not_read(self):
        for name in ('fixture.log', 'runtime.log', 'account.log', 'profile.log', 'auth.log', 'ready.log', 'other-build.log'):
            (self.logs / name).write_text('error: excluded_private_marker\n')
        _, body, text = self.collect()
        self.assertEqual(body['lines'], [])
        self.assertEqual([s['log'] for s in body['sources']], ['ios/build/' + n for n in diagnostics.LOG_NAMES])
        self.assertNotIn('excluded_private_marker', text)

    def test_oversized_logs_read_only_bounded_head_tail_and_keep_terminal_error(self):
        file = self.logs / 'native-build.log'
        file.write_bytes(b'normal build output\n' * 30_000 + b'error: terminal native compilation failure\n')
        _, body, text = self.collect()
        self.assertIn('terminal native compilation failure', text)
        self.assertTrue(body['truncated'])
        self.assertLessEqual(body['sources'][1]['bytesRead'], diagnostics.MAX_READ_BYTES)
        self.assertLessEqual((self.output / 'client-diagnostics.json').stat().st_size, diagnostics.MAX_ARTIFACT_BYTES)
        self.assertLessEqual((self.output / 'client-diagnostics.txt').stat().st_size, diagnostics.MAX_ARTIFACT_BYTES)

    def test_line_count_and_bytes_remain_bounded_for_many_or_huge_errors(self):
        for name in diagnostics.LOG_NAMES:
            (self.logs / name).write_text(''.join(f'error: compilation failure {index} ' + 'value ' * 150 + '\n' for index in range(100)))
        result, body, _ = self.collect()
        self.assertLessEqual(result['lineCount'], 3 * diagnostics.MAX_LINES_PER_LOG)
        self.assertTrue(body['truncated'])
        self.assertTrue(all(len(line['text'].encode('utf-8')) <= diagnostics.MAX_LINE_BYTES for line in body['lines']))

    def test_file_directory_symlinks_and_hardlinks_cannot_export_outside_data(self):
        outside = Path(self.temp.name) / 'outside'
        outside.mkdir()
        confidential = outside / 'hidden.log'
        confidential.write_text('error: outside_private_marker\n')
        (self.logs / 'java-build.log').symlink_to(confidential)
        os.link(confidential, self.logs / 'native-build.log')
        _, body, text = self.collect()
        self.assertNotIn('outside_private_marker', text)
        self.assertEqual(body['sources'][0]['status'], 'unsafe')
        self.assertEqual(body['sources'][1]['status'], 'unsafe')
        for file in self.logs.iterdir():
            file.unlink()
        self.logs.rmdir()
        self.logs.symlink_to(outside, target_is_directory=True)
        (outside / 'native-tests.log').write_text('error: outside_private_marker\n')
        _, body, text = self.collect()
        self.assertEqual(body['lines'], [])
        self.assertNotIn('outside_private_marker', text)

    def test_reserved_output_symlinks_are_replaced_without_touching_target(self):
        outside = Path(self.temp.name) / 'preserved.txt'
        outside.write_text('preserve outside content')
        (self.output / 'client-diagnostics.json').symlink_to(outside)
        self.collect()
        self.assertEqual(outside.read_text(), 'preserve outside content')
        self.assertFalse((self.output / 'client-diagnostics.json').is_symlink())

    def test_output_directory_symlink_is_rejected_without_writing_outside(self):
        self.output.rmdir()
        outside = Path(self.temp.name) / 'outside-evidence'
        outside.mkdir()
        self.output.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(OSError):
            self.collect()
        self.assertEqual(list(outside.iterdir()), [])

    def test_success_and_preflight_stage_no_source_diagnostics_or_wildcard_files(self):
        (self.output / 'client-diagnostics.json').mkdir()
        (self.output / 'client-diagnostics.txt').write_text('raw_private_marker')
        (self.output / 'unreceipted.mp4').write_text('raw_private_marker')
        for mode in ('client', 'preflight'):
            report = {**self.report, 'mode': mode, 'status': 'passed', 'evidence': []}
            stage = diagnostics.stage_artifacts(self.repo, self.output, report, Path(self.temp.name))
            self.assertFalse(stage.is_relative_to(self.repo))
            self.assertEqual([p.name for p in stage.iterdir()], ['report.json'])
            self.assertNotIn('raw_private_marker', (stage / 'report.json').read_text())

    def test_failed_client_source_reserved_directories_and_symlinks_never_upload_raw_data(self):
        (self.output / 'client-diagnostics.json').mkdir()
        outside = Path(self.temp.name) / 'private.txt'
        outside.write_text('raw_private_marker')
        (self.output / 'client-diagnostics.txt').symlink_to(outside)
        (self.logs / 'native-build.log').write_text('error: actual compilation failure\n')
        report = {**self.report, 'status': 'failed', 'evidence': []}
        stage = diagnostics.stage_artifacts(self.repo, self.output, report, Path(self.temp.name), collect=True)
        self.assertEqual(set(p.name for p in stage.iterdir()), {'report.json', 'client-diagnostics.json', 'client-diagnostics.txt'})
        self.assertIn('actual compilation failure', (stage / 'client-diagnostics.txt').read_text())
        self.assertNotIn('raw_private_marker', ''.join(p.read_text() for p in stage.iterdir()))
        self.assertEqual(outside.read_text(), 'raw_private_marker')

    def test_collector_failure_discards_partial_stage_and_uploads_only_report(self):
        def broken_collect(repo, output, report):
            (output / 'client-diagnostics.txt').write_text('partial_private_marker')
            raise OSError('synthetic collector failure')
        report = {**self.report, 'status': 'failed', 'evidence': []}
        with mock.patch.object(diagnostics, 'collect_client_diagnostics', side_effect=broken_collect):
            stage = diagnostics.stage_artifacts(self.repo, self.output, report, Path(self.temp.name), collect=True)
        self.assertEqual([p.name for p in stage.iterdir()], ['report.json'])
        self.assertNotIn('diagnostics', report)
        self.assertEqual(report['diagnosticsStatus'], 'unavailable')
        self.assertEqual(len(list(Path(self.temp.name).glob('voxel-ios-evidence-*'))), 1)

    def test_only_receipted_unchanged_media_is_staged(self):
        data = b'validated fixture image bytes'
        (self.output / 'client.png').write_bytes(data)
        (self.output / 'unreceipted.mp4').write_bytes(b'not validated')
        report = {**self.report, 'evidence': [{'path': 'client.png', 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}]}
        stage = diagnostics.stage_artifacts(self.repo, self.output, report, Path(self.temp.name))
        self.assertEqual(set(p.name for p in stage.iterdir()), {'client.png', 'report.json'})
        (self.output / 'client.png').write_bytes(b'changed after validation')
        with self.assertRaisesRegex(ValueError, 'evidence_changed'):
            diagnostics.stage_artifacts(self.repo, self.output, report, Path(self.temp.name))


class DiagnosticIntegrationTests(unittest.TestCase):
    def test_failed_cli_parent_alias_preserves_paths_through_final_staging(self):
        with tempfile.TemporaryDirectory(prefix='ios-cli-alias-fixture-') as tmp:
            root = Path(tmp).resolve()
            repo = root / 'source'
            (repo / 'ios').mkdir(parents=True)
            (repo / 'ios/check-simulator.sh').write_text('# mocked failing client\n')
            parent_alias = root / 'parent-alias'
            parent_alias.symlink_to(root, target_is_directory=True)
            source_alias = parent_alias / 'source'
            fake = FakeAppleCommands(repo)
            def fail_build(args, **kwargs):
                # OS commands reach the same source through either spelling.
                self.assertEqual(kwargs['cwd'].resolve(), repo)
                kwargs = {**kwargs, 'cwd': repo}
                if args[0] == 'bash':
                    logs = repo / 'ios/build'
                    logs.mkdir()
                    (logs / 'native-build.log').write_text(
                        f'{source_alias}/ios/native/Alias.swift:12:3: error: unknown alias type\n'
                        f'{repo}/ios/native/Canonical.swift:14:2: error: unknown canonical type\n'
                        f'{source_alias}-sibling/Outside.swift:4:1: error: missing outside type\n')
                    raise checks.CheckError('command_failed', exit_code=1)
                return fake(args, **kwargs)
            actual_run = checks.run_checks
            def execute(*args, **kwargs):
                return actual_run(*args, run_command=fail_build, system_name='Darwin', wait=lambda _: None, **kwargs)
            output_control = root / 'github-output'
            with mock.patch.object(checks, 'run_checks', side_effect=execute), \
                    mock.patch.dict(os.environ, {'GITHUB_OUTPUT': str(output_control)}), \
                    contextlib.redirect_stdout(io.StringIO()):
                result = checks.main(['--repo', str(source_alias), '--artifact-root', str(root),
                                     '--expected-head', HEAD, '--mode', 'client', '--request-id', REQUEST])
            self.assertEqual(result, 1)
            stage = Path(output_control.read_text().strip().removeprefix('artifactDir='))
            self.assertFalse(stage.is_relative_to(repo))
            receipt = json.loads((stage / 'report.json').read_text())
            self.assertEqual(receipt['sourceHead'], HEAD)
            self.assertEqual(receipt['requestId'], REQUEST)
            text = (stage / receipt['diagnostics']['textPath']).read_text()
            self.assertIn('ios/native/Alias.swift:12:3: error: unknown alias type', text)
            self.assertIn('ios/native/Canonical.swift:14:2: error: unknown canonical type', text)
            self.assertIn('[external]/Outside.swift:4:1: error: missing outside type', text)
            self.assertNotIn(str(source_alias), text)
            self.assertNotIn(str(repo), text)

    def test_successful_client_poisoned_reserved_files_never_reach_uploaded_stage(self):
        with tempfile.TemporaryDirectory(prefix='ios-success-poison-fixture-') as tmp:
            repo = Path(tmp)
            (repo / 'ios').mkdir()
            (repo / 'ios/check-simulator.sh').write_text('# mocked client\n')
            fake = FakeAppleCommands(repo)
            def poison(args, **kwargs):
                result = fake(args, **kwargs)
                if args[0] == 'bash':
                    output = Path(args[3])
                    (output / 'client-diagnostics.json').mkdir()
                    (output / 'client-diagnostics.txt').write_text('unsanitized_private_marker')
                    (output / 'extra.mp4').write_text('unsanitized_private_marker')
                return result
            report = checks.run_checks(repo, repo / 'ios-evidence', HEAD, 'client', REQUEST, run_command=poison, system_name='Darwin', wait=lambda _: None)
            self.assertEqual(report['status'], 'passed')
            stage = Path(report['_artifactDirectory'])
            self.assertEqual(set(p.name for p in stage.iterdir()), {'report.json', 'preflight.png', 'client.png'})
            self.assertNotIn('diagnostics', json.loads((stage / 'report.json').read_text()))

    def test_preflight_and_missing_client_never_collect_build_logs(self):
        for mode in ('preflight', 'client'):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory(prefix='ios-no-build-diagnostics-') as tmp:
                repo = Path(tmp)
                logs = repo / 'ios/build'
                logs.mkdir(parents=True)
                (logs / 'native-build.log').write_text('error: unrelated_old_build\n')
                output = repo / 'ios-evidence'
                report = checks.run_checks(repo, output, HEAD, mode, REQUEST, run_command=FakeAppleCommands(repo), system_name='Darwin', wait=lambda _: None)
                self.assertNotIn('diagnostics', report)
                self.assertFalse((output / 'client-diagnostics.json').exists())
                self.assertFalse((Path(report['_artifactDirectory']) / 'client-diagnostics.json').exists())
                self.assertEqual(report['status'], 'passed' if mode == 'preflight' else 'failed')

    def test_failed_client_script_returns_bound_identity_exit_code_and_compile_error(self):
        with tempfile.TemporaryDirectory(prefix='ios-failed-build-fixture-') as tmp:
            repo = Path(tmp)
            (repo / 'ios').mkdir()
            (repo / 'ios/check-simulator.sh').write_text('# mocked failing client build\n')
            fake = FakeAppleCommands(repo)
            def fail_build(args, **kwargs):
                if args[0] == 'bash':
                    logs = repo / 'ios/build'
                    logs.mkdir()
                    (logs / 'java-build.log').write_text('[ERROR] ios/java/Bridge.java:[8,2] cannot find symbol\n[ERROR] PASSWORD=secret_fixture_value\n')
                    raise checks.CheckError('command_failed', exit_code=1)
                return fake(args, **kwargs)
            output = repo / 'ios-evidence'
            report = checks.run_checks(repo, output, HEAD, 'client', REQUEST, run_command=fail_build, system_name='Darwin', wait=lambda _: None)
            self.assertEqual(report['status'], 'failed')
            self.assertFalse(report['clientChecked'])
            summary = json.loads((Path(report['_artifactDirectory']) / report['diagnostics']['path']).read_text())
            self.assertEqual(summary['sourceHead'], HEAD)
            self.assertEqual(summary['requestId'], REQUEST)
            self.assertEqual(summary['errorStage'], 'client_script')
            self.assertEqual(summary['exitCode'], 1)
            self.assertIn('cannot find symbol', summary['lines'][0]['text'])
            self.assertNotIn('secret_fixture_value', json.dumps(summary))

    def test_command_error_exposes_only_numeric_exit_or_timeout_not_raw_streams(self):
        for error, expected in [(subprocess.CalledProcessError(127, ['private-command'], output='secret_fixture_value', stderr='private_fixture_value'), (127, False)),
                                (subprocess.TimeoutExpired(['private-command'], 1, output='secret_fixture_value'), (None, True))]:
            with self.subTest(error=type(error).__name__), mock.patch.object(checks.subprocess, 'run', side_effect=error):
                with self.assertRaises(checks.CheckError) as caught:
                    checks.command(['fixture'], cwd=Path('.'))
                self.assertEqual((caught.exception.exit_code, caught.exception.timed_out), expected)
                self.assertEqual(str(caught.exception), 'command_failed')
                self.assertNotIn('fixture_value', str(caught.exception))

    def test_workflow_uploads_only_summaries_not_build_logs(self):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/ios-simulator.yml').read_text()
        upload = workflow.split('- uses: actions/upload-artifact@v4', 1)[1]
        self.assertIn('path: ${{ steps.checks.outputs.artifactDir }}/*', upload)
        self.assertNotIn('source/ios-evidence', upload)
        self.assertIn("steps.checks.outputs.artifactDir != ''", upload)
        self.assertNotIn('source/ios/build', workflow)

    def test_client_cannot_write_workflow_output_or_environment_controls(self):
        with tempfile.TemporaryDirectory(prefix='ios-control-fixture-') as tmp:
            repo = Path(tmp)
            (repo / 'ios').mkdir()
            (repo / 'ios/check-simulator.sh').write_text('# mocked client\n')
            fake = FakeAppleCommands(repo)
            controls = {key: '/fake/workflow-control' for key in ('GITHUB_OUTPUT', 'GITHUB_ENV', 'GITHUB_PATH', 'GITHUB_STEP_SUMMARY')}
            with mock.patch.dict(checks.os.environ, controls):
                report = checks.run_checks(repo, repo / 'ios-evidence', HEAD, 'client', REQUEST, run_command=fake, system_name='Darwin', wait=lambda _: None)
            self.assertEqual(report['status'], 'passed')
            client_env = next(kw['env'] for args, kw in fake.calls if args[0] == 'bash')
            self.assertTrue(all(key not in client_env for key in controls))


if __name__ == '__main__':
    unittest.main()
