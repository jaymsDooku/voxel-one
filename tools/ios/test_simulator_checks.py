"""Linux-compatible contract tests; all Apple commands and apps are MOCKS."""
import copy
import contextlib
import io
import json
from pathlib import Path
import plistlib
import struct
import tempfile
import unittest
from unittest import mock
import zlib

import simulator_checks as checks

HEAD = 'a' * 40
REQUEST = 'e3dc3d1a-7337-4c72-a372-89276285cd00'
UDID = 'C6832151-D858-43BF-B90D-05F63089554B'
TYPE = 'com.apple.CoreSimulator.SimDeviceType.iPhone-16-Pro'
RUNTIME = 'com.apple.CoreSimulator.SimRuntime.iOS-18-5'


def inventory():
    return {'devicetypes': [{'identifier': TYPE, 'name': 'iPhone 16 Pro'}],
            'runtimes': [{'identifier': RUNTIME, 'name': 'iOS 18.5', 'version': '18.5', 'isAvailable': True}],
            'devices': {RUNTIME: [{'deviceTypeIdentifier': TYPE, 'name': 'iPhone 16 Pro', 'udid': 'OLD-DEVICE', 'isAvailable': True}]}}


def fixture_png():
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    width, height = 320, 568
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress((b'\0' + b'\0' * (width * 3)) * height)) + chunk(b'IEND', b''))


class FakeAppleCommands:
    def __init__(self, repo):
        self.repo = repo
        self.calls = []
        self.inventory = inventory()
        self.head = HEAD
        self.receipt_mutator = lambda r: None
        self.install_missing = False
        self.booted = True
        self.launch = 'example.voxelone.client: 123'
        self.client_runs = 0
        self.failure_stage = None

    def __call__(self, args, **kwargs):
        assert kwargs['cwd'] == self.repo, 'All Git and client commands must use requested source, not verifier checkout'
        self.calls.append((args, kwargs))
        stage = args[2] if args[:2] == ['xcrun', 'simctl'] else None
        if stage == 'io':
            stage = 'screenshot'
        if stage == self.failure_stage and stage is not None:
            raise checks.CheckError('command_failed')
        if args == ['git', 'rev-parse', 'HEAD']:
            return self.head
        if args == ['git', 'diff', '--quiet', 'HEAD', '--']:
            return ''
        if args[0] in ('sw_vers', 'xcodebuild'):
            return 'MOCK version; not actual Mac evidence'
        if args[:3] == ['xcrun', '--sdk', 'iphonesimulator']:
            return '18.5'
        if args == ['xcrun', 'simctl', 'list', '--json']:
            return json.dumps(self.inventory)
        if args[:3] == ['xcrun', 'simctl', 'create']:
            return UDID
        if args == ['xcrun', 'simctl', 'list', 'devices', '--json']:
            return json.dumps({'devices': {RUNTIME: [{'udid': UDID, 'state': 'Booted' if self.booted else 'Shutdown', 'isAvailable': True}]}})
        if args[:3] == ['xcrun', 'simctl', 'io']:
            Path(args[-1]).write_bytes(fixture_png())
            return ''
        if args[0] == 'bash':
            self.client_runs += 1
            output = Path(args[3])
            app = self.repo / 'ios/build/MockVoxel.app'
            app.mkdir(parents=True)
            (app / 'Info.plist').write_bytes(plistlib.dumps({'CFBundleIdentifier': 'example.voxelone.client', 'CFBundleExecutable': 'MockVoxel'}))
            (app / 'MockVoxel').write_bytes(b'\xcf\xfa\xed\xfe' + b'MOCK; not a runnable binary')
            receipt = {'sourceHead': HEAD, 'simulatorUdid': UDID, 'application': 'voxel-one', 'evidenceType': 'native-client',
                       'synthetic': False, 'status': 'passed', 'bundleId': 'example.voxelone.client',
                       'appPath': 'ios/build/MockVoxel.app', 'tests': [{'name': 'MOCK contract fixture workflow', 'status': 'passed'}], 'videos': []}
            self.receipt_mutator(receipt)
            (output / 'client-result.json').write_text(json.dumps(receipt))
            return ''
        if args[:3] == ['xcrun', 'simctl', 'get_app_container']:
            if self.install_missing:
                raise checks.CheckError('command_failed')
            return str(self.repo / 'ios/build/MockVoxel.app')
        if args[:3] == ['xcrun', 'simctl', 'launch']:
            return self.launch
        if args[:3] in (['xcrun', 'simctl', 'boot'], ['xcrun', 'simctl', 'bootstatus'],
                       ['xcrun', 'simctl', 'shutdown'], ['xcrun', 'simctl', 'delete']):
            return ''
        raise AssertionError('Unexpected mock command')


