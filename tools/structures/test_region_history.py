"""Run the private region migration experiment against compiled production replay."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
CLASSES = ROOT / 'target/classes'


@unittest.skipUnless((CLASSES / 'dev/jayms/net/WorldVoxels.class').is_file(),
                     'Compile production classes with Maven before this integration check')
class RegionHistoryIntegrationTest(unittest.TestCase):
    def run_check(self, class_name):
        folder = ROOT / 'target/structure-region-history-tests'
        folder.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=folder) as temporary:
            sources = [ROOT / 'tools/structures' / name for name in
                       ('RegionHistoryPrototype.java', 'RegionHistoryPrototypeCheck.java', 'RegionHistoryCodecCheck.java',
                        'RegionDependencyPrototype.java', 'RegionDependencyCheck.java',
                        'RegionGenerationPrototype.java', 'RegionGenerationCheck.java', 'RegionGenerationEvidence.java',
                        'RegionAuthorityPrototype.java', 'RegionAuthorityCheck.java',
                        'RegionWorldLibraryPrototype.java', 'RegionWorldLibraryCheck.java', 'RegionOfflineMetadataPrototype.java', 'RegionOfflineMetadataCheck.java',
                        'RegionOfflineImportPrototype.java', 'RegionOfflineImportCheck.java', 'RegionOfflineLegacyCheck.java', 'RegionSidecarsPrototype.java', 'RegionSidecarsCheck.java', 'RegionOfflineBundlePrototype.java', 'RegionOfflineBundleCheck.java')]
            compiled = subprocess.run(['javac', '--release', '17', '-cp', str(CLASSES), '-d', temporary,
                                       *map(str, sources)], capture_output=True, text=True, timeout=30)
            self.assertEqual(compiled.returncode, 0, compiled.stderr[:4000])
            command = ['java', '-Xmx512m', '-cp', os.pathsep.join((temporary, str(CLASSES))), class_name]
            if class_name in ('RegionGenerationCheck', 'RegionAuthorityCheck'):
                command.append(str(Path(temporary) / 'synthetic-generations'))
            result = subprocess.run(command, check=True,
                                    capture_output=True, text=True, timeout=30)
            return [json.loads(line) for line in result.stdout.splitlines()]

    def test_actual_replay_migration(self):
        rows = self.run_check('RegionHistoryPrototypeCheck')
        self.assertEqual([row['terrain_version'] for row in rows], [1, 2, 3])
        for row in rows:
            self.assertEqual(row['status'], 'passed')
            self.assertFalse(row['qualification'])

    def test_bounded_codec(self):
        rows = self.run_check('RegionHistoryCodecCheck')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertGreaterEqual(rows[0]['rejection_cases'], 40)
        self.assertFalse(rows[0]['qualification'])

    def test_dependency_manifest(self):
        rows = self.run_check('RegionDependencyCheck')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['unique_definitions'], 3)
        self.assertGreaterEqual(rows[0]['rejection_cases'], 35)
        self.assertFalse(rows[0]['qualification'])

    def test_generation_publication(self):
        rows = self.run_check('RegionGenerationCheck')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['final_generation'], 4)
        self.assertGreaterEqual(rows[0]['mid_write_cancel_bytes'], 65536)
        self.assertFalse(rows[0]['directory_fsync_qualified'])
        self.assertFalse(rows[0]['qualification'])

    def test_offline_bundle(self):
        rows = self.run_check('RegionOfflineBundleCheck')
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertTrue(rows[0]['source_files_unchanged'])
        self.assertTrue(rows[0]['generation_restart'])
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])

    def test_sidecars(self):
        rows = self.run_check('RegionSidecarsCheck')
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertTrue(rows[0]['generation_restart'])
        self.assertFalse(rows[0]['semantic_city_validation'])
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])

    def test_offline_legacy(self):
        rows = self.run_check('RegionOfflineLegacyCheck')
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['legacy_versions'], 5)
        self.assertEqual(rows[0]['legacy_fixtures'], 7)
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])

    def test_offline_import(self):
        rows = self.run_check('RegionOfflineImportCheck')
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['production_save_fixtures'], 6)
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])

    def test_offline_metadata(self):
        rows = self.run_check('RegionOfflineMetadataCheck')
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['maximum_drop_count'], 100000)
        self.assertTrue(rows[0]['generation_restart'])
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])

    def test_world_library(self):
        rows = self.run_check('RegionWorldLibraryCheck')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['model_entries'], 4)
        self.assertEqual(rows[0]['unplaced_custom_entries'], 2)
        self.assertGreaterEqual(rows[0]['rejection_cases'], 18)
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])

    def test_actual_engine_authority(self):
        rows = self.run_check('RegionAuthorityCheck')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['rotations'], 4)
        self.assertEqual(rows[0]['world_library_generation'], 2)
        self.assertTrue(rows[0]['v1_upgrade'])
        self.assertEqual(rows[0]['retained_library_entries'], 5)
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])


if __name__ == '__main__':
    unittest.main()
