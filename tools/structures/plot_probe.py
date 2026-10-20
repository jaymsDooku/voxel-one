#!/usr/bin/env python3
"""Plot a recorded CPU probe report. Requires ImageMagick for PNG output."""
import argparse
import html
import json
import os
from pathlib import Path
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('report', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    data = json.loads(args.report.read_text())
    if data['qualification'] != 'NOT QUALIFIED':
        raise ValueError('Probe must not claim qualification')
    groups = data['summary']
    scale = 590 / max(item['elapsed_ms']['worst'] for item in groups.values())
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="960" height="480" viewBox="0 0 960 480">',
           '<rect width="960" height="480" fill="#101824"/>',
           '<g font-family="DejaVu Sans" fill="#ecf2fa">']

    def text(x, y, value, size=16):
        svg.append(f'<text x="{x}" y="{y}" font-family="DejaVu Sans" font-size="{size}" fill="#ecf2fa" stroke="none">{html.escape(value)}</text>')

    text(30, 38, 'CPU chunk-mesher probe — NOT QUALIFIED', 25)
    text(30, 66, data['environment_label'], 15)
    text(30, 91, f"{data['runs_per_density']} trials per density; three warmups; elapsed worker time in ms", 15)
    text(30, 121, 'Median / p95 / worst. One chunk, isolated two-leaf fractional cells.', 15)
    for i, key in enumerate(('1', '8', '64', '512')):
        group = groups[key]
        timings = group['elapsed_ms']
        y = 156 + i * 63
        text(30, y + 14, f'{key} cells')
        for metric, color, offset in (('worst', '#52647d', 0), ('p95', '#efb35c', 7), ('median', '#61c9c0', 14)):
            svg.append(f'<rect x="140" y="{y + offset}" width="{max(1, timings[metric] * scale):.2f}" height="6" fill="{color}"/>')
        text(750, y + 14, f"{timings['median']:.1f} / {timings['p95']:.1f} / {timings['worst']:.1f}", 14)
    text(30, 428, 'Not a frame, full-fixture, instance-rendering, placement or network benchmark.', 15)
    text(30, 454, 'Local development data. No supported limits or reference-target pass inferred.', 15)
    svg.append('</g></svg>')
    source = args.output.with_suffix('.svg')
    source.write_text('\n'.join(svg) + '\n')
    scratch = Path(__file__).resolve().parents[2] / 'target/probe-plot-temporaries'
    scratch.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=scratch) as temporary:
        env = os.environ.copy()
        env['MAGICK_TMPDIR'] = temporary
        subprocess.run(['convert', str(source), str(args.output)], check=True, env=env)
    print('Playtest: plotted recorded probe measurements; NOT QUALIFIED.')


if __name__ == '__main__':
    main()
