# Music Visualizer MP3 validation

Implementation: MP3SPI 1.9.5.4 supplies Java Sound decoding through the existing signed 16-bit PCM, OpenAL playback clock and FFT pipeline. MP3 is selectable with O/Load; drops use the same import path. Invalid input shows a recoverable message. WAV, AIFF and AU remain supported. Mono/stereo, 8–96 kHz, 32 MiB input and 32 MiB decoded source PCM limits remain unchanged. Mono expansion to stereo remains unchanged. MP3 encoder delay/padding can remain; gapless playback is not promised. No external runtime codec or paid service is used.

## Tested source and diff audit

Published implementation head: `e6e1acb4e9202f5a216f4de62aaae42fde9b5b93` ([PR #77](https://github.com/jaymsDooku/voxel-one/pull/77)). Saved hashes for every tested non-dashboard source file and all captured media match the published checkout. No runtime-source changes followed the passing native run. Controller publication was completed; this evidence-status update changes metadata only.

Audited the entire proposed diff against recorded base `b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6`. No file deletions are proposed. The 91 inherited paths outside this MP3 change match integrated master parent `3f877c21c8e6776adba9d2130e2fd92e55853763` exactly, including Voxel Street, its website package and its evidence. All non-MP3 upstream progress entries were preserved during conflict recovery. Intended line replacements update supported-format/error text and merge Java Sound service resources in the shaded client. License cleanup changes trailing whitespace/line endings only. `git diff --check b1d7b8d796b97a87a3d63bb08baf2bb9746b8ca6` passed on the full proposed working tree.

Fixtures: original six-second 44.1 kHz sine signals, 100 Hz for three seconds then 1000 Hz for three seconds, amplitude 0.45. MPEG header inspection found 232 frames each: CBR stereo uses 128 kbit/s throughout; VBR mono uses 32, 40, 80, 96, 128 and 192 kbit/s. `deploy/generate_music_mp3_fixtures.py` reproduces them with local LAME. LAME is not required by the shipped client or Maven tests.

## Decoder, analysis and package checks

Environment: Linux amd64; OpenJDK 17 via `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` and that directory's `bin` first in PATH; isolated `target/tmp`.

```
mvn -q -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=MusicMp3Test,MusicAnalysisTest,ControlsTest,SandboxIdentityTest test package
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/MusicMp3DecodeSmoke.java
```

Both exited 0 on the rebased implementation. The 20 focused tests passed with zero failures/errors/skips: four MP3 decoder/limits/container tests, six analysis tests, five control tests and five identity tests. Coverage includes actual CBR/VBR decode and bass/mid response, mono duplication, corrupt/empty/oversize input and retry, compressed input exceeding decoded cap, WAV/AIFF/AU compatibility and existing analysis/control behavior. The shaded JAR alone decoded both fixtures to 44100 Hz PCM, 6.060 seconds. Provider service descriptors and decoder license notices were verified in the JAR. The focused check was followed by a complete local Maven verify on published source `e6e1acb4e9202f5a216f4de62aaae42fde9b5b93`: 741 tests across 142 suites, zero failures/errors/skips, exit 0, elapsed 11 minutes 18 seconds. Earlier interrupted attempts are not counted as passes. Sanitized fresh reports are in `music-mp3-full-verify-e6e1acb4.json`; only files with this full run's unique report suffix were counted.

Full local verification command, with Java 17 in JAVA_HOME/PATH:

```
timeout 1200s mvn -B -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dsurefire.reportNameSuffix=mp3-e6-final verify
```

Observed: `BUILD SUCCESS`, 741 tests, no failures/errors/skips. This completed before the execution cap. No required tests were skipped or gates weakened. Main runtime source and captured media remain unchanged from the published source.

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

## Fresh media — published and verified

- `music-mp3-error.png`: readable invalid-input retry message.
- `music-mp3-cbr.png`: live CBR bass response and native playback clock.
- `music-mp3-vbr.png`: live VBR bass response after palette/sensitivity changes.
- `music-mp3.mp4`: final F10 recording, 9.973 seconds, 118 frames, 824×540 encoded from the 1100×720 application framebuffer, 1,677,272 bytes. Recorder is silent by design.
- `music-mp3-video-contact.png`: four actual decoded frames, inspected alongside all three screenshots. Frames show paused seek clamping, VBR bass, paused backward seek and live mid response.

```
javac --release 17 -cp target/voxel-one-1.0-SNAPSHOT-client.jar -d target/music-mp3-smoke deploy/VerifyMusicMedia.java
java -cp target/voxel-one-1.0-SNAPSHOT-client.jar:target/music-mp3-smoke VerifyMusicMedia dashboard/evidence/music-mp3.mp4 dashboard/evidence/music-mp3-media-check.json dashboard/evidence/music-mp3-video-contact.png
```

Both exited 0. Every H.264 frame decoded, metadata frame count matched, first/last image hashes differed, four frames were extracted and the clip stayed below 6 MB. The final screenshots and contact sheet were visually inspected. All five captures were verified at published head `e6e1acb4e9202f5a216f4de62aaae42fde9b5b93` using GitHub Contents API blob hashes and byte sizes against the local captures. `music-mp3-published-media.json` records that verification; this metadata update awaits runner recording.

## Hosted checks and remaining gates

Actual GitHub job/step results were inspected for historical source `c1e17f8c96e3bd17fd251520c7cd82a0ea43a20f`: [run 39493586227](https://github.com/jaymsDooku/voxel-one/actions/runs/39493586227) completed successfully. All four jobs (Linux, Windows, Intel Mac, ARM Mac) passed `mvn --batch-mode verify` and the packaged-JAR MP3 decoder check. Sanitized exact-source job results are in `music-mp3-hosted-c1e17f8c.json`. These passes predate the master rebase and do not qualify the final rebased head.

Controller published source and media at `e6e1acb4e9202f5a216f4de62aaae42fde9b5b93`. Terminal GitHub API results were independently inspected: [push run 39497933277](https://github.com/jaymsDooku/voxel-one/actions/runs/39497933277) and [PR run 39497939561](https://github.com/jaymsDooku/voxel-one/actions/runs/39497939561) both succeeded. Linux, Windows, Intel Mac and ARM Mac build jobs all passed Maven verify and the packaged-JAR MP3 decode check. Publish jobs were skipped as expected; no deployment occurred.

Downloaded the four actual sanitized Maven summary artifacts from the exact-head push run. Each artifact's sourceHead equals `e6e1acb4e9202f5a216f4de62aaae42fde9b5b93`. Each reports 741 tests across 142 suites, zero failures/errors/skips, including all four MusicMp3Test cases. Verified aggregate counts and MP3 suite counts; no raw runtime logs, XML properties or account data were exported. Artifact IDs and counts are in `music-mp3-hosted-test-artifacts-e6e1acb4.json`; terminal job results are in `music-mp3-hosted-e6e1acb4.json`.

All required implementation validation is complete. Completed CI supplements the independently executed native workflow and inspected media; it does not constitute reviewer approval. Only sanitized evidence/progress metadata changed after tested head `e6e1acb4`; runtime/test/CI sources and all media still match recorded hashes. Ready for independent review, with runner publication of these final metadata updates pending.

Final audit against the updated recorded review base `3f877c21c8e6776adba9d2130e2fd92e55853763`: 28 changed paths at publication, all within the MP3 implementation, test, documentation, licensing and evidence scope. No deletions. Every non-MP3 progress entry matches the review base unchanged. Full diff check passed.

Native PCM upload/clock was tested through OpenAL Soft null output, not audible speakers. Software rendering does not qualify physical-GPU performance. Other desktop native playback and physical hardware qualification remain pending; existing stable-rollout gates are retained. Required final-source implementation checks have passed. Independent review is required; hardware/stable-rollout qualification remains separate and pending as described above.
