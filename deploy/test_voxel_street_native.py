"""Artifact isolation and actual-result gates; no graphical execution is claimed."""
import importlib.util
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import json

spec = importlib.util.spec_from_file_location('native', Path(__file__).with_name('check_voxel_street_native.py'))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


class EvidenceCollectionTest(unittest.TestCase):
    def test_only_allowlisted_synthetic_artifacts_leave_runtime(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output, destination = root / 'runtime', root / 'evidence'
            destination.mkdir()
            for label, name in native.RESULTS.items():
                folder = output / label
                folder.mkdir(parents=True)
                (folder / name).write_text('Playtest: PASS. Synthetic fixture.\n')
                (folder / 'private-runtime.txt').write_text('DO NOT COPY')
                (folder / 'account.dat').write_text('DO NOT COPY')
            (output / 'sandbox/voxel-sandbox-craft.png').write_bytes(b'synthetic capture')
            result = native.collect(output, destination)
            self.assertTrue(all(w['passed'] for w in result))
            self.assertEqual(len(list(destination.iterdir())), 4)
            self.assertFalse(any('private' in p.name or 'account' in p.name for p in destination.iterdir()))

    def test_missing_or_failed_workflows_do_not_pass_or_publish_failure_text(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output, destination = root / 'runtime', root / 'evidence'
            destination.mkdir()
            (output / 'catalog').mkdir(parents=True)
            (output / 'catalog/music-catalog.txt').write_text('FAIL: private diagnostic\n')
            result = native.collect(output, destination)
            self.assertFalse(any(w['passed'] for w in result))
            self.assertEqual(list(destination.iterdir()), [])


    def test_failed_run_clears_stale_passes_and_restores_historical_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'target/music-regressions/catalog').mkdir(parents=True)
            (root / 'target/music-regressions/catalog/music-catalog.txt').write_text('Playtest: PASS. stale')
            evidence = root / 'dashboard/evidence'
            evidence.mkdir(parents=True)
            historical = evidence / 'music-regression-music-catalog.txt'
            historical.write_bytes(b'original inherited report')
            def run(*args, **kwargs):
                self.assertFalse((root / 'target/music-regressions').exists())
                historical.write_bytes(b'runner overwrite')
                return SimpleNamespace(returncode=1)
            with patch.object(native, 'ROOT', root), patch.dict(native.os.environ, {'DISPLAY': ':synthetic-cli-fixture'}), patch.object(native.subprocess, 'check_output', return_value='a' * 40), patch.object(native.subprocess, 'run', side_effect=run), patch('sys.argv', ['check', '--display', ':synthetic-cli-fixture', '--expected-head', 'a' * 40]):
                self.assertEqual(native.main(), 1)
            self.assertEqual(historical.read_bytes(), b'original inherited report')
            report = json.loads((root / 'target/voxel-street-native-evidence/voxel-street-native-graphical.json').read_text())
            self.assertFalse(report['passed'])
            self.assertFalse(any(w['passed'] for w in report['workflows']))


if __name__ == '__main__':
    unittest.main()
