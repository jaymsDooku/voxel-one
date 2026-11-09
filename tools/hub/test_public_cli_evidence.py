"""A refused public CLI test run must not retain a previous passing receipt."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class PublicCliEvidenceTest(unittest.TestCase):
    def test_existing_profile_refused_before_network_and_old_pass_invalidated(self):
        script = Path(__file__).with_name('public-cli-playtest.py').resolve()
        scratch = Path('target/hub-tmp'); scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory).resolve()
            executable = root/'synthetic-unused-executable'; executable.write_text('not executed')
            profile = root/'existing-profile'; profile.mkdir()
            report = root/'result.json'; report.write_text('{"passed": true, "observed": "old run"}')
            result = subprocess.run([sys.executable, str(script), '--executable', str(executable),
                '--profile', str(profile), '--output', str(report)], cwd=root,
                capture_output=True, text=True, timeout=5)
            self.assertEqual(1, result.returncode)
            self.assertIn('ValueError', result.stderr)
            self.assertIs(json.loads(report.read_text())['passed'], False)
            self.assertFalse((profile/'.voxel-one').exists())


if __name__ == '__main__':
    unittest.main()
