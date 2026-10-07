#!/usr/bin/env python3
"""Run native game UI and paid simulation checks on the assigned inherited display."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
parser = argparse.ArgumentParser()
parser.add_argument('--display', required=True)
parser.add_argument('--profile', default='target/resource-progression-profile')
parser.add_argument('--evidence', default='dashboard/evidence')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
os.chdir(root)
classpath = os.pathsep.join(['target/classes', 'target/test-classes'] + [str(p) for p in sorted((root / 'target/m2').rglob('*.jar'))])
subprocess.run(['javac', '-cp', classpath, '-d', 'target/test-classes', 'deploy/ResourceProgressionSmoke.java'], check=True)
env = dict(os.environ, DISPLAY=args.display, TMPDIR=str(root/'target/tmp'))
try:
    result = subprocess.run(['java', '-Djava.io.tmpdir='+str(root/'target/tmp'), '-cp', classpath,
                'dev.jayms.ResourceProgressionSmoke', args.evidence, args.profile], env=env, timeout=300)
except subprocess.TimeoutExpired:
    print("Resource progression native playtest timed out after 300 seconds")
    sys.exit(124)
sys.exit(result.returncode)
