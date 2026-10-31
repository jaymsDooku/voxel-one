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
    def test_actual_replay_migration(self):
        folder = ROOT / 'target/structure-region-history-tests'
        folder.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=folder) as temporary:
            sources = [ROOT / 'tools/structures' / name for name in
                       ('RegionHistoryPrototype.java', 'RegionHistoryPrototypeCheck.java')]
            subprocess.run(['javac', '--release', '17', '-cp', str(CLASSES), '-d', temporary,
                            *map(str, sources)], check=True, capture_output=True, timeout=30)
            result = subprocess.run(['java', '-Xmx512m', '-cp', os.pathsep.join((temporary, str(CLASSES))),
                                     'RegionHistoryPrototypeCheck'], check=True,
                                    capture_output=True, text=True, timeout=30)
            rows = [json.loads(line) for line in result.stdout.splitlines()]
            self.assertEqual([row['terrain_version'] for row in rows], [1, 2, 3])
            for row in rows:
                self.assertEqual(row['status'], 'passed')
                self.assertFalse(row['qualification'])


if __name__ == '__main__':
    unittest.main()
