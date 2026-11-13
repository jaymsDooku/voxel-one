#!/usr/bin/env python3
"""Check a measured full-fixture receipt. This tool does not run or certify benchmarks."""
import argparse
import json
import math
import re
from pathlib import Path

TIMINGS = {
    'capture_s': (5, 15), 'export_s': (5, 15), 'preview_initial_s': (.5, 1),
    'preview_move_s': (.1, .1), 'preview_rotate_s': (.1, .1),
    'placement_s': (5, 15), 'durable_save_s': (5, 15),
    'cold_load_s': (8, 20), 'sync_all_clients_s': (10, 30),
}
MAX_REPORT_BYTES = 16 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def number(value, field, positive=False):
    require(type(value) in (int, float) and 0 <= value <= 2**63 - 1 and math.isfinite(value)
            and (value > 0 if positive else value >= 0), 'Invalid ' + field)
    return value


def text(value, field):
    require(isinstance(value, str) and bool(value.strip()), 'Missing ' + field)
    return value


def summary(values):
    values = sorted(values)
    n = len(values)
    return {'median': (values[(n - 1) // 2] + values[n // 2]) / 2,
            'p95': values[math.ceil(.95 * n) - 1], 'worst': values[-1]}


def check(receipt, head, environment_id):
    require(isinstance(receipt, dict), 'Receipt must be an object')
    require(re.fullmatch('[0-9a-f]{40}', head) is not None, 'Expected exact source SHA')
    require(receipt.get('schema') == 1 and type(receipt.get('schema')) is int, 'Unsupported schema')
    require(receipt.get('source_head') == head, 'Source head mismatch')
    env = receipt.get('environment')
    require(isinstance(env, dict), 'Missing environment')
    require(env.get('reference_id') == environment_id and bool(environment_id.strip()), 'Reference environment mismatch')
    for field in ('client_cpu', 'client_ram', 'gpu', 'gpu_driver', 'client_os',
                  'server_cpu', 'server_ram', 'server_os', 'network_shape', 'network_measurement'):
        text(env.get(field), field)
    require(env.get('physical_gpu') is True, 'Physical GPU qualification pending')
    require(env.get('network_shape_verified') is True, 'Shaped network verification pending')
    fixtures = receipt.get('fixtures')
    require(isinstance(fixtures, dict) and set(fixtures) == {'city', 'maximum'}, 'Both full fixtures required')
    result = {}
    for index, name in enumerate(('city', 'maximum')):
        fixture = fixtures[name]
        require(isinstance(fixture, dict), 'Invalid fixture ' + name)
        require(fixture.get('admitted') is True, name + ' admission pending')
        require(re.fullmatch('[0-9a-f]{64}', str(fixture.get('package_sha256', ''))) is not None, name + ' package identity missing')
        require(fixture.get('dimensions') == ([512, 128, 512] if name == 'city' else [1024, 256, 1024]), name + ' dimensions mismatch')
        for field in ('occupied_cells', 'models', 'instances', 'compressed_bytes', 'decoded_bytes', 'geometry_leaves'):
            require(type(fixture.get(field)) is int and fixture[field] > 0, name + ' invalid ' + field)
        require(fixture['compressed_bytes'] <= 64 * 1024 * 1024, name + ' compressed limit')
        require(fixture['decoded_bytes'] <= 512 * 1024 * 1024, name + ' decoded limit')
        if name == 'city':
            require(900_000 <= fixture['occupied_cells'] <= 1_100_000 and fixture['models'] == 256
                    and fixture['instances'] >= 10_000 and 16 * 1024 * 1024 <= fixture['compressed_bytes'] <= 32 * 1024 * 1024,
                    'City fixture does not meet corpus requirements')
            require(type(fixture.get('buildings')) is int and fixture['buildings'] >= 100, 'City buildings missing')
            for field in ('roads', 'pylons', 'fractional_geometry'):
                require(fixture.get(field) is True, 'City ' + field + ' missing')
        else:
            require(fixture['occupied_cells'] == 4_000_000 and fixture['models'] == 512
                    and fixture.get('fragmented') is True, 'Maximum fixture incomplete')
        require(fixture['models'] <= 512, 'Dependency limit')
        for field in ('progress_over_1s_verified', 'cancellation_verified', 'nearby_geometry_usable',
                      'durable_save_verified', 'cold_cache_verified', 'upload_excluded_from_placement', 'sync_from_commit_verified'):
            require(fixture.get(field) is True, name + ' missing workflow evidence: ' + field)
        text(fixture.get('evidence'), name + ' evidence')
        runs = fixture.get('runs')
        require(isinstance(runs, list) and 20 <= len(runs) <= 100, name + ' needs 20..100 measured runs')
        ids = set()
        metrics = {key: [] for key in (*TIMINGS, 'upload_s', 'ack_ms', 'max_main_thread_stall_ms',
                    'min_fps', 'server_hz', 'tick_p95_ms', 'frame_p95_ms', 'frame_worst_ms',
                    'peak_memory_bytes', 'traffic_bytes')}
        for run in runs:
            require(isinstance(run, dict), name + ' invalid run')
            run_id = run.get('id')
            require(type(run_id) is int and run_id > 0 and run_id not in ids, name + ' invalid/duplicate run id')
            ids.add(run_id)
            require(run.get('completed') is True and run.get('clients_synchronized') == 32
                    and type(run.get('clients_synchronized')) is int
                    and run.get('rendered_clients') == 32 and type(run.get('rendered_clients')) is int
                    and run.get('empty_dependency_caches') is True and run.get('concurrent_placements') == 1
                    and type(run.get('concurrent_placements')) is int, name + ' run conditions incomplete')
            for key in metrics:
                metrics[key].append(number(run.get(key), name + '.' + key,
                                           key not in ('ack_ms', 'max_main_thread_stall_ms')))
            require(run['ack_ms'] <= 100 and run['max_main_thread_stall_ms'] <= 100
                    and run['min_fps'] >= 30 and run['server_hz'] >= 20 and run['tick_p95_ms'] < 50,
                    name + ' responsiveness/frame/tick budget failed')
            require(run['frame_worst_ms'] >= run['frame_p95_ms'], name + ' inconsistent frame statistics')
        measured = {key: summary(values) for key, values in metrics.items()}
        for key in ('min_fps', 'server_hz'):
            measured[key]['worst'] = min(metrics[key])
        for key, budgets in TIMINGS.items():
            require(measured[key]['p95'] <= budgets[index], name + ' p95 budget failed: ' + key)
        if name == 'maximum':
            require(measured['upload_s']['worst'] <= 45, 'Maximum upload budget failed')
        result[name] = {'runs': len(runs), 'metrics': measured}
    return {'source_head': head, 'reference_environment': environment_id,
            'receipt_budgets': 'passed', 'note': 'Receipt checks only; independent source, media and measurement review remains required.',
            'fixtures': result}


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate JSON field: ' + key)
        result[key] = value
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('receipt', type=Path)
    parser.add_argument('--head', required=True)
    parser.add_argument('--environment-id', required=True)
    args = parser.parse_args()
    try:
        with args.receipt.open('rb') as stream:
            raw = stream.read(MAX_REPORT_BYTES + 1)
        require(len(raw) <= MAX_REPORT_BYTES, 'Receipt exceeds 16 MiB')
        receipt = json.loads(raw, object_pairs_hook=unique_object)
        print(json.dumps(check(receipt, args.head, args.environment_id), indent=2))
    except (ValueError, TypeError, KeyError, OSError, RecursionError) as error:
        parser.exit(1, 'NOT QUALIFIED: ' + str(error) + '\n')


if __name__ == '__main__':
    main()
