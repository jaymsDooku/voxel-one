# Software recorder checks

Three GROUND render-only runs; Low; 640x400; Mesa llvmpipe OpenGL 3.3; 12 warm-up and 24 measured frames each. Functional export check, not a Windows baseline or optimization comparison. No cold driver-cache claim. Package/source binding: graphics-source-manifest.json.

| Measure | Median | Min | Max |
|---|---:|---:|---:|
| p50FrameMs | 1221.089 | 1213.323 | 1240.224 |
| p95FrameMs | 1572.551 | 1558.964 | 1901.141 |
| p99FrameMs | 1765.508 | 1710.605 | 1977.319 |
| onePercentLowFps | 0.566 | 0.506 | 0.585 |

Recorder-copy CPU totals are in each JSON. Recorder-on/off and video-on/off observer cost still need equal-route reference hardware runs. Reduced-quality gains and unchanged-quality optimization gains have not been measured.
