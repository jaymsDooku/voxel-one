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
                       ('HashTrieChunkDirectoryCheck.java', 'WorldSnapshotProbe.java', 'WorldChunkLookupProbe.java', 'SharedHashChunkDirectoryPrototype.java', 'SharedHashChunkDirectoryCheck.java', 'PersistentChunkDirectoryPrototype.java', 'PersistentChunkDirectoryCheck.java', 'DiskPackageIndexPrototype.java', 'DiskPackageIndexCheck.java', 'DiskPackageIndexEvidence.java', 'DiskRegionCachePrototype.java', 'DiskRegionCacheCheck.java', 'AsyncRegionReadPrototype.java', 'AsyncRegionReadCheck.java', 'NeighborBatchBenchmark.java', 'RegionReadViewPrototype.java', 'RegionReadViewCheck.java', 'RegionHistoryPrototype.java', 'RegionHistoryPrototypeCheck.java', 'RegionHistoryCodecCheck.java', 'RegionHistoryStreamCheck.java', 'RegionHistoryStreamEvidence.java',
                        'RegionDependencyPrototype.java', 'RegionDependencyCheck.java',
                        'RegionGenerationPrototype.java', 'RegionGenerationCheck.java', 'RegionGenerationEvidence.java',
                        'RegionAuthorityPrototype.java', 'RegionAuthorityCheck.java',
                        'RegionWorldLibraryPrototype.java', 'RegionWorldLibraryCheck.java', 'RegionEnvelopeViewCheck.java', 'RegionEnvelopeViewEvidence.java', 'RegionOfflineMetadataPrototype.java', 'RegionOfflineMetadataCheck.java',
                        'RegionOfflineImportPrototype.java', 'RegionOfflineImportCheck.java', 'RegionOfflineLegacyCheck.java', 'RegionSidecarsPrototype.java', 'RegionSidecarsCheck.java', 'RegionOfflineBundlePrototype.java', 'RegionOfflineBundleCheck.java', 'RegionServerMetadataPrototype.java', 'RegionServerMetadataCheck.java', 'RegionServerImportPrototype.java', 'RegionServerImportCheck.java', 'RegionServerBundleCheck.java', 'RegionOfflineOwnerPrototype.java', 'RegionOfflineOwnerCheck.java', 'CityHistorySnapshotCheck.java', 'RegionCityOwnerPrototype.java', 'RegionCityRestorePrototype.java', 'RegionCompleteRestoreCheck.java', 'RegionOfflineCellOwnerCheck.java', 'RegionDenseHistoryCheck.java', 'WorldOverrideCursorCheck.java', 'WorldOverrideCursorEvidence.java', 'ChunkCaptureChurnCheck.java', 'ChunkCaptureChurnEvidence.java')]
            compiled = subprocess.run(['javac', '-encoding', 'UTF-8', '--release', '17', '-cp', str(CLASSES), '-d', temporary,
                                       *map(str, sources)], capture_output=True, text=True, timeout=30)
            self.assertEqual(compiled.returncode, 0, compiled.stderr[:4000])
            command = ['java', '-Xmx512m', '-cp', os.pathsep.join((temporary, str(CLASSES))), class_name]
            if class_name == 'WorldChunkLookupProbe':
                command.append(str(Path(temporary) / 'synthetic-lookup.json'))
            if class_name in ('RegionGenerationCheck', 'RegionAuthorityCheck'):
                command.append(str(Path(temporary) / 'synthetic-generations'))
            result = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr[:4000])
            return [json.loads(line) for line in result.stdout.splitlines()]

    def test_world_chunk_lookup(self):
        row = self.run_check('WorldChunkLookupProbe')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['chunks'], 8192)
        self.assertEqual(row['runs'], 20)
        self.assertTrue(row['snapshot_edit_clear'])
        self.assertFalse(row['qualification'])
        for name in ('coherent_ms', 'mixed_ms', 'coherent_allocated_bytes', 'mixed_allocated_bytes'):
            values = sorted(row[name]['samples'])
            self.assertEqual(len(values), 20)
            self.assertAlmostEqual(row[name]['median'], (values[9] + values[10]) / 2, places=5)
            self.assertAlmostEqual(row[name]['p95'], values[18], places=5)
            self.assertAlmostEqual(row[name]['worst'], values[19], places=5)

    def test_hash_trie_chunk_directory(self):
        row = self.run_check('HashTrieChunkDirectoryCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['rejection_cases'], 11)
        self.assertEqual(row['random_operations'], 25000)
        self.assertTrue(row['frozen_world_images'])
        self.assertFalse(row['qualification'])
        result = row['benchmark']
        self.assertLessEqual(result['trie_depth'], 7)
        self.assertLessEqual(result['collision_entries'], 16)
        self.assertEqual(result['runs'], 20)
        for name in ('linked_snapshot_ms', 'linked_update_ms', 'trie_update_ms',
                     'linked_read_ms', 'trie_read_ms'):
            values = sorted(result[name]['samples'])
            self.assertEqual(len(values), 20)
            self.assertAlmostEqual(result[name]['median'], (values[9] + values[10]) / 2, places=5)
            self.assertAlmostEqual(result[name]['p95'], values[18], places=5)
            self.assertAlmostEqual(result[name]['worst'], values[19], places=5)

    def test_shared_hash_chunk_directory(self):
        row = self.run_check('SharedHashChunkDirectoryCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['rejection_cases'], 11)
        self.assertEqual(row['random_operations'], 25000)
        self.assertEqual(row['retained_roots'], 8)
        self.assertTrue(row['frozen_world_images'])
        self.assertFalse(row['qualification'])
        result = row['benchmark']
        self.assertEqual(result['entries'], 8192)
        self.assertEqual(result['shards'], 64)
        self.assertEqual(result['copied_entries_after_updates'], 8192)
        self.assertEqual(result['runs'], 20)
        for name in ('linked_snapshot_ms', 'shared_snapshot_ms', 'linked_update_ms',
                     'shared_update_ms', 'linked_read_ms', 'shared_read_ms'):
            samples = sorted(result[name]['samples'])
            self.assertEqual(len(samples), 20)
            self.assertAlmostEqual(result[name]['median'], (samples[9] + samples[10]) / 2, places=5)
            self.assertAlmostEqual(result[name]['p95'], samples[18], places=5)
            self.assertAlmostEqual(result[name]['worst'], samples[19], places=5)

    def test_persistent_chunk_directory(self):
        row = self.run_check('PersistentChunkDirectoryCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['rejection_cases'], 11)
        self.assertEqual(row['random_operations'], 25000)
        self.assertEqual(row['retained_roots'], 8)
        self.assertTrue(row['frozen_world_images'])
        self.assertFalse(row['qualification'])
        result = row['benchmark']
        self.assertEqual(result['entries'], 8192)
        self.assertLessEqual(result['height'], 20)
        self.assertEqual(result['runs'], 20)
        self.assertEqual(result['writes_per_run'], 256)
        self.assertEqual(result['queries_per_run'], 65536)
        for name in ('hash_snapshot_ms', 'hash_update_ms', 'tree_update_ms',
                     'hash_read_ms', 'tree_read_ms'):
            samples = sorted(result[name]['samples'])
            self.assertEqual(len(samples), 20)
            self.assertAlmostEqual(result[name]['median'], (samples[9] + samples[10]) / 2, places=5)
            self.assertAlmostEqual(result[name]['p95'], samples[18], places=5)
            self.assertAlmostEqual(result[name]['worst'], samples[19], places=5)

    def test_region_read_view(self):
        row = self.run_check('RegionReadViewCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['rejection_cases'], 12)
        self.assertTrue(row['completed_results_revalidated'])
        self.assertTrue(row['unrelated_edits_preserved'])
        self.assertFalse(row['qualification'])

    def test_async_region_reads(self):
        row = self.run_check('AsyncRegionReadCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['rejection_cases'], 11)
        self.assertEqual(row['disk_cells'], 4)
        self.assertEqual(row['known_absent'], 1)
        self.assertTrue(row['worker_only_io'])
        self.assertFalse(row['qualification'])

    def test_disk_region_cache(self):
        row = self.run_check('DiskRegionCacheCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['cells'], 4)
        self.assertEqual(row['regions'], 3)
        self.assertEqual(row['cache_pages'], 2)
        self.assertEqual(row['rejection_cases'], 14)
        self.assertTrue(row['world_patch_queries'])
        self.assertFalse(row['qualification'])

    def test_disk_package_index(self):
        row = self.run_check('DiskPackageIndexCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['cells'], 5)
        self.assertEqual(row['regions'], 3)
        self.assertEqual(row['rejection_cases'], 18)
        self.assertTrue(row['production_byte_roundtrip'])
        self.assertTrue(row['cache_cleanup'])
        self.assertFalse(row['qualification'])

    def test_actual_replay_migration(self):
        rows = self.run_check('RegionHistoryPrototypeCheck')
        self.assertEqual([row['terrain_version'] for row in rows], [1, 2, 3])
        for row in rows:
            self.assertEqual(row['status'], 'passed')
            self.assertFalse(row['qualification'])

    def test_world_override_cursor(self):
        row = self.run_check('WorldOverrideCursorCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['stored_cells'], 2054)
        self.assertEqual(row['flat_records'], 0)
        for key in ['strict_restart', 'models_and_survival', 'air_overrides_terrain', 'stale_world_rejected', 'retry', 'source_unchanged']:
            self.assertTrue(row[key])
        self.assertFalse(row['qualification'])

    def test_envelope_section_views(self):
        row = self.run_check('RegionEnvelopeViewCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['versions'], 4)
        self.assertGreaterEqual(row['rejection_cases'], 40)
        for key in ['byte_exact', 'geometry_and_air', 'library_preserved', 'metadata_boundaries', 'array_ranges', 'generation_restart', 'source_unchanged', 'retry']:
            self.assertTrue(row[key])
        self.assertFalse(row['qualification'])

    def test_region_streams(self):
        row = self.run_check('RegionHistoryStreamCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertGreaterEqual(row['rejection_cases'], 16)
        for key in ['short_reads', 'byte_exact', 'caller_streams_open', 'cap_plus_one_probe', 'geometry_and_air', 'file_restart', 'retry']:
            self.assertTrue(row[key])
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

    def test_chunk_capture_churn(self):
        row = self.run_check('ChunkCaptureChurnCheck')[0]
        self.assertEqual(row['status'], 'passed')
        self.assertEqual(row['runs'], 20)
        self.assertEqual(row['advances'], 3)
        self.assertEqual(row['final_chunks'], 257)
        self.assertTrue(row['geometry_checked_each_run'])
        self.assertTrue(row['stale_clear_rejected'])
        self.assertFalse(row['qualification'])
        for key, samples in [('wall', 'wall_samples_ms'), ('max_advance_per_run', 'max_advance_samples_ms')]:
            values = sorted(row[samples])
            self.assertEqual(len(values), 20)
            self.assertAlmostEqual(row[key]['median_ms'], (values[9]+values[10])/2, places=5)
            self.assertAlmostEqual(row[key]['p95_ms'], values[18], places=5)
            self.assertAlmostEqual(row[key]['worst_ms'], values[19], places=5)

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
