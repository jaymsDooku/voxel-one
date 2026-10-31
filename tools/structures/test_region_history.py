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
                        'RegionAuthorityPrototype.java', 'RegionAuthorityCheck.java')]
            subprocess.run(['javac', '--release', '17', '-cp', str(CLASSES), '-d', temporary,
                            *map(str, sources)], check=True, capture_output=True, timeout=30)
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

    def test_actual_engine_authority(self):
        rows = self.run_check('RegionAuthorityCheck')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'passed')
        self.assertEqual(rows[0]['rotations'], 4)
        self.assertFalse(rows[0]['production_integration'])
        self.assertFalse(rows[0]['qualification'])


if __name__ == '__main__':
    unittest.main()
