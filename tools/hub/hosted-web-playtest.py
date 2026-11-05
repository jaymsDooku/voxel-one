#!/usr/bin/env python3
"""Play a registered Sites game through a game-only, memory-authenticated proxy.

Credentials enter hidden stdin and never reach browser code, files or arguments.
This verifies deployed assets through service access, not visitor sign-in or a
new deployment. The controller owns publication and catalog promotion.
"""
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import termios
import threading
import urllib.error
import urllib.parse
import urllib.request

PREFIX = '/games/space-invaders/'
MAX_BODY = 2 * 1024 * 1024
MAX_TOTAL = 64 * 1024 * 1024


def main():
    fd = sys.stdin.fileno()
    previous = termios.tcgetattr(fd) if sys.stdin.isatty() else None
    if previous:
        hidden = termios.tcgetattr(fd)
        hidden[3] &= ~termios.ECHO
        termios.tcsetattr(fd, termios.TCSANOW, hidden)
    print('Ready for hidden Sites service access JSON on stdin.', flush=True)
    try:
        access = json.loads(sys.stdin.readline())
    finally:
        if previous:
            termios.tcsetattr(fd, termios.TCSANOW, previous)
    origin = access['origin']
    # This tool is scoped to the existing catalog game and registered origin.
    if origin != 'https://voxel-one.jamesleaver1.chatgpt.site':
        raise ValueError('Unexpected registered game origin')

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None

    opener = urllib.request.build_opener(NoRedirect)
    records = {}
    total = 0
    lock = threading.Lock()

    def fetch(path):
        nonlocal total
        request = urllib.request.Request(origin + path, headers={
            'OAI-Sites-Authorization': 'Bearer ' + access['token']})
        with opener.open(request, timeout=25) as response:
            body = response.read(MAX_BODY + 1)
            if len(body) > MAX_BODY:
                raise ValueError('Asset exceeds bound')
            if path == PREFIX and not re.search(rb'id=["\x27]start["\x27]', body):
                raise ValueError('Deployed game Start control unavailable')
            with lock:
                total += len(body)
                if total > MAX_TOTAL or len(records) >= 512:
                    raise ValueError('Session exceeds bound')
                records[path] = {'sha256': hashlib.sha256(body).hexdigest(), 'size': len(body)}
            return body, response.headers.get('Content-Type', 'application/octet-stream')

    # Replace any old pass before network work; a failed run cannot leave a stale receipt.
    Path('dashboard/evidence/hub-hosted-web-assets.json').write_text(json.dumps({
        'passed': False, 'observed': 'Started; deployed service-view validation has not completed.'
    }, indent=2) + '\n')
    # Fail before exposing any unexpected/authentication page to the browser.
    fetch(PREFIX)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            path = urllib.parse.urlsplit(self.path).path
            # No query passthrough, APIs, parent paths, encoded traversal or redirects.
            allowed = path == PREFIX or (path.startswith(PREFIX)
                and re.fullmatch(r'[a-zA-Z0-9_./-]+', path)
                and '..' not in path.split('/')
                and Path(path).suffix in {'.mjs', '.css', '.png', '.jpg', '.jpeg', '.webp', '.svg', '.ogg', '.wav', '.mp3'})
            if not allowed:
                self.send_error(403)
                return
            try:
                body, content_type = fetch(path)
            except Exception:
                self.send_error(502, 'Game asset unavailable')
                return
            self.send_response(200)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Security-Policy', "default-src 'self' blob: data:; connect-src 'self'; object-src 'none'")
            self.end_headers()
            self.wfile.write(body)

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    result = None
    try:
        result = subprocess.run(['node', 'tools/hub/hosted-web-playtest.cjs',
            'http://127.0.0.1:' + str(server.server_port) + PREFIX], timeout=90, check=False)
    finally:
        server.shutdown()
        server.server_close()
        report = {'origin': origin, 'path': PREFIX, 'access': 'Registered Sites service access held server-side in memory; no visitor identity or sign-in claim.',
            'passed': result is not None and result.returncode == 0, 'assets': records,
            'limitations': ['No new deployment or catalog promotion occurred.', 'Same deployed build reload is not cross-deployment freshness.', 'Service-authenticated proxy is not the normal signed-in browser flow.']}
        Path('dashboard/evidence/hub-hosted-web-assets.json').write_text(json.dumps(report, indent=2) + '\n')
    return result.returncode


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as error:
        # Do not retain upstream error bodies, URLs, headers or private page content.
        print('Hosted game check failed: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
