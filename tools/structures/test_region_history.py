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
                        'RegionOfflineImportPrototype.java', 'RegionOfflineImportCheck.java', 'RegionOfflineLegacyCheck.java', 'RegionSidecarsPrototype.java', 'RegionSidecarsCheck.java', 'RegionOfflineBundlePrototype.java', 'RegionOfflineBundleCheck.java', 'RegionServerMetadataPrototype.java', 'RegionServerMetadataCheck.java', 'RegionServerImportPrototype.java', 'RegionServerImportCheck.java', 'RegionServerBundleCheck.java', 'RegionOfflineOwnerPrototype.java', 'RegionOfflineOwnerCheck.java', 'CityHistorySnapshotCheck.java', 'RegionCityOwnerPrototype.java', 'RegionCityRestorePrototype.java', 'RegionCompleteRestoreCheck.java', 'RegionOfflineCellOwnerCheck.java', 'RegionDenseHistoryCheck.java')]
            compiled = subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '17', '-cp', str(CLASSES), '-d', temporary,
                                       *map(str, sources)], capture_output=True, text=True, timeout=30)
            self.assertEqual(compiled.returncode, 0, compiled.stderr[:4000])
            command = ['java', '-Xmx512m', '-cp', os.pathsep.join((temporary, str(CLASSES))), class_name]
            if class_name in ('RegionGenerationCheck', 'RegionAuthorityCheck'):
                command.append(str(Path(temporary) / 'synthetic-generations'))
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr[:4000])
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

    def test_complete_restore(self):
        row = self.run_check('RegionCompleteRestoreCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertTrue(row['strict_authority_restart'])
        self.assertTrue(row['strict_generation_restart'])
        self.assertEqual(row['legacy_city_formats'], 17)
        self.assertTrue(row['offline_and_server_metadata'])
        self.assertTrue(row['source_unchanged'])
        self.assertFalse(row['live_region_backend'])
        self.assertFalse(row['qualification'])

    def test_city_history_snapshot(self):
        row = self.run_check('CityHistorySnapshotCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['existing_wire_version'], 1)
        self.assertTrue(row['production_loader'])
        self.assertTrue(row['strict_stream_reader'])
        self.assertTrue(row['source_unchanged'])
        self.assertFalse(row['live_region_backend'])
        self.assertFalse(row['qualification'])

    def test_dense_indexed_history(self):
        row = self.run_check('RegionDenseHistoryCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['input_records'], 200704)
        self.assertEqual(row['chunk_batches'], 3)
        self.assertTrue(row['partial_stale_rejected'])
        self.assertTrue(row['preflight_budget_rejected'])
        self.assertEqual(row['final_cells'], 49)
        self.assertTrue(row['replay_path_rejected'])
        self.assertTrue(row['indexed_path_restored'])
        self.assertTrue(row['stale_rejected'])
        self.assertFalse(row['qualification'])

    def test_offline_cell_owner(self):
        row = self.run_check('RegionOfflineCellOwnerCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertGreaterEqual(row['captured_cells'], 2054)
        self.assertTrue(row['world_geometry_capture'])
        self.assertTrue(row['legacy_files_unchanged'])
        self.assertFalse(row['production_region_backend'])
        self.assertFalse(row['qualification'])

    def test_offline_owner(self):
        row = self.run_check('RegionOfflineOwnerCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertTrue(row['actual_local_game'])
        self.assertTrue(row['active_city_preserved'])
        self.assertTrue(row['legacy_file_untouched'])
        self.assertFalse(row['production_backend_selected'])
        self.assertFalse(row['qualification'])

    def test_server_bundle(self):
        row = self.run_check('RegionServerBundleCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertTrue(row['server_source'])
        self.assertTrue(row['generation_restart'])
        self.assertTrue(row['source_files_unchanged'])
        self.assertFalse(row['production_integration'])
        self.assertFalse(row['qualification'])

    def test_server_import(self):
        rows = self.run_check('RegionServerImportCheck')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['schema_fixtures'], 13)
        self.assertEqual(rows[0]['server_formats'], 7)
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])

    def test_server_metadata(self):
        rows = self.run_check('RegionServerMetadataCheck')
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['maximum_players'], 10000)
        self.assertEqual(rows[0]['maximum_drops'], 100000)
        self.assertTrue(rows[0]['generation_restart'])
        self.assertFalse(rows[0]['production_integration'])
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
