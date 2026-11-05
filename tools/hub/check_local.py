#!/usr/bin/env python3
"""Run local Hub regressions without writing to shared Maven or temp folders."""
import os
from pathlib import Path
import subprocess
import sys


def main():
    root = Path(__file__).resolve().parents[2]
    cache = root / "target/maven-cache"
    temporary = root / "target/hub-tmp"
    for directory in (cache, temporary):
        directory.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env.update({key: str(temporary) for key in ("TMPDIR", "TMP", "TEMP")})
    # Match the scoped platform workflow, including the pinned TLS login regression.
    tests = ("SandboxIdentityTest,HubCatalogTest,HubShellTest,HubShellActivationTest,"
             "UpdaterTest,AccountStoreTest,ProtocolCompatibilityTest,"
             "MultiplayerTest#authenticationRegistrationDuplicateSessionsAndCertificatePin")
    commands = [
        ["mvn", "-q", f"-Dmaven.repo.local={cache}",
         f"-Djava.io.tmpdir={temporary}",
         f'-DargLine=-Djava.io.tmpdir="{temporary}"',
         f"-Dtest={tests}", "test", "package"],
        [sys.executable, "-m", "unittest", "discover", "-s", "tools/hub", "-p", "test_*.py"],
    ]
    for label, command in zip(("Java regressions and package", "Publisher regressions"), commands):
        print(f"Running: {label}", flush=True)
        try:
            result = subprocess.run(command, cwd=root, env=env, check=False)
        except OSError as error:
            print(f"Could not start {label}: {error}", file=sys.stderr)
            return 1
        if result.returncode:
            print(f"Failed: {label} (exit {result.returncode})", file=sys.stderr)
            return result.returncode if result.returncode > 0 else 1
    print("Local checks passed. Platform gameplay and published-update acceptance remain separate.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
