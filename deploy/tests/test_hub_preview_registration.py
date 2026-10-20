"""Execute the actual pre-checkout workflow fence against a synthetic GitHub API."""
import contextlib
import io
import json
import os
from pathlib import Path
import re
import unittest
from unittest.mock import patch
import urllib.error

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / '.github/workflows/hub-publication-prepare.yml'
SOURCE = 'a' * 40
CATALOG = 'b' * 40
BRANCH = 'git/ref/heads/feature/queue-62383835386364312d346130642d346262652d383736632d393534373062343337356161'


def fence_source():
    text = WORKFLOW.read_text()
    block = text.split('        shell: python\n        run: |\n', 1)[1].split('      - uses:', 1)[0]
    return '\n'.join(line[10:] for line in block.splitlines())


class RegistrationTest(unittest.TestCase):
    def run_fence(self, source=SOURCE, tag='hub-preview-1', expected='absent', remote=None):
        state = {BRANCH: {'object': {'type': 'commit', 'sha': SOURCE}}}
        state.update(remote or {})
        calls = []
        self.calls = calls

        def read(request, timeout):
            prefix = 'https://api.github.com/repos/jaymsDooku/voxel-one/'
            self.assertTrue(request.full_url.startswith(prefix))
            self.assertEqual('GET', request.get_method())
            self.assertEqual(30, timeout)
            key = request.full_url[len(prefix):]
            calls.append(key)
            value = state.get(key)
            if isinstance(value, int):
                raise urllib.error.HTTPError(request.full_url, value, 'Synthetic failure', {}, None)
            if value is None:
                raise urllib.error.HTTPError(request.full_url, 404, 'Absent', {}, None)
            return io.BytesIO(json.dumps(value).encode())

        with patch.dict(os.environ, {'SOURCE_SHA': source, 'PREVIEW_TAG': tag,
                                     'EXPECTED_CATALOG_HEAD': expected, 'GH_READ_TOKEN': 'synthetic'}, clear=True), \
             patch('urllib.request.urlopen', read), contextlib.redirect_stdout(io.StringIO()):
            exec(compile(fence_source(), str(WORKFLOW), 'exec'), {})
        return calls

    def test_first_generation_and_existing_catalog(self):
        self.assertEqual(4, len(self.run_fence()))
        self.assertEqual(4, len(self.run_fence(tag='hub-preview-65535', expected=CATALOG,
            remote={'git/ref/heads/experience-catalog': {'object': {'sha': CATALOG}}})))

    def test_invalid_inputs_rejected_before_network(self):
        for args in ({'source': 'master'}, {'source': 'A'*40}, {'source': SOURCE+'\n'},
                     {'tag': 'hub-preview-0'}, {'tag': 'hub-preview-01'}, {'tag': 'hub-preview-65536'},
                     {'tag': 'hub-preview-1; echo unsafe'}, {'tag': 'latest'}, {'expected': 'master'}):
            with self.subTest(args=args), self.assertRaises(ValueError):
                self.run_fence(**args)
            self.assertEqual([], self.calls)

    def test_stale_source_catalog_and_reused_generation(self):
        for args in ({'source': 'c'*40}, {'expected': CATALOG},
                     {'remote': {'git/ref/heads/experience-catalog': {'object': {'sha': CATALOG}}}},
                     {'remote': {'git/ref/tags/hub-preview-1': {'object': {'sha': SOURCE}}}},
                     {'remote': {'releases/tags/hub-preview-1': {'id': 1}}},
                     {'remote': {BRANCH: {'object': {'type': 'tag', 'sha': SOURCE}}}}):
            with self.subTest(args=args), self.assertRaises(ValueError):
                self.run_fence(**args)

    def test_network_auth_and_service_errors_fail_closed(self):
        for status in (401, 403, 429, 500):
            with self.subTest(status=status), self.assertRaises(urllib.error.HTTPError):
                self.run_fence(remote={'git/ref/heads/experience-catalog': status})
        with self.assertRaises(urllib.error.HTTPError):
            self.run_fence(remote={BRANCH: 404})

    def test_registration_and_preserved_build_contract(self):
        text = WORKFLOW.read_text()
        self.assertIn('  workflow_dispatch:', text)
        self.assertEqual(1, text.count('  contents: read'))
        self.assertNotRegex(text, r':\s*write\b')
        self.assertEqual(2, text.count("github.ref == 'refs/heads/master'"))
        self.assertLess(text.index('Fence controller inputs'), text.index('actions/checkout'))
        self.assertEqual(2, text.count('ref: ${{ inputs.source_sha }}'))
        self.assertEqual(2, text.count('persist-credentials: false'))
        self.assertEqual(2, text.count('test "$(git rev-parse HEAD)" = "$SOURCE_SHA"'))
        self.assertNotIn('"$GITHUB_SHA"', text)
        self.assertIn('needs: prepare', text)
        self.assertNotIn('${{ inputs.', '\n'.join(re.findall(r'        run: \|\n(.*?)(?=      -|\Z)', text, re.S)))
        for check in ('test_publisher.py', 'test_publication_bundle.py', 'UpdaterTest', 'AccountStoreTest',
                      'MultiplayerTest#authenticationRegistrationDuplicateSessionsAndCertificatePin',
                      "plan['sourceKind']=='exact-commit'", "'stableRolloutQualified':False",
                      'ubuntu-24.04', 'windows-2022', 'macos-15-intel', 'macos-15'):
            self.assertIn(check, text)
        # Preserve the reviewed launcher implementation after the source fence.
        self.assertEqual(2, text.count('actions/upload-artifact@v4'))
        self.assertIn('--expected-catalog-head "$EXPECTED_CATALOG_HEAD"', text)


if __name__ == '__main__':
    unittest.main(verbosity=2)
