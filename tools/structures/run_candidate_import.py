#!/usr/bin/env python3
"""Isolated decode experiment. Does not alter production source, classes, worlds or caps."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runs', type=int, choices=range(1, 21), default=20)
    parser.add_argument('--fixture', choices=('city', 'maximum', 'both'), default='both')
    args = parser.parse_args()
    target = ROOT / 'target/structure-candidate-import'
    target.mkdir(parents=True, exist_ok=True)
    folder = Path(tempfile.mkdtemp(prefix='run-', dir=target))
    production = {str(p.relative_to(ROOT)): digest(p) for p in sorted((ROOT / 'src/main/java').rglob('*.java'))}
    class_files = {str(p.relative_to(ROOT)): digest(p) for p in sorted((ROOT / 'target/classes').rglob('*.class'))}
    source = ROOT / 'src/main/java/dev/jayms/net/structure/StructurePackage.java'
    text = source.read_text()
    changes = {
        'MAX_LEAVES=131_072': 'MAX_LEAVES=8_388_608',
        'MAX_DEPENDENCY_QUADS=1_048_576': 'MAX_DEPENDENCY_QUADS=2_097_152',
        'MAX_INSTANCE_QUADS=1_048_576': 'MAX_INSTANCE_QUADS=67_108_864',
    }
    for old, new in changes.items():
        if text.count(old) != 1:
            raise SystemExit('Production source differs from the reviewed experiment input')
        text = text.replace(old, new)
    candidate = folder / 'StructurePackage.java'
    candidate.write_text(text)
    classes = folder / 'classes'
    baseline = folder / 'baseline'
    classes.mkdir()
    baseline.mkdir()
    report = dict(scope='Isolated candidate decode only; not production admission or qualification',
                  runs=args.runs, production_source_sha256=production, production_class_sha256=class_files,
                  candidate_source_sha256=digest(candidate), replacements=changes,
                  heap='1 GiB', sampling_interval_ms=5, output=str(folder.relative_to(ROOT)), fixtures={})
    trusted = json.loads((ROOT / 'dashboard/evidence/structure-model-snapshots.json').read_text())['benchmark']['fixture_sha256']
    try:
        subprocess.run(['javac', '--release', '17', '-cp', 'target/classes', '-d', str(baseline),
                        'tools/structures/CheckAdmission.java'], cwd=ROOT, check=True)
        subprocess.run(['javac', '--release', '17', '-cp', 'target/classes', '-d', str(classes), str(candidate),
                        'src/main/java/dev/jayms/net/structure/StructureUniformCells.java',
                        'tools/structures/CandidateImportBenchmark.java'], cwd=ROOT, check=True)
        for fixture in ('city', 'maximum') if args.fixture == 'both' else (args.fixture,):
            path = ROOT / f'target/structure-fixtures/{fixture}-v2.vstr'
            if digest(path) != trusted[fixture]:
                raise SystemExit('Fixture identity differs from the recorded corpus')
            control = subprocess.run(['java', '-Xmx1g', '-cp', f'{baseline}:target/classes', 'CheckAdmission',
                                      str(path), trusted[fixture]], cwd=ROOT, capture_output=True, text=True)
            if control.returncode != 2:
                raise SystemExit('Production control did not reject the analysis-only fixture')
            report['fixtures'][fixture] = dict(production_control=json.loads(control.stdout))
            command = ['java', '-Xmx1g', '-cp', f'{classes}:target/classes', 'CandidateImportBenchmark',
                       fixture, str(path), trusted[fixture], str(args.runs), str(folder)]
            with (folder / f'{fixture}-private.stdout').open('w') as out:
                run = subprocess.run(command, cwd=ROOT, stdout=out, stderr=out, timeout=1800)
            report['fixtures'][fixture].update(exit_code=run.returncode,
                                              command=[x.replace(str(ROOT) + '/', '') for x in command])
            result = folder / f'{fixture}-result.json'
            if result.exists():
                report['fixtures'][fixture]['result'] = json.loads(result.read_text())
            samples = folder / f'{fixture}-samples.jsonl'
            if samples.exists():
                report['fixtures'][fixture]['samples'] = [json.loads(x) for x in samples.read_text().splitlines()]
            if run.returncode:
                report['status'] = 'failed'
                break
        else:
            report['status'] = 'passed'
    finally:
        report['production_source_unchanged'] = all(digest(ROOT / p) == h for p, h in production.items())
        report['production_classes_unchanged'] = all(digest(ROOT / p) == h for p, h in class_files.items())
        (folder / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(dict(output=report['output'], status=report.get('status', 'incomplete'),
                              production_source_unchanged=report['production_source_unchanged'],
                              production_classes_unchanged=report['production_classes_unchanged'])))
    return 0 if report.get('status') == 'passed' and report['production_source_unchanged'] and report['production_classes_unchanged'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
