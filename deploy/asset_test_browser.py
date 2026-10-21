#!/usr/bin/env python3
"""Synthetic browser handoff: URI stays in process memory/stdin, never evidence."""
import os, subprocess, sys
from pathlib import Path
result=Path(os.environ['ASSET_SMOKE_RESULT'])
process=subprocess.run(['node','deploy/test_asset_manager.cjs',os.environ['ASSET_SMOKE_FIXTURE']],input=sys.argv[1]+'\n',text=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=45)
result.write_text('PASS' if process.returncode==0 else 'FAIL')
sys.exit(process.returncode)
