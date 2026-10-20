# Music Visualizer MP3 validation

Implementation: MP3SPI 1.9.5.4 supplies Java Sound decoding through the existing signed 16-bit PCM, OpenAL playback clock and FFT pipeline. MP3 is selectable with O/Load; drops use the same import path. Invalid input shows a recoverable message. WAV, AIFF and AU remain supported. Mono/stereo, 8–96 kHz, 32 MiB input and 32 MiB decoded source PCM limits remain unchanged. Mono expansion to stereo remains unchanged. MP3 encoder delay/padding can remain; gapless playback is not promised. No external runtime codec or paid service is used.

## Tested source and diff audit

Local rebased implementation head: `2d75a6eb03d2945d8e5503a5a71f386256b467f4`, plus local harness portability/seek-coverage refinements and license whitespace cleanup. Main MP3 runtime code is unchanged by those follow-ups. Source and media hashes are in `music-mp3-source-manifest.json`. This is not a final submitted-head receipt.

Audited the entire proposed diff against recorded base `b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6`. No file deletions are proposed. The 91 inherited paths outside this MP3 change match integrated master parent `3f877c21c8e6776adba9d2130e2fd92e55853763` exactly, including Voxel Street, its website package and its evidence. All non-MP3 upstream progress entries were preserved during conflict recovery. Intended line replacements update supported-format/error text and merge Java Sound service resources in the shaded client. License cleanup changes trailing whitespace/line endings only. `git diff --check b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6` passed on the full proposed working tree.

Fixtures: original six-second 44.1 kHz sine signals, 100 Hz for three seconds then 1000 Hz for three seconds, amplitude 0.45. MPEG header inspection found 232 frames each: CBR stereo uses 128 kbit/s throughout; VBR mono uses 32, 40, 80, 96, 128 and 192 kbit/s. `deploy/generate_music_mp3_fixtures.py` reproduces them with local LAME. LAME is not required by the shipped client or Maven tests.

## Decoder, analysis and package checks

Environment: Linux amd64; OpenJDK 17 via `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` and that directory's `bin` first in PATH; isolated `target/tmp`.

```
mvn -q -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=MusicMp3Test,MusicAnalysisTest,ControlsTest,SandboxIdentityTest test package
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/MusicMp3DecodeSmoke.java
```

Both exited 0 on the rebased implementation. The 20 focused tests passed with zero failures/errors/skips: four MP3 decoder/limits/container tests, six analysis tests, five control tests and five identity tests. Coverage includes actual CBR/VBR decode and bass/mid response, mono duplication, corrupt/empty/oversize input and retry, compressed input exceeding decoded cap, WAV/AIFF/AU compatibility and existing analysis/control behavior. The shaded JAR alone decoded both fixtures to 44100 Hz PCM, 6.060 seconds. Provider service descriptors and decoder license notices were verified in the JAR. Focused tests are not a full-suite pass. Earlier local full-verify attempts were interrupted and are not claimed passed.

## Playtest: native MP3 import and controls — passed

Command: `python3 deploy/run_music_mp3_smoke.py --display "$DISPLAY"`.

Environment: inherited assigned authenticated X11 display; Linux amd64; managed headful Java 25.0.3; Mesa `llvmpipe (LLVM 15.0.6, 256 bits)`; 1100×720 application framebuffer; OpenAL Soft null output; isolated synthetic `target/music-mp3-home` profile. No account data or authentication cookie was read or included in evidence. The harness uses the packaged client JAR, a production MusicVisualizer render loop and file chooser, real X11 keys, native OpenAL PCM upload/clock and the engine F10 recorder. Browser playtesting does not apply to this native client.

| Steps | Expected result | Observed result |
| --- | --- | --- |
| Import a synthetic corrupt MP3 | Recoverable error and retry guidance | `Cannot play audio... O retries.` visible; no track; next import succeeds |
| Press O and choose CBR stereo, then VBR mono | MP3 accepted by chooser; a new decoded track plays and drives bands | Both imports changed decoded-track identity, played and drove bass above 0.4; seeking to the mid passage drove mids above 0.4 |
| Space pause; wait; Right twice; Left; Space resume | Paused clock stable; forward seek clamps below completion; backward seek preserves pause; resume works | Clock held within 0.05 s; second Right clamped below duration; Left selected about 1.06 s while paused; resume passed for both files |
| P, Plus, ], R | Palette changes; sensitivity/motion advance by 0.25; restart returns to beginning | All assertions passed for both files; restart position below 1.5 s and playback active |
| Replace MP3 with synthetic WAV; Escape | Existing WAV still plays/drives bass; audio/window released on exit | WAV playback/bass passed; decoded track and window released |
| F10 start/stop across both MP3s | Fresh playable MP4 below 6 MB | Captured 1,677,272-byte H.264 clip; media checks below passed |

Final native command exited 0. `music-mp3-playtest.json` records assertions and runtime. Initial harness attempts exposed Java 25 compiler output on a Java 17 runtime and a headless Java 17 installation; compilation now uses `--release 17`, while native execution uses the managed headful Java 25 runtime. A timing-dependent seek assertion was replaced by two Right presses to force clamping independent of startup timing. The final run includes the corrected coverage and fresh captures.

## Fresh media — pending controller publication

- `music-mp3-error.png`: readable invalid-input retry message.
- `music-mp3-cbr.png`: live CBR bass response and native playback clock.
- `music-mp3-vbr.png`: live VBR bass response after palette/sensitivity changes.
- `music-mp3.mp4`: final F10 recording, 9.973 seconds, 118 frames, 824×540 encoded from the 1100×720 application framebuffer, 1,677,272 bytes. Recorder is silent by design.
- `music-mp3-video-contact.png`: four actual decoded frames, inspected alongside all three screenshots. Frames show paused seek clamping, VBR bass, paused backward seek and live mid response.

```
javac --release 17 -cp target/voxel-one-1.0-SNAPSHOT-client.jar -d target/music-mp3-smoke deploy/VerifyMusicMedia.java
java -cp target/voxel-one-1.0-SNAPSHOT-client.jar:target/music-mp3-smoke VerifyMusicMedia dashboard/evidence/music-mp3.mp4 dashboard/evidence/music-mp3-media-check.json dashboard/evidence/music-mp3-video-contact.png
```

Both exited 0. Every H.264 frame decoded, metadata frame count matched, first/last image hashes differed, four frames were extracted and the clip stayed below 6 MB. The final screenshots and contact sheet were visually inspected. Media and reports are local, sanitized and pending runner publication to the assigned feature branch.

## Hosted checks and remaining gates

Actual GitHub job/step results were inspected for historical source `c1e17f8c96e3bd17fd251520c7cd82a0ea43a20f`: [run 39493586227](https://github.com/jaymsDooku/voxel-one/actions/runs/39493586227) completed successfully. All four jobs (Linux, Windows, Intel Mac, ARM Mac) passed `mvn --batch-mode verify` and the packaged-JAR MP3 decoder check. Sanitized exact-source job results are in `music-mp3-hosted-c1e17f8c.json`. These passes predate the master rebase and do not qualify the final rebased head.

Controller publication is required for final exact-head hosted checks. The new handoff must publish the rebased source, harness refinements and fresh media; it must not submit review or claim pending checks passed. No repeat of the unchanged old-source handoff is needed.

Native PCM upload/clock was tested through OpenAL Soft null output, not audible speakers. Software rendering does not qualify physical-GPU performance. Other desktop native playback and physical hardware qualification remain pending; existing stable-rollout gates are retained. Do not mark ready before required final-source validation.
