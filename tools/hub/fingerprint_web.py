#!/usr/bin/env python3
"""Build a fresh, fingerprinted web experience in a separate output directory.
The publication controller deploys this directory and supplies a tested receipt.
"""
import argparse
import hashlib
from pathlib import Path
import re
import shutil


def build(source, output):
    source, output = Path(source).resolve(), Path(output).resolve()
    if source == output or source in output.parents or output in source.parents:
        raise ValueError('Use separate source and output directories')
    allowed = {'.html', '.css', '.mjs', '.png', '.jpg', '.jpeg', '.webp', '.svg', '.wav', '.ogg', '.mp3', '.woff', '.woff2'}
    files = sorted(p for p in source.rglob('*') if p.is_file() and p.suffix in allowed and not p.name.endswith('.test.mjs'))
    if not files or len(files) > 512:
        raise ValueError('Invalid web asset set')
    digest = hashlib.sha256()
    for file in files:
        if file.is_symlink() or source not in file.resolve().parents or file.stat().st_size > 8*1024*1024:
            raise ValueError('Invalid web asset')
        digest.update(file.relative_to(source).as_posix().encode()+b'\0'+file.read_bytes())
    version = digest.hexdigest()[:20]
    names = {}
    for file in files:
        relative = file.relative_to(source)
        if file.suffix in ('.mjs', '.css'):
            names[relative.as_posix()] = relative.with_name(file.stem+'-'+version+file.suffix).as_posix()
    output.mkdir(parents=True, exist_ok=True)
    for file in files:
        relative = file.relative_to(source)
        destination = output/names.get(relative.as_posix(), relative.as_posix())
        destination.parent.mkdir(parents=True, exist_ok=True)
        if file.suffix in ('.mjs', '.html', '.css'):
            text = file.read_text()
            def rewrite(match):
                quote, path = match.group(1), match.group(2)
                resolved = (file.parent/path).resolve()
                if source not in resolved.parents:
                    raise ValueError('Module reference leaves the experience')
                key = resolved.relative_to(source).as_posix()
                if key not in names:
                    return match.group(0)
                prefix = path.rpartition('/')[0]
                replacement = (prefix+'/' if '/' in path else '')+Path(names[key]).name
                return quote+replacement+quote
            text = re.sub(r'''(["'])(\.?/?[^"'\s<>]*\.(?:mjs|css))\1''', rewrite, text)
            destination.write_text(text)
        else:
            shutil.copy2(file, destination)
    (output/'build.txt').write_text(version+'\n')
    return version

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--source',required=True);p.add_argument('--output',required=True);a=p.parse_args()
    print(build(a.source,a.output))
