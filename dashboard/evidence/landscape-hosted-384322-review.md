# Hosted source review: 384322d7fa9b83745b20db78d5ef3073aa25f67d

Historical source after subsequent rebase. Native run 39515239868 failed overall: navigation regression exit 1. No complete qualification receipt exists. Committed regression PASS text in that upload is not proof of fresh success. A revised harness clears named reports and records fresh per-scenario status.

Playtest: hosted Linux X11, llvmpipe LLVM 15.0.7, OpenAL null, synthetic isolated profiles. The exact-head status records successful WAV lifecycle, MP3 lifecycle, MP4 decode, three matched timing pairs and real 1800-second playback soak. The 24 focused tests have zero failures/errors. These results do not qualify physical GPU or audible speakers.

Inspected the actual decoded four-frame contact sheet: recognizable terrain, lake and rooted grove; distinct particle/trail accents; readable controls through contrasting passages and completion. Clip: H264, 824x540 actual recorded size, 256 decoded frames, 17.558 seconds, 2,916,063 bytes. Requested native window settings were 1100x720; actual media size differs on hosted X11.

Soak: 1800.052510107 seconds, 637113 frames. Maximum active counts: 8 ripples, 150 particles, 104 memory elements; caps 8/256/128. Mesh stayed 148752 bytes across all 30 samples. RSS after five minutes was 495580 kB; at 30 minutes 495712 kB, a 132 kB increase (0.027%). Last ten minutes span 495688–495712 kB. Heap oscillated with collection, ending at 89406528 bytes; low samples recur rather than steadily rise. This supports stable resource use on this software renderer. Production window, audio and mesh release assertions passed before the final soak report was written.

Performance: all three alternating matched pairs meet the 10% median/p95 budget on the same hosted platform/settings. Source production Java and shaders match the rebased client, but full final-head checks remain required. Preserve the old reports separately; do not relabel them final-head evidence.
