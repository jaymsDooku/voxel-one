#!/usr/bin/env python3
"""Run the preserved road playtest with separate carrier review evidence names."""
from pathlib import Path

source = Path(__file__).with_name('run_noncardinal_smoke.py').read_text()
old = "prefix='noncardinal-'+args.scenario"
assert source.count(old) == 1, 'Preserved runner prefix changed; inspect before running'
source = source.replace(old, "prefix='carrier-final-'+args.scenario")
exec(compile(source, __file__, 'exec'))
