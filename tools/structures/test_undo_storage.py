"""Run candidate storage checks only; does not change or qualify game undo."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class UndoStorageTest(unittest.TestCase):
    def test_diagnostic_candidate_invariants(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'target') as folder:
            sources = sorted((ROOT / 'tools/structures/undo-prototype').glob('*.java'))
            sources += [ROOT / 'tools/structures/UndoImageProbe.java', ROOT / 'tools/structures/UndoLiveRetentionProbe.java']
            subprocess.run(['javac', '--release', '17', '-cp', str(ROOT / 'target/classes'),
                            '-d', folder, *map(str, sources)], check=True, capture_output=True, text=True)
            result = subprocess.run(['java', '-Xmx128m', '-cp', os.pathsep.join((folder, str(ROOT / 'target/classes'))),
                                     'dev.jayms.net.structure.UndoPrototypeChecks'],
                                    capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode)
            self.assertEqual('', result.stderr)
            self.assertIn('four packed-undo diagnostic groups passed', result.stdout)
            self.assertIn('game undo remains unchanged; NOT QUALIFIED', result.stdout)


if __name__ == '__main__':
    unittest.main()
