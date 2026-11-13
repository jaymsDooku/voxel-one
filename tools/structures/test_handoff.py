#!/usr/bin/env python3
import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from verify_handoff import ROOT, digest, safe_member, verify


class HandoffTest(unittest.TestCase):
    def test_allowlist(self):
        self.assertTrue(safe_member('tools/structures/run_probe.py'))
        for name in ('/tools/structures/run_probe.py', '../accounts.db', 'target/runtime.log',
                     'tools//structures/run_probe.py', 'tools/structures/../config.py', '.env'):
            self.assertFalse(safe_member(name), name)

    def exercise(self, mode):
        folder = ROOT / 'target/structure-handoff-tests'
        folder.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=folder) as temp:
            root = Path(temp)
            (root / 'pom.xml').write_bytes(b'actual')
            name = 'tools/structures/sample.py'
            data = b'fixture only'
            expected = dict(members={name: dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())},
                            required_checkout_source_sha256={'pom.xml': digest(root / 'pom.xml')})
            if mode == 'changed_source':
                expected['required_checkout_source_sha256']['pom.xml'] = '0' * 64
            archive = root / 'handoff.tar'
            with tarfile.open(archive, 'w', format=tarfile.USTAR_FORMAT) as output:
                raw = json.dumps(expected).encode()
                header = tarfile.TarInfo('HANDOFF.json')
                header.size = len(raw)
                output.addfile(header, io.BytesIO(raw))
                if mode != 'missing':
                    info = tarfile.TarInfo(name)
                    info.size = len(data)
                    if mode == 'symlink':
                        info.type = tarfile.SYMTYPE
                        info.linkname = '../outside'
                        info.size = 0
                        output.addfile(info)
                    else:
                        output.addfile(info, io.BytesIO(b'x' * len(data) if mode == 'corrupt' else data))
            manifest = root / 'manifest.json'
            external = dict(archive=dict(bytes=archive.stat().st_size, sha256=digest(archive)))
            if mode == 'wrong_archive_hash':
                external['archive']['sha256'] = '0' * 64
            manifest.write_text(json.dumps(external))
            return verify(archive, manifest, root)

    def test_valid_bundle(self):
        result = self.exercise('valid')
        self.assertEqual(1, result['verified_members'])
        self.assertEqual(1, result['verified_checkout_sources'])

    def test_corrupted_member(self):
        with self.assertRaisesRegex(ValueError, 'Member checksum'):
            self.exercise('corrupt')

    def test_archive_hash_mismatch(self):
        with self.assertRaisesRegex(ValueError, 'Archive checksum'):
            self.exercise('wrong_archive_hash')

    def test_symlink_member(self):
        with self.assertRaisesRegex(ValueError, 'nonregular'):
            self.exercise('symlink')

    def test_missing_member(self):
        with self.assertRaisesRegex(ValueError, 'Missing member'):
            self.exercise('missing')

    def test_source_mismatch(self):
        with self.assertRaisesRegex(ValueError, 'Checkout source checksum'):
            self.exercise('changed_source')


if __name__ == '__main__':
    unittest.main()
