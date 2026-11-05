"""Exercise the real CLI before browser startup; failures must invalidate old passes."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


@unittest.skipIf(os.name == 'nt', 'Service-view CLI uses POSIX hidden stdin on the Linux QA host')
class HostedWebEvidenceTest(unittest.TestCase):
    def test_pre_browser_failures_clear_both_old_receipts_without_echo(self):
        script = Path(__file__).with_name('hosted-web-playtest.py').resolve()
        temporary = Path('target/hub-tmp')
        temporary.mkdir(parents=True, exist_ok=True)
        # None reaches a network request: invalid JSON, invalid origin, missing token.
        inputs = ['{"token":"synthetic-private-marker",',
            json.dumps({'origin': 'https://invalid.example', 'token': 'synthetic-private-marker'}),
            json.dumps({'origin': 'https://voxel-one.jamesleaver1.chatgpt.site'})]
        for payload in inputs:
            with self.subTest(payload_kind=inputs.index(payload)), tempfile.TemporaryDirectory(dir=temporary) as directory:
                evidence = Path(directory)/'dashboard/evidence'
                evidence.mkdir(parents=True)
                names = ('hub-hosted-web-assets.json', 'hub-hosted-web-playtest.json')
                for name in names:
                    (evidence/name).write_text(json.dumps({'passed': True, 'observed': 'old run'}))
                result = subprocess.run([sys.executable, str(script)], input=payload+'\n',
                    text=True, capture_output=True, timeout=5, cwd=directory)
                self.assertEqual(1, result.returncode)
                self.assertNotIn('synthetic-private-marker', result.stdout+result.stderr)
                for name in names:
                    receipt = json.loads((evidence/name).read_text())
                    self.assertIs(receipt['passed'], False)
                    self.assertNotIn('old run', receipt['observed'])
                self.assertFalse((evidence/'hub-hosted-space-invaders.png').exists())


if __name__ == '__main__':
    unittest.main()
