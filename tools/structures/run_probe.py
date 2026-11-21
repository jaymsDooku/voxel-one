#!/usr/bin/env python3
"""Run production-mesher prerequisite probes; never issue a qualification pass."""
import argparse
import hashlib
import json
import math
import platform
import os
import statistics
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def summarize(values):
    ordered = sorted(values)
    return dict(median=statistics.median(ordered),
                p95=ordered[math.ceil(.95 * len(ordered)) - 1], worst=ordered[-1])


def validate_trials(trials, runs):
    expected = {(cells, run) for cells in (1, 8, 64, 512) for run in range(1, runs + 1)}
    actual = {(trial['cells'], trial['run']) for trial in trials}
    if len(trials) != len(expected) or actual != expected:
        raise ValueError('Missing or duplicate probe trials')
    for trial in trials:
        if (trial['quads'] != trial['cells'] * 12
                or trial['mesh_array_bytes'] != trial['quads'] * 216
                or not math.isfinite(trial['elapsed_ms']) or trial['elapsed_ms'] <= 0):
            raise ValueError('Invalid probe measurement')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runs', type=int, default=20)
    parser.add_argument('--label', required=True, help='Actual execution environment; not a certification')
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    parser.add_argument('--classpath', default='target/classes')
    parser.add_argument('--xms', default='256m')
    parser.add_argument('--xmx', default='1g')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/structure-probe')
    args = parser.parse_args()
    if not 1 <= args.runs <= 100:
        parser.error('runs 1..100')
    args.output.mkdir(parents=True, exist_ok=True)
    classes = args.output / 'classes'
    classes.mkdir(exist_ok=True)
    source = ROOT / 'tools/structures/PrerequisiteProbe.java'
    subprocess.run([args.javac, '-cp', args.classpath, '-d', str(classes), str(source)], check=True, cwd=ROOT)
    command = [args.java, f'-Xms{args.xms}', f'-Xmx{args.xmx}', '-XX:+UseG1GC',
               '-Djava.io.tmpdir=' + str(args.output.resolve()), '-cp',
               str(classes) + ':' + args.classpath, 'PrerequisiteProbe', str(args.runs)]
    lines = args.output / 'trials.jsonl'
    # Only synthetic probe output is retained; no player runtime/account data.
    with lines.open('w') as output:
        subprocess.run(command, stdout=output, check=True, cwd=ROOT, timeout=1800)
    trials = [json.loads(line) for line in lines.read_text().splitlines()]
    validate_trials(trials, args.runs)
    groups = {}
    for cells in (1, 8, 64, 512):
        sample = [v for v in trials if v['cells'] == cells]
        groups[str(cells)] = dict(elapsed_ms=summarize([v['elapsed_ms'] for v in sample]),
                                 quads=sample[0]['quads'], mesh_array_bytes=sample[0]['mesh_array_bytes'],
                                 heap_pool_peak_sum_bytes=max(v['heap_pool_peak_sum_bytes'] for v in sample),
                                 process_peak_rss_kib=max(v['process_peak_rss_kib'] for v in sample))
    report = dict(schema=1, environment_label=args.label, qualification='NOT QUALIFIED',
                  scope='Existing CPU chunk-mesher primitive only; no complete-fixture or instance-rendering benchmark',
                  environment=dict(os=platform.system(), kernel=platform.release(), architecture=platform.machine(),
                                   os_release=platform.freedesktop_os_release().get('PRETTY_NAME', 'unknown'),
                                   logical_cpus=os.cpu_count(), affinity_cpus=len(os.sched_getaffinity(0)),
                                   cpu_model=next((line.split(':', 1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines()
                                                   if line.startswith('model name')), 'unknown'),
                                   java=subprocess.run([args.java, '-version'], capture_output=True, text=True, check=True).stderr.strip()),
                  command=command, runs_per_density=args.runs, warmup_per_density=3,
                  game_head=subprocess.run(['git', 'rev-parse', 'HEAD'], capture_output=True, text=True, check=True, cwd=ROOT).stdout.strip(),
                  production_source_sha256={name: hashlib.sha256((ROOT / 'src/main/java/dev/jayms' / name).read_bytes()).hexdigest()
                                            for name in ('MeshDataGenerator.java', 'Chunk.java', 'MeshData.java')},
                  source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                  trial_sha256=hashlib.sha256(lines.read_bytes()).hexdigest(), trials=trials, summary=groups,
                  peak_memory_note='Heap pool peaks are summed (conservative, not simultaneous). RSS is process lifetime high-water mark; -1 means unavailable. Excludes GPU memory.',
                  timing_note='Fixture setup and explicit pre-trial GC excluded. Existing mesher scratch is warmed. No frame, tick, network, durability or cold-load measurement.',
                  regression='Empty chunk, solid-chunk six-face merging, and interrupted-worker cancellation passed',
                  pending='Reference-target execution; full corpus pipeline; instance complexity; physical GPU; multi-host synchronization; supported limits')
    (args.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Playtest: production CPU mesher probe, empty/solid regression and cancellation passed; NOT QUALIFIED.')
    print(json.dumps(groups, sort_keys=True))


if __name__ == '__main__':
    main()
