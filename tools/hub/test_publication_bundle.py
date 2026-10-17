"""Exercise generation fencing and delivery checks through a synthetic HTTP service."""
import http.server
import json
from pathlib import Path
import subprocess
import sys
import socketserver
import tempfile
import threading
import unittest
import urllib.request
import zipfile

import publication_bundle as publication


class PublicationTest(unittest.TestCase):
    def setUp(self):
        Path('target/hub-tmp').mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir='target/hub-tmp')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
        self.native = self.root / 'native.json'; self.web = self.root / 'web.json'; self.jar = self.root / 'launcher.jar'
        self.native.write_text(json.dumps({'tested': True, 'published': True, 'source': 'a'*40, 'build': 'playtest-1', 'notes': 'Synthetic tested native receipt'}))
        self.web.write_text(json.dumps({'tested': True, 'published': True, 'source': 'b'*40, 'build': 'web-1', 'notes': 'Synthetic published web receipt', 'url': 'https://voxel-one.jamesleaver1.chatgpt.site/games/space-invaders/'}))
        with zipfile.ZipFile(self.jar, 'w') as jar:
            jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nMain-Class: dev.jayms.launcher.Launcher\n')
            jar.writestr('dev/jayms/launcher/Launcher.class', b'synthetic test artifact')

    def prepare(self, output=None, **overrides):
        values = dict(native=self.native, web=self.web, jar=self.jar, source=self.source, tag='hub-preview-1', expected_head='absent', output=output or self.root/'bundle')
        values.update(overrides)
        return publication.prepare(**values)

    def test_prepare_fences_generation_source_and_destination(self):
        for args in ({'source': '0'*40}, {'tag': 'latest'}, {'tag': 'hub-preview-65536'}, {'tag': 'hub-preview-0001'}, {'tag': 'hub-preview-../2'}, {'expected_head': 'master'}):
            with self.assertRaises(ValueError): self.prepare(**args)
        script = Path(__file__).resolve().with_name('publication_bundle.py')
        attempt = subprocess.run([sys.executable, str(script), 'prepare',
            '--native-receipt', str(self.native.resolve()), '--web-receipt', str(self.web.resolve()),
            '--jar', str(self.jar.resolve()), '--source-sha', self.source, '--tag', 'hub-preview-1',
            '--expected-catalog-head', 'absent', '--output', str((self.root/'nested').resolve())],
            cwd=self.root, capture_output=True, text=True)
        self.assertNotEqual(0, attempt.returncode)
        self.assertIn('Run publication preparation from the checkout root', attempt.stderr)
        self.assertFalse((self.root/'bundle').exists())
        plan = self.prepare()
        self.assertEqual('experience-catalog', plan['destinationBranch'])
        self.assertEqual('absent', plan['expectedDestinationHead'])
        self.assertFalse(plan['stableRolloutQualified'])
        self.assertIn('/hub-preview-1/voxel-one-shell.jar', (self.root/'bundle/shell.properties').read_text())
        with self.assertRaises(ValueError): self.prepare()

    def test_public_delivery_mismatch_offline_and_last_bundle_preservation(self):
        self.prepare(); bundle = self.root/'bundle'
        before = {p.name: p.read_bytes() for p in bundle.iterdir()}
        payload = dict(before)
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                name = self.path.rsplit('/', 1)[-1]
                if name not in payload:
                    self.send_error(404); return
                data = payload[name]; self.send_response(200); self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)
            def log_message(self, *_): pass
        class LoopbackServer(http.server.ThreadingHTTPServer):
            def server_bind(self):
                # No hostname discovery or platform local-network permission flow.
                socketserver.TCPServer.server_bind(self)
                self.server_name = 'localhost'
                self.server_port = self.server_address[1]
        server = LoopbackServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        def reader(url, maximum):
            name = url.rsplit('/', 1)[-1]
            with urllib.request.urlopen('http://127.0.0.1:%d/%s' % (server.server_port, name), timeout=2) as response:
                body = response.read(maximum+1)
                if len(body) > maximum: raise ValueError('Oversized')
                return body
        try:
            self.assertTrue(publication.verify(bundle, self.root/'good.json', reader))
            payload['voxel-one-shell.jar'] = b'corrupt published asset'
            self.assertFalse(publication.verify(bundle, self.root/'corrupt.json', reader))
            del payload['catalog.properties']
            self.assertFalse(publication.verify(bundle, self.root/'missing.json', reader))
            checks = json.loads((self.root/'missing.json').read_text())['checks']
            self.assertEqual(404, checks[0]['httpStatus'])
            self.assertEqual(before, {p.name: p.read_bytes() for p in bundle.iterdir()})
        finally:
            server.shutdown(); server.server_close(); thread.join(timeout=2)

    def test_web_only_change_preserves_native_compatibility_and_changes_catalog_version(self):
        self.prepare(); first = (self.root/'bundle/catalog.properties').read_text()
        receipt = json.loads(self.web.read_text()); receipt['build'] = 'web-2'; self.web.write_text(json.dumps(receipt))
        self.prepare(output=self.root/'next')
        second = (self.root/'next/catalog.properties').read_text()
        native = lambda text: [line for line in text.splitlines() if line.startswith(('city.', 'sandbox.', 'engine-editor.'))]
        self.assertEqual(native(first), native(second))
        self.assertNotEqual(first.split('version=',1)[1].splitlines()[0], second.split('version=',1)[1].splitlines()[0])


if __name__ == '__main__': unittest.main()
