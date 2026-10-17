#!/usr/bin/env python3
"""Assemble public catalog metadata from separate, already-published receipts.
Writes a file only. The release/website owner promotes that file after validation.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re


def assemble(native_receipt, web_receipt, output):
    native = json.loads(Path(native_receipt).read_text())
    web = json.loads(Path(web_receipt).read_text())
    for receipt in (native, web):
        if receipt.get('tested') is not True or receipt.get('published') is not True:
            raise ValueError('Promotion requires tested and published receipts')
        if not re.fullmatch('[0-9a-f]{40}', receipt.get('source', '')):
            raise ValueError('A full public source revision is required')
        if not re.fullmatch('[A-Za-z0-9._-]{1,80}', receipt.get('build', '')):
            raise ValueError('Invalid build ID')
        if any(c in receipt.get('notes', '') for c in '\r\n\\') or not 1 <= len(receipt.get('notes', '')) <= 4000:
            raise ValueError('Use single-line public release notes')
    if not re.fullmatch(r'playtest-[1-9][0-9]{0,9}', native['build']):
        raise ValueError('Native build must name an immutable playtest release')
    if not re.fullmatch(r'https://voxel-one\.jamesleaver1\.chatgpt\.site/games/space-invaders/(?:\?build=[0-9a-f]{20})?', web.get('url', '')):
        raise ValueError('Unexpected published web URL')
    template = Path('src/main/resources/dev/jayms/launcher/catalog.properties').read_text()
    lines = []
    for line in template.splitlines():
        key, sep, value = line.partition('=')
        identity, _, field = key.partition('.')
        receipt = web if identity == 'space-invaders' else native
        if identity == 'space-invaders' and field == 'target':
            value = web['url']
        if field == 'client-sources':
            value = native['source']
        if field in ('source', 'build', 'notes'):
            value = receipt[field]
        if key == 'version':
            value = hashlib.sha256(json.dumps({'native': native, 'web': web, 'template': template}, sort_keys=True, separators=(',', ':')).encode()).hexdigest()[:20]
        lines.append(key+sep+value)
    text = '\n'.join(lines)+'\n'
    text = ''.join(c if ord(c) < 128 else ''.join('\\u%04x' % int.from_bytes(c.encode('utf-16-be')[i:i+2], 'big') for i in range(0, len(c.encode('utf-16-be')), 2)) for c in text)
    Path(output).write_text(text, encoding='ascii')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--native-receipt', required=True)
    parser.add_argument('--web-receipt', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    assemble(args.native_receipt, args.web_receipt, args.output)
