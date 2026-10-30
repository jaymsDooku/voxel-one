"""Synthetic checker tests. These do not qualify the game or benchmark hardware."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
import check_qualification as checker

HEAD = 'a' * 40


def fixture(name):
    run = dict(id=1, completed=True, clients_synchronized=32, rendered_clients=32, empty_dependency_caches=True,
               concurrent_placements=1, upload_s=1, ack_ms=10, max_main_thread_stall_ms=10,
               min_fps=60, server_hz=20, tick_p95_ms=10, frame_p95_ms=16,
               frame_worst_ms=20, peak_memory_bytes=1024, traffic_bytes=1024)
    run.update({key: .01 for key in checker.TIMINGS})
    result = dict(admitted=True, package_sha256='b' * 64,
                  dimensions=[512, 128, 512] if name == 'city' else [1024, 256, 1024],
                  occupied_cells=1_000_000 if name == 'city' else 4_000_000,
                  models=256 if name == 'city' else 512, instances=10_240,
                  compressed_bytes=20 * 1024 * 1024, geometry_leaves=4_000_000,
                  buildings=121, roads=True, pylons=True, fractional_geometry=True,
                  fragmented=True, evidence='SYNTHETIC TEST DATA; not execution evidence',
                  runs=[dict(run, id=i) for i in range(1, 21)])
    result.update({key: True for key in ('progress_over_1s_verified', 'cancellation_verified',
                  'nearby_geometry_usable', 'durable_save_verified', 'cold_cache_verified',
                  'upload_excluded_from_placement', 'sync_from_commit_verified')})
    return result


def receipt():
    env = {key: 'synthetic test environment' for key in ('client_cpu', 'client_ram', 'gpu',
           'gpu_driver', 'client_os', 'server_cpu', 'server_ram', 'server_os',
           'network_shape', 'network_measurement')}
    env.update(reference_id='synthetic', physical_gpu=True, network_shape_verified=True)
    return dict(schema=1, source_head=HEAD, environment=env,
                fixtures={name: fixture(name) for name in ('city', 'maximum')})


class QualificationTest(unittest.TestCase):
    def check(self, data):
        return checker.check(data, HEAD, 'synthetic')

    def test_synthetic_complete_receipt_and_nearest_rank_summary(self):
        result = self.check(receipt())
        self.assertEqual('passed', result['receipt_budgets'])
        self.assertEqual(20, result['fixtures']['maximum']['runs'])
        self.assertEqual(dict(median=10.5, p95=19, worst=20), checker.summary(list(range(1, 21))))

    def test_missing_wrong_source_environment_and_software_rendering_fail(self):
        for mutate in (lambda d: d.pop('fixtures'), lambda d: d.update(source_head='c' * 40),
                       lambda d: d['environment'].update(reference_id='other'),
                       lambda d: d['environment'].update(physical_gpu=False),
                       lambda d: d['environment'].pop('gpu_driver')):
            data = receipt(); mutate(data)
            with self.assertRaises(ValueError): self.check(data)

    def test_incomplete_fixture_runs_and_conditions_fail(self):
        for mutate in (lambda f: f.update(admitted=False), lambda f: f.update(runs=f['runs'][:19]),
                       lambda f: f['runs'][1].update(id=1), lambda f: f.update(models=1),
                       lambda f: f.update(compressed_bytes=1), lambda f: f.update(buildings=99),
                       lambda f: f.update(cancellation_verified=False),
                       lambda f: f['runs'][0].update(clients_synchronized=31),
                       lambda f: f['runs'][0].update(rendered_clients=0),
                       lambda f: f['runs'][0].update(empty_dependency_caches=False),
                       lambda f: f['runs'][0].update(concurrent_placements=2)):
            data = receipt(); mutate(data['fixtures']['city'])
            with self.assertRaises(ValueError): self.check(data)

    def test_invalid_measurements_and_hard_responsiveness_limits_fail(self):
        for value in (None, True, '1', -1, float('nan'), float('inf'), 10**400):
            data = receipt(); data['fixtures']['city']['runs'][0]['capture_s'] = value
            with self.assertRaises(ValueError): self.check(data)
        for key, value in (('ack_ms', 100.01), ('max_main_thread_stall_ms', 100.01),
                           ('min_fps', 29.99), ('server_hz', 19.99), ('tick_p95_ms', 50)):
            data = receipt(); data['fixtures']['city']['runs'][0][key] = value
            with self.assertRaises(ValueError): self.check(data)

    def test_percentile_boundary_and_upload(self):
        data = receipt(); runs = data['fixtures']['city']['runs']
        for run in runs: run['capture_s'] = 5
        runs[-1]['capture_s'] = 10
        self.check(data)  # One worst outlier is outside nearest-rank p95 for 20 samples.
        runs[-2]['capture_s'] = 10
        with self.assertRaises(ValueError): self.check(data)
        data = receipt(); data['fixtures']['maximum']['runs'][0]['upload_s'] = 45.01
        with self.assertRaises(ValueError): self.check(data)

    def test_cli_rejects_duplicate_fields_and_component_report(self):
        script = Path(checker.__file__)
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2] / 'target') as folder:
            path = Path(folder) / 'receipt.json'
            path.write_text(json.dumps(receipt()))
            result = subprocess.run([sys.executable, str(script), str(path), '--head', HEAD,
                                     '--environment-id', 'synthetic'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode)
            self.assertEqual('passed', json.loads(result.stdout)['receipt_budgets'])
            for raw in ('{"schema":1,"schema":1}', json.dumps({'scope': 'CPU component only'})):
                path.write_text(raw)
                result = subprocess.run([sys.executable, str(script), str(path), '--head', HEAD,
                                         '--environment-id', 'synthetic'], capture_output=True, text=True)
                self.assertEqual(1, result.returncode)
                self.assertIn('NOT QUALIFIED:', result.stderr)
                self.assertEqual('', result.stdout)


if __name__ == '__main__':
    unittest.main()
