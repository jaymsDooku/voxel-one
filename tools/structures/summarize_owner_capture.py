"""Bounded summary of the recorded small-fixture diagnostic; never reruns it."""
import json
import math
import statistics
from pathlib import Path

report = Path('dashboard/evidence/structure-owner-capture-benchmark.json')
if report.stat().st_size > 64 * 1024:
    raise ValueError('Report byte budget')
r = json.loads(report.read_text())
assert r['status'] == 'passed' and r['measured_runs_per_path'] == 20
assert r['canonical_payloads_equal'] and r['strict_restart'] and not r['qualification']
for key in ('replay', 'world_cells'):
    assert len(r[key]['runs']) == 20
    for metric in ('capture_ms', 'maximum_owner_pump_ms', 'sampled_heap_used_bytes'):
        assert all(math.isfinite(r[key][metric][k]) and r[key][metric][k] >= 0
                   for k in ('median', 'p95', 'worst'))
        values = sorted(row[metric] for row in r[key]['runs'])
        expected = {'median': statistics.median(values), 'p95': values[18], 'worst': values[19]}
        assert all(abs(r[key][metric][k] - expected[k]) <= 0.00001 for k in expected)
lines = ['Voxel One: actual owner backup capture diagnostic', '',
         f"Fixture: {r['cells']:,} cells / {r['flat_records']:,} flat records",
         '20 runs/path; 1 warmup/path; alternating order; Java heap 512 MiB.',
         'Host details and limits: see report provenance.', '',
         '                         median       p95       worst (ms)']
for key, label, metric in [('replay', 'Replay capture', 'capture_ms'),
                           ('world_cells', 'Cell capture', 'capture_ms'),
                           ('replay', 'Replay max owner pump', 'maximum_owner_pump_ms'),
                           ('world_cells', 'Cell max owner pump', 'maximum_owner_pump_ms')]:
    v = r[key][metric]
    lines.append(f"{label:23} {v['median']:8.2f}  {v['p95']:8.2f}  {v['worst']:8.2f}")
lines += ['', 'Canonical payloads equal; both strict restarts match source cells.',
          'Sampled heap only; not true peak, allocation cost or isolated RSS.',
          f"Cell minus replay: median {r['world_cells']['capture_ms']['median'] - r['replay']['capture_ms']['median']:+.2f} ms; p95 {r['world_cells']['capture_ms']['p95'] - r['replay']['capture_ms']['p95']:+.2f} ms.", '',
          'Not the supplied city/maximum fixture or reference GPU/network environment.',
          'Production backend, 20-run full-fixture and 32-client qualification pending.',
          'No caps raised; rollout stays gated.']
print('\n'.join(lines))
