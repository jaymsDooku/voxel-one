"""Exercise the production decoder gate; synthetic packages are not qualification."""
import gzip
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


def package():
    name = b'CLI regression'
    header = struct.pack('>IIH', 0x56535452, 2, len(name)) + name
    header += struct.pack('>6i2i', 1, 1, 1, 0, 0, 0, 0, 1)
    cell = struct.pack('>HBHHhBBI', 0, 0, 0, 0, -1, 16, 1, 0xff000003)
    return gzip.compress(header + cell, mtime=0)


class AdmissionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = tempfile.TemporaryDirectory(dir=ROOT / 'target')
        cls.addClassCleanup(cls.folder.cleanup)
        cls.work = Path(cls.folder.name)
        subprocess.run(['javac', '--release', '17', '-cp', str(ROOT / 'target/classes'),
                        '-d', str(cls.work), str(ROOT / 'tools/structures/CheckAdmission.java')],
                       check=True, capture_output=True, text=True)

    def run_cli(self, path, digest):
        result = subprocess.run(['java', '-Xmx512m', '-cp', os.pathsep.join(
            (str(ROOT / 'target/classes'), str(self.work))), 'CheckAdmission', str(path), digest],
            capture_output=True, text=True, timeout=30)
        self.assertEqual('', result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual('NOT QUALIFIED', report['qualification'])
        self.assertNotIn(str(path), result.stdout)
        return result.returncode, report

    def test_valid_package_admitted_without_claiming_qualification(self):
        path = self.work / 'small.vstr'
        raw = package(); path.write_bytes(raw)
        code, report = self.run_cli(path, hashlib.sha256(raw).hexdigest())
        self.assertEqual(0, code)
        self.assertTrue(report['admitted'])
        self.assertTrue(report['identity_verified'])
        self.assertEqual((1, 0, 1, 0), tuple(report[k] for k in ('cells', 'models', 'geometry_leaves', 'instances')))

    def test_malformed_and_bad_checksum_are_decoder_rejections(self):
        path = self.work / 'malformed.vstr'
        for raw in (b'garbage', package()[:-4], package()[:-8] + b'\0' * 8):
            path.write_bytes(raw)
            code, report = self.run_cli(path, hashlib.sha256(raw).hexdigest())
            self.assertEqual(2, code)
            self.assertFalse(report['admitted'])
            self.assertTrue(report['identity_verified'])
            self.assertEqual('decoder_rejected', report['reason'])

    def test_production_geometry_cap_is_reported_as_first_rejection(self):
        # Spatially ordered, nonempty cells exceed the current development cap.
        # The early rejection does not certify the rest of a rejected package.
        name = b'Geometry cap'
        raw = struct.pack('>IIH', 0x56535452, 2, len(name)) + name
        raw += struct.pack('>6i2i', 1024, 256, 1024, 0, 0, 0, 0, 131073)
        cells = bytearray()
        for index in range(131073):
            section, local = divmod(index, 4096)
            cells += struct.pack('>HBHHhBBI', section, 0, 0, local, -1, 16, 1, 0xff000003)
        raw = gzip.compress(raw + cells, mtime=0)
        path = self.work / 'geometry-cap.vstr'; path.write_bytes(raw)
        code, report = self.run_cli(path, hashlib.sha256(raw).hexdigest())
        self.assertEqual(2, code)
        self.assertTrue(report['identity_verified'])
        self.assertEqual('geometry_cap', report['reason'])

    def test_wrong_identity_and_missing_input_are_not_admission_results(self):
        path = self.work / 'identity.vstr'; path.write_bytes(package())
        for candidate, digest in ((path, '0' * 64), (path, 'not-a-hash'),
                                  (self.work / 'private-missing.vstr', '0' * 64)):
            code, report = self.run_cli(candidate, digest)
            self.assertEqual(1, code)
            self.assertEqual('input_error', report['status'])
            self.assertFalse(report['identity_verified'])

    def test_oversized_file_rejected_before_hash_or_decode(self):
        path = self.work / 'oversized.vstr'
        with path.open('wb') as stream: stream.truncate(64 * 1024 * 1024 + 1)
        code, report = self.run_cli(path, '0' * 64)
        self.assertEqual(2, code)
        self.assertEqual('compressed_limit', report['reason'])
        self.assertFalse(report['identity_verified'])


if __name__ == '__main__':
    unittest.main()