class SelectionTests(unittest.TestCase):
    def test_dynamic_selection_prefers_latest_available_ios_iphone_pair(self):
        data = inventory()
        old = 'com.apple.CoreSimulator.SimRuntime.iOS-17-5'
        data['runtimes'].append({'identifier': old, 'name': 'iOS 17.5', 'version': '17.5', 'isAvailable': True})
        data['devices'][old] = copy.deepcopy(data['devices'][RUNTIME])
        runtime, device = checks.select_iphone(data)
        self.assertEqual(runtime['identifier'], RUNTIME)
        self.assertEqual(device['identifier'], TYPE)

    def test_unavailable_newer_runtime_and_ipad_are_not_selected(self):
        data = inventory()
        data['runtimes'].append({'identifier': 'com.apple.CoreSimulator.SimRuntime.iOS-99-0', 'name': 'iOS 99', 'version': '99.0', 'isAvailable': False})
        data['devicetypes'].append({'identifier': 'com.apple.CoreSimulator.SimDeviceType.iPad-Pro', 'name': 'iPad Pro'})
        data['devices'][RUNTIME].append({'deviceTypeIdentifier': 'com.apple.CoreSimulator.SimDeviceType.iPad-Pro', 'isAvailable': True})
        self.assertEqual(checks.select_iphone(data)[1]['identifier'], TYPE)

    def test_no_available_iphone_pair_fails_without_inventing_a_version(self):
        data = inventory()
        data['devices'][RUNTIME][0]['isAvailable'] = False
        with self.assertRaisesRegex(checks.CheckError, 'available_iphone_runtime_missing'):
            checks.select_iphone(data)

    def test_request_rejects_branches_shell_syntax_modes_and_non_uuid_ids(self):
        for head, mode, request in [('master', 'preflight', REQUEST), ('a' * 40 + '\n', 'preflight', REQUEST),
                                    (HEAD, 'shell', REQUEST), (HEAD, 'client', 'test; command')]:
            with self.subTest(head=head, mode=mode, request=request), self.assertRaises(checks.CheckError):
                checks.validate_request(head, mode, request)

    def test_active_sdk_18_5_selects_18_5_over_newer_installed_26_2(self):
        data = inventory()
        newer = 'com.apple.CoreSimulator.SimRuntime.iOS-26-2'
        data['runtimes'].append({'identifier': newer, 'name': 'iOS 26.2', 'version': '26.2', 'isAvailable': True})
        data['devices'][newer] = copy.deepcopy(data['devices'][RUNTIME])
        self.assertEqual(checks.select_iphone(data)[0]['identifier'], newer)
        self.assertEqual(checks.select_iphone(data, max_sdk='18.5')[0]['identifier'], RUNTIME)

    def test_no_compatible_available_pair_reports_actionable_error(self):
        data = inventory()
        data['runtimes'][0]['version'] = '26.2'
        with self.assertRaisesRegex(checks.CheckError, 'compatible_iphone_runtime_missing'):
            checks.select_iphone(data, max_sdk='18.5')

    def test_sdk_runtime_compatibility_uses_major_minor_and_rejects_unknown_sdk(self):
        data = inventory()
        data['runtimes'][0]['version'] = '18.5.2'
        self.assertEqual(checks.select_iphone(data, max_sdk='18.5.1')[0]['identifier'], RUNTIME)
        with self.assertRaisesRegex(checks.CheckError, 'simulator_sdk_version_invalid'):
            checks.select_iphone(data, max_sdk='unknown SDK')


class RunContractTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='ios-mock-contract-')
        self.repo = Path(self.temp.name)
        self.output = self.repo / 'ios-evidence'
        self.fake = FakeAppleCommands(self.repo)

    def tearDown(self):
        self.temp.cleanup()

    def run_check(self, mode='preflight', system='Darwin'):
        return checks.run_checks(self.repo, self.output, HEAD, mode, REQUEST,
                                 run_command=self.fake, system_name=system, wait=lambda _: None)

    def script(self):
        (self.repo / 'ios').mkdir()
        (self.repo / 'ios/check-simulator.sh').write_text('# MOCK script; executed only by FakeAppleCommands\n')

    def test_mock_preflight_creates_fresh_device_and_is_not_client_evidence(self):
        result = self.run_check()
        self.assertEqual(result['status'], 'passed')
        self.assertFalse(result['clientChecked'])
        self.assertTrue(result['environmentReady'])
        self.assertEqual(result['sourceHead'], HEAD)
        self.assertEqual(result['udid'], UDID)
        self.assertIn('not Voxel One', result['evidence'][0]['description'])
        create = [a for a, _ in self.fake.calls if a[:3] == ['xcrun', 'simctl', 'create']]
        self.assertEqual(len(create), 1)
        self.assertEqual(create[0][-2:], [TYPE, RUNTIME])
        self.assertNotIn('OLD-DEVICE', [s for a, _ in self.fake.calls for s in a])
        self.assertEqual(self.fake.calls[-2][0], ['xcrun', 'simctl', 'shutdown', UDID])
        self.assertEqual(self.fake.calls[-1][0], ['xcrun', 'simctl', 'delete', UDID])

    def test_wrong_source_head_fails_before_any_apple_command(self):
        self.fake.head = 'b' * 40
        result = self.run_check()
        self.assertEqual(result['errorCode'], 'source_head_mismatch')
        self.assertFalse(any(a[0] == 'xcrun' for a, _ in self.fake.calls))
        self.assertEqual(json.loads((self.output / 'report.json').read_text())['status'], 'failed')

    def test_linux_cannot_claim_simulator_readiness(self):
        result = self.run_check(system='Linux')
        self.assertEqual(result['errorCode'], 'macos_required')
        self.assertFalse(result['environmentReady'])

    def test_missing_native_client_script_fails_after_successful_environment_preflight(self):
        result = self.run_check('client')
        self.assertEqual(result['status'], 'failed')
        self.assertEqual(result['errorCode'], 'native_client_script_missing')
        self.assertFalse(result['clientChecked'])
        self.assertTrue(result['environmentReady'])

    def test_mock_client_contract_checks_install_launch_and_uses_own_simctl_capture(self):
        self.script()
        result = self.run_check('client')
        self.assertEqual(result['status'], 'passed')
        self.assertTrue(result['clientChecked'])
        self.assertEqual(self.fake.client_runs, 1)
        bash = next((a, kw) for a, kw in self.fake.calls if a[0] == 'bash')
        self.assertEqual(bash[0][2:], [UDID, str(self.output), HEAD])
        self.assertEqual(bash[1]['env']['IOS_SOURCE_HEAD'], HEAD)
        self.assertTrue(any(a[:3] == ['xcrun', 'simctl', 'get_app_container'] for a, _ in self.fake.calls))
        self.assertTrue(any(a[:3] == ['xcrun', 'simctl', 'launch'] for a, _ in self.fake.calls))
        self.assertTrue(any(a[-1] == str(self.output / 'client.png') for a, _ in self.fake.calls))

    def test_synthetic_only_receipt_fails(self):
        self.script()
        self.fake.receipt_mutator = lambda r: r.update(synthetic=True, evidenceType='diagnostic-harness')
        result = self.run_check('client')
        self.assertEqual(result['errorCode'], 'native_client_receipt_invalid')
        self.assertFalse(result['clientChecked'])

    def test_uninstalled_client_and_wrong_launch_receipt_cannot_pass(self):
        self.script()
        self.fake.install_missing = True
        result = self.run_check('client')
        self.assertEqual(result['status'], 'failed')
        self.assertFalse(result['clientChecked'])
        self.assertFalse((self.output / 'client.png').exists())

    def test_client_launch_failure_never_becomes_checked(self):
        self.script()
        self.fake.launch = 'diagnostic-app: 123'
        result = self.run_check('client')
        self.assertEqual(result['errorCode'], 'native_client_launch_failed')
        self.assertFalse(result['clientChecked'])

    def test_source_head_change_during_client_script_invalidates_evidence(self):
        self.script()
        self.fake.receipt_mutator = lambda r: setattr(self.fake, 'head', 'b' * 40)
        result = self.run_check('client')
        self.assertEqual(result['errorCode'], 'source_head_changed')
        self.assertFalse(result['clientChecked'])

    def test_non_native_executable_cannot_pass_app_verification(self):
        self.script()
        def replace_binary(receipt):
            (self.repo / receipt['appPath'] / 'MockVoxel').write_text('synthetic diagnostic output')
        self.fake.receipt_mutator = replace_binary
        result = self.run_check('client')
        self.assertEqual(result['errorCode'], 'native_app_invalid')
        self.assertFalse(result['clientChecked'])

    def test_receipt_requires_matching_head_udid_passed_workflows_and_native_app(self):
        changes = [dict(sourceHead='b' * 40), dict(simulatorUdid='other'), dict(tests=[]),
                   dict(appPath='../private.app'), dict(bundleId='example.harness'), dict(videos=['../private.mp4'])]
        for index, change in enumerate(changes):
            with self.subTest(change=change):
                root = self.repo / str(index)
                root.mkdir()
                (root / 'ios').mkdir()
                (root / 'ios/check-simulator.sh').write_text('# MOCK\n')
                fake = FakeAppleCommands(root)
                fake.receipt_mutator = lambda r, change=change: r.update(change)
                result = checks.run_checks(root, root / 'evidence', HEAD, 'client', REQUEST,
                                          run_command=fake, system_name='Darwin', wait=lambda _: None)
                self.assertEqual(result['status'], 'failed')
                self.assertFalse(result['clientChecked'])

    def test_not_booted_fails_and_preserves_safe_error_report(self):
        self.fake.booted = False
        result = self.run_check()
        self.assertEqual(result['errorCode'], 'simulator_not_booted')
        self.assertFalse(result['environmentReady'])
        self.assertEqual(result['cleanup'], 'passed')

    def test_existing_evidence_is_not_reused_or_overwritten(self):
        self.output.mkdir()
        (self.output / 'report.json').write_text('preserved')
        result = self.run_check()
        self.assertEqual(result['errorCode'], 'evidence_directory_not_fresh')
        self.assertEqual((self.output / 'report.json').read_text(), 'preserved')
        self.assertEqual(self.fake.calls, [])

    def test_media_paths_and_corrupt_png_are_rejected(self):
        self.output.mkdir()
        (self.output / 'fake.png').write_bytes(b'not a simulator screenshot')
        with self.assertRaisesRegex(checks.CheckError, 'invalid_screenshot'):
            checks.media_metadata(self.output, 'fake.png')
        with self.assertRaisesRegex(checks.CheckError, 'invalid_evidence_path'):
            checks.media_metadata(self.output, '../private.png')

    def test_command_failures_record_fixed_stage_without_raw_logs(self):
        for stage in ('create', 'boot', 'bootstatus', 'screenshot'):
            with self.subTest(stage=stage):
                root = self.repo / stage
                root.mkdir()
                fake = FakeAppleCommands(root)
                fake.failure_stage = stage
                report = checks.run_checks(root, root / 'evidence', HEAD, 'preflight', REQUEST,
                                          run_command=fake, system_name='Darwin', wait=lambda _: None)
                self.assertEqual(report['status'], 'failed')
                self.assertEqual(report['errorCode'], 'command_failed')
                self.assertEqual(report['errorStage'], stage)
                self.assertEqual(report['phase'], stage)
                self.assertFalse(report['clientChecked'])
                self.assertNotIn('stdout', report)
                self.assertNotIn('stderr', report)


