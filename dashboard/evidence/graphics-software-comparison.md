# Linux software recorder integration

Three same-source LOW GROUND runs, 12 warm-up + 24 measured frames each. Mesa llvmpipe OpenGL 3.3; these are functional export checks, not reference hardware benchmarks or measured optimization gains. No before/after comparison is available. Local package revision is unknown; source/JAR hashes are in graphics-source-manifest.json.

| Metric | Median across runs | Minimum | Maximum |
|---|---:|---:|---:|
| p50FrameMs | 1230.278 | 1207.452 | 1232.752 |
| p95FrameMs | 1588.777 | 1568.120 | 1671.463 |
| p99FrameMs | 1660.851 | 1584.924 | 1771.049 |
| onePercentLowFps | 0.602 | 0.565 | 0.631 |

`python3 deploy/compare_performance.py` accepted the three matching reports. Empty capture and changed-resolution cases returned exit 2 as expected. Recorder overhead is not measured separately by these checks.
