#!/usr/bin/env python3
"""Malformed intermediate-fixture parser checks; no player importer claim."""
import gzip
import json
from pathlib import Path
import struct
import tempfile
import unittest
import fixtures


class FixtureParserTest(unittest.TestCase):
    def reject(self, data, message):
        folder = fixtures.ROOT / 'target/structure-fixture-tests'
        folder.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=folder) as temporary:
            path = Path(temporary) / 'invalid.gz'
            path.write_bytes(gzip.compress(data, mtime=0))
            with self.assertRaisesRegex(ValueError, message):
                fixtures.verify(path)

    def test_wrong_magic(self):
        self.reject(b'NOTVXBF!', 'Wrong magic')

    def test_header_allocation_bound(self):
        self.reject(fixtures.MAGIC + struct.pack('>I', 4097), 'Header limit')

    def test_truncated_header(self):
        self.reject(fixtures.MAGIC + struct.pack('>I', 12) + b'{}', 'Truncated')

    def test_changed_dimensions_rejected(self):
        header = fixtures.header('city')
        header['bounds'][0] = 1025
        data = json.dumps(header).encode()
        self.reject(fixtures.MAGIC + struct.pack('>I', len(data)) + data, 'Unexpected fixture header')

    def test_unknown_fixture(self):
        data = b'{"fixture":"unknown"}'
        self.reject(fixtures.MAGIC + struct.pack('>I', len(data)) + data, 'Unexpected fixture header')

    def test_truncated_dependencies(self):
        data = json.dumps(fixtures.header('city')).encode()
        self.reject(fixtures.MAGIC + struct.pack('>I', len(data)) + data, 'Truncated')


if __name__ == '__main__':
    unittest.main()
