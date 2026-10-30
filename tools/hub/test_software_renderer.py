import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import software_renderer as mesa


class SoftwareRendererTest(unittest.TestCase):
    def setUp(self):
        Path('target').mkdir(exist_ok=True)

    def test_corrupt_cached_archive_is_rejected_before_extraction(self):
        with tempfile.TemporaryDirectory(dir='target') as directory:
            root = Path(directory)
            (root / 'mesa.7z').write_bytes(b'bad')
            with patch.object(mesa, 'SIZE', 3), patch.object(mesa, 'SHA256', '0' * 64):
                with self.assertRaisesRegex(ValueError, 'SHA-256'):
                    mesa.fetch_archive(root)

    def test_runtime_outside_disposable_work_is_rejected(self):
        with tempfile.TemporaryDirectory(dir='target') as directory:
            with self.assertRaisesRegex(ValueError, 'disposable'):
                mesa.prepare(Path(directory) / 'work', Path(directory) / 'java.exe')

    def test_only_local_dlls_copied_and_existing_runtime_preserved(self):
        with tempfile.TemporaryDirectory(dir='target') as directory:
            work = Path(directory).resolve()
            runtime = work / 'runtime/bin/java.exe'
            runtime.parent.mkdir(parents=True)
            runtime.write_bytes(b'synthetic-runtime')

            def extract(command, **kwargs):
                self.assertIn('x64/*.dll', command)
                self.assertEqual(command[1], 'e')
                (work / 'mesa-x64/opengl32.dll').write_bytes(b'synthetic-dll')

            with patch.object(mesa, 'fetch_archive', return_value=work / 'verified.7z'), \
                    patch.object(mesa.shutil, 'which', return_value='7z'), \
                    patch.object(mesa.subprocess, 'run', side_effect=extract):
                result = mesa.prepare(work, runtime)
            self.assertEqual(runtime.read_bytes(), b'synthetic-runtime')
            self.assertEqual((runtime.parent / 'opengl32.dll').read_bytes(), b'synthetic-dll')
            self.assertEqual(result['dllSha256']['opengl32.dll'], hashlib.sha256(b'synthetic-dll').hexdigest())
            # A new extraction must not replace an existing runtime DLL.
            (work / 'mesa-x64/opengl32.dll').unlink()
            (work / 'mesa-x64').rmdir()
            with patch.object(mesa, 'fetch_archive', return_value=work / 'verified.7z'), \
                    patch.object(mesa.shutil, 'which', return_value='7z'), \
                    patch.object(mesa.subprocess, 'run', side_effect=extract):
                with self.assertRaisesRegex(ValueError, 'existing runtime'):
                    mesa.prepare(work, runtime)
            self.assertEqual((runtime.parent / 'opengl32.dll').read_bytes(), b'synthetic-dll')


if __name__ == '__main__':
    unittest.main()
