"""Pinned, application-local Mesa for Windows validation. Never installs a driver."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import time
import urllib.parse
import urllib.request

URL = 'https://github.com/pal1000/mesa-dist-win/releases/download/26.2.5/mesa3d-26.2.5-release-mingw.7z'
SIZE = 59285313
SHA256 = '2471864068672b82032d8f8cccfb8da8e196b1b06b316b99838b47c35d738a17'
PROVENANCE = 'https://github.com/pal1000/mesa-dist-win/releases/tag/26.2.5'


class Redirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        url = urllib.parse.urlsplit(newurl)
        if (url.scheme != 'https' or url.hostname not in
                {'github.com', 'release-assets.githubusercontent.com', 'objects.githubusercontent.com'}
                or url.username is not None or url.password is not None or url.port not in (None, 443)):
            raise ValueError('Untrusted Mesa download redirect')
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def verify(archive):
    if archive.stat().st_size != SIZE:
        raise ValueError('Mesa archive size mismatch')
    digest = hashlib.sha256()
    with archive.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(chunk)
    if digest.hexdigest() != SHA256:
        raise ValueError('Mesa archive SHA-256 mismatch')


def fetch_archive(directory):
    directory.mkdir(parents=True, exist_ok=True)
    archive = directory / 'mesa.7z'
    if archive.exists():
        verify(archive)
        return archive
    partial = directory / 'mesa.partial'
    deadline = time.monotonic() + 180
    try:
        request = urllib.request.Request(URL, headers={'User-Agent': 'Voxel-One-Hub-Software-Check/1'})
        with urllib.request.build_opener(Redirect()).open(request, timeout=30) as response, partial.open('xb') as output:
            total = 0
            while chunk := response.read(1024 * 1024):
                total += len(chunk)
                if total > SIZE or time.monotonic() > deadline:
                    raise ValueError('Mesa download exceeded its bound')
                output.write(chunk)
        verify(partial)
        partial.replace(archive)
        return archive
    finally:
        partial.unlink(missing_ok=True)


def prepare(work, runtime):
    """The caller supplies its newly built, disposable jpackage runtime only."""
    work, runtime = Path(work).resolve(), Path(runtime).resolve()
    if not runtime.is_relative_to(work) or runtime.name != 'java.exe' or not runtime.is_file():
        raise ValueError('Mesa is restricted to the disposable worktree runtime')
    archive = fetch_archive(work / 'mesa-download')
    extracted = work / 'mesa-x64'
    extracted.mkdir()  # Never reuse an unknown extraction or overwrite files.
    seven = shutil.which('7z')
    if not seven:
        raise ValueError('Existing 7z executable required; no installer is run')
    # Extract only flat DLLs. No distributor scripts, installers or registry tools run.
    subprocess.run([seven, 'e', str(archive), '-o' + str(extracted), 'x64/*.dll', '-y'],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=90)
    files = list(extracted.iterdir())
    if (not 1 <= len(files) <= 32 or not (extracted / 'opengl32.dll').is_file()
            or sum(p.stat().st_size for p in files) > 512 * 1024 * 1024
            or any(not p.is_file() or p.is_symlink() or p.suffix.lower() != '.dll' for p in files)):
        raise ValueError('Unexpected Mesa DLL extraction')
    for file in files:
        destination = runtime.parent / file.name
        if destination.exists():
            raise ValueError('Mesa must not replace an existing runtime file')
    for file in files:
        shutil.copyfile(file, runtime.parent / file.name)
    return {'kind': 'software-llvmpipe', 'distribution': PROVENANCE,
            'archiveSha256': SHA256, 'archiveBytes': SIZE,
            'dllSha256': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
            'scope': 'Disposable validation runtime only; not shipped or installed system-wide'}