class WorkflowContractTests(unittest.TestCase):
    def test_workflow_has_exact_head_read_only_permissions_and_scoped_triggers(self):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/ios-simulator.yml').read_text()
        self.assertIn("run-name: 'Voxel iOS ${{ inputs.requestId || github.sha }}'", workflow)
        self.assertIn('runs-on: macos-15', workflow)
        self.assertIn('name: ios-evidence', workflow)
        self.assertIn('persist-credentials: false', workflow)
        self.assertIn('ref: ${{ steps.request.outputs.head }}', workflow)
        self.assertIn('path: source', workflow)
        self.assertIn('path: validator', workflow)
        self.assertIn('ref: ${{ github.sha }}', workflow)
        self.assertEqual(workflow.count('persist-credentials: false'), 2)
        self.assertIn('git -C source rev-parse HEAD', workflow)
        self.assertIn('git -C validator rev-parse HEAD', workflow)
        self.assertIn('unittest discover -s validator/tools/ios', workflow)
        self.assertIn('python3 validator/tools/ios/simulator_checks.py --repo "$GITHUB_WORKSPACE/source"', workflow)
        self.assertIn('source/ios-evidence/report.json', workflow)
        self.assertIn('branches: [feature/ios-worker-bootstrap]', workflow)
        self.assertIn('if: always()', workflow)
        self.assertNotIn('pull_request_target', workflow)
        self.assertNotIn('secrets.', workflow)
        self.assertNotIn('contents: write', workflow)

    def test_cli_repo_and_relative_evidence_paths_use_requested_checkout(self):
        with tempfile.TemporaryDirectory(prefix='ios-source-checkout-') as tmp:
            repo = Path(tmp) / 'source'
            repo.mkdir()
            captured = {}
            def execute(source, output, head, mode, request):
                captured.update(repo=source, output=output, head=head)
                return {'status': 'passed', 'sourceHead': HEAD, 'clientChecked': False}
            with mock.patch.object(checks, 'run_checks', side_effect=execute), contextlib.redirect_stdout(io.StringIO()):
                result = checks.main(['--repo', str(repo), '--expected-head', HEAD, '--mode', 'preflight', '--request-id', REQUEST])
            self.assertEqual(result, 0)
            self.assertEqual(captured['repo'], repo.resolve())
            self.assertEqual(captured['output'], repo.resolve() / 'ios-evidence')
            self.assertEqual(captured['head'], HEAD)

    def test_trusted_cli_checks_actual_source_git_head_in_separate_repository(self):
        with tempfile.TemporaryDirectory(prefix='ios-real-source-head-') as tmp:
            repo = Path(tmp) / 'source'
            repo.mkdir()
            checks.command(['git', 'init'], cwd=repo)
            (repo / 'source.txt').write_text('public source fixture\n')
            checks.command(['git', 'add', 'source.txt'], cwd=repo)
            checks.command(['git', '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid', 'commit', '-m', 'fixture'], cwd=repo)
            head = checks.command(['git', 'rev-parse', 'HEAD'], cwd=repo)
            # A feature's replacement verifier is deliberately unusable. The
            # already imported trusted verifier never executes this source file.
            (repo / 'tools/ios').mkdir(parents=True)
            (repo / 'tools/ios/simulator_checks.py').write_text('raise SystemExit("feature verifier must not execute")\n')
            with mock.patch.object(checks.platform, 'system', return_value='Linux'), contextlib.redirect_stdout(io.StringIO()):
                result = checks.main(['--repo', str(repo), '--expected-head', head, '--mode', 'preflight', '--request-id', REQUEST])
            report = json.loads((repo / 'ios-evidence/report.json').read_text())
            self.assertEqual(result, 1)
            self.assertEqual(report['sourceHead'], head)
            self.assertEqual(report['errorCode'], 'macos_required')
            self.assertFalse(report['clientChecked'])

    def test_cli_rejects_evidence_outside_requested_source(self):
        with tempfile.TemporaryDirectory(prefix='ios-source-boundary-') as tmp:
            repo = Path(tmp) / 'source'
            repo.mkdir()
            with self.assertRaisesRegex(SystemExit, 'inside the source checkout'):
                checks.main(['--repo', str(repo), '--evidence-dir', '../validator/evidence', '--expected-head', HEAD,
                             '--mode', 'preflight', '--request-id', REQUEST])


if __name__ == '__main__':
    unittest.main()
