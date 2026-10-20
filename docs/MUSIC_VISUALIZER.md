# Voxel Music Visualizer

Choose **Voxel Music Visualizer** in the desktop application picker, then **Open visualizer**. It runs locally and needs no account or server. Escape or the window close button returns to the picker. Each launch creates its own audio, analysis, scene and recorder resources.

Direct entry: `java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game music` (or `--music-visualizer`). Unknown or missing command-line arguments still produce normal errors. Exiting direct entry opens application selection.

Load a local MP3, WAV, AIFF or AU file with **O**, the Load button, or a file drop. Supported input is mono/stereo at 8–96 kHz, converted to signed 16-bit PCM. Both the input file and decoded PCM are limited to 32 MiB. MP3 decoding uses the bundled Java Sound MP3SPI/JLayer provider; it needs no external codec, network service or paid service. Errors stay in the interface; Load can retry. Loading a new file stops the previous track and clears its traces. Decode runs off the rendering thread. Audio output must be available; a missing device gives a recoverable message.

- **Space**: pause/resume; resumes from the beginning after completion.
- **Left / Right**: seek back/forward 5 seconds. Click the timeline for an absolute seek.
- **R**: restart position, preserving pause/play state.
- **Minus / Plus**: sensitivity, 0.25–3. Click the bottom left control to set it.
- **[ / ]**: motion intensity, 0–2. Click the bottom right control to set it.
- **P**: cycle cyan, ember and lime palettes.
- **F11**: fullscreen/windowed.
- **F10**: shared engine recorder; silent MP4 saved in `.voxel-one/recordings` under the local profile.
- **Escape**: return to application selection. Audio stops and resources are released.

The default scene is a living landscape: 15×15 voxel terrain columns, groves, grass and a central lake. Bass (40–250 Hz) drives broad travelling waves. Mids (250–2500 Hz) bend tree crowns and grass while trunks stay rooted at the same displaced column surface. Highs (2500–12000 Hz, limited by Nyquist) release restrained fireflies and water highlights. Detected beats send thin rings over land and water and leave temporary illuminated ground trails. Palette colors tint the ground and accents. **D** toggles slow camera drift; the default scenic camera stays fixed. Drift, waves, vegetation movement, beat accents and particles all scale with motion intensity. Zero motion gives a still presentation. Quiet passages keep only slight leaf and water movement.

Independent 2048-sample Hann-windowed FFTs combine channel powers without phase cancellation and follow the OpenAL source playback offset, with at most two transforms per rendered frame (one per stereo channel) and a 512-sample hop. Bass attack/decay is 150/900 ms; mids 100/600 ms; highs 40/350 ms. Spectral flux detects onsets with an adaptive threshold, silence gate and a 0.23-second refractory period. Approximate bands do not separate instruments. Sustained energy eases lighting over 5 seconds on attack and 10 seconds on decay. It does not flash the whole scene on beats.

Terrain displacement is globally bounded at 0.18708 units (8% of the actual 2.33849-unit base height range), including overlapping ripples and maximum motion. At default motion the bound is 0.09354 units. Fixed pools recycle the oldest slot: 8 ripples expire within 6 seconds, 256 fireflies within 8 seconds and 128 trail elements within 20 seconds. At most 4096 boxes fit the renderer's fixed staging buffer; the scene uses one instanced cube draw and releases its GPU/native buffers on exit. Roots and terrain use the same integer-centered surface sample each frame.

Seeking and replacing a track clear FFT envelopes and all transient pools. Slow atmosphere brightness is retained across those actions to avoid a sudden scene-wide lighting jump. Explicit pause freezes the visible landscape, pool ages and drift while the audio analysis envelopes decay. Completion instead lets the landscape ease to calm and effects expire. Resume restores the audio response. Load errors can be retried. Exit releases scene, recorder and audio resources.

No Terrain or City presets are included. This application does not change the Sandbox or City world formats, ports or controls.

## Reproduce validation

Use the assigned graphical display and an isolated synthetic profile:

```sh
mkdir -p target/tmp
mvn -q -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=MusicAnalysisTest,MusicLandscapeTest,MusicMp3Test,SandboxIdentityTest,ControlsTest test
python3 deploy/run_music_smoke.py --display "$DISPLAY"
python3 deploy/run_music_regressions.py --display "$DISPLAY" --evidence-prefix landscape-regression
python3 deploy/check_music_media.py --prefix landscape-live
python3 deploy/run_landscape_comparison.py --display "$DISPLAY"
python3 deploy/run_landscape_soak.py --display "$DISPLAY"
```

The native harness uses actual X11 keys, the production application picker and file picker, real OpenAL source playback, and the production renderer/F10 recorder. It generates local 100 Hz, 1000 Hz, 6000 Hz, pulsed and silent passages. It checks pause/resume, seek, completion/restart, invalid files, effect bounds/decay, optional drift and frozen pause, controls, fullscreen, exit and relaunch. OpenAL Soft's null output makes the run deterministic on this VPS: it tests the native clock and PCM upload, not audible physical speaker output. The report names the renderer, resolution, scene settings and observed frame rate. Software-rendered results do not qualify physical-GPU performance or a stable rollout on untested hardware.

## MP3 compatibility and checks

MP3 uses the same asynchronous Java Sound conversion to 16-bit PCM, OpenAL playback clock and FFT pipeline as the existing containers. CBR stereo and VBR mono MPEG Layer III fixtures at 44.1 kHz cover both bitrate modes. Input remains mono/stereo at 8–96 kHz; files over 32 MiB and decoded source PCM over 32 MiB remain rejected. Mono is then duplicated to stereo as before. MP3 encoder delay/padding can remain in decoded duration; sample-accurate gapless playback is not promised. Invalid or unsupported files show a recoverable error; press O or Load to retry.

The decoder is pure Java, intended for the existing Java 17 desktop runtimes on Linux, Windows and Intel/ARM Mac. A Linux decode pass does not qualify native playback on every desktop. Physical audio output and physical-GPU performance remain separate qualification gates.

```sh
mvn -B verify
java -cp target/voxel-one-1.0-SNAPSHOT-client.jar deploy/MusicMp3DecodeSmoke.java
python3 deploy/run_music_mp3_smoke.py --display "$DISPLAY"
```

The MP3 native harness uses the shipped shaded JAR, a real Swing file chooser and X11 keys. It needs a headful Java 17+ runtime and compiles its harness classes with `--release 17`. It checks invalid-file recovery, CBR/VBR playback and band response, pause/resume, paused keyboard seek/clamping, seek/restart, controls and WAV replacement. It captures fresh screenshots and a short F10 recording. Its OpenAL null backend checks PCM upload and playback timing, not audible speakers. The inherited assigned display is required.

Fixtures in `src/test/resources/music/` are original six-second sine signals: 100 Hz for three seconds, then 1000 Hz for three seconds, amplitude 0.45. `deploy/generate_music_mp3_fixtures.py` reproduces them with system LAME (128 kbit/s CBR stereo and quality-4 VBR mono). LAME is only a fixture-generation tool; tests and the shipped client need no LAME installation. Encoder output is checked in so Maven tests run on all supported operating systems.

Decoder source and attribution: [Soundlibs MP3SPI/JLayer](https://github.com/pdudits/soundlibs). Bundled license copies and source links are under `META-INF/licenses/` in the client JAR.

## Landscape qualification

`deploy/run_landscape_bench.py` runs three matched native renderer trials at 1100×720 with a 5-second warm-up, vsync disabled, the same 24-second original stereo fixture, sensitivity/motion 1 and cyan palette. Each trial lasts 20 seconds. Reports contain median and p95 frame times. Baseline reports were captured from the original Resonance Core before edits. `landscape-dense-draft-*` reports are the rejected 25×25 draft, not the final landscape. This VPS uses Linux llvmpipe software GL and OpenAL Soft null output. It does not qualify physical GPU performance or audible speakers.

`deploy/run_landscape_soak.py` exercises 1800 real seconds of native playback. Its original 8 kHz mono fixture cycles bass, mids, highs, mixed rhythmic passages, quiet audio and silence. Source PCM is 28.8 MB, within the existing 32 MiB decode limit. The test checks live pool/displacement bounds, GL errors, constant tracked mesh residency and resource release, and records heap/RSS once per minute for trend review. A `.partial` file explicitly means running, not passed. `MusicLandscapeTest` separately stress-tests maximum inputs for 30 simulated minutes and verifies cap saturation and expiry; it does not replace the wall-clock soak.

Fresh evidence is named `landscape-live-*` and `landscape-live.mp4`, preserving historical music evidence. F10 records the actual production renderer. The native harness covers contrasting passages and natural decay into silence, then stops recording only after a natural completion/calm screenshot; a separate seek-to-silence check remains. Required final-head validation, wall-clock resource trend review and physical-GPU qualification must remain pending until actually executed and inspected.

`deploy/run_landscape_comparison.py` alternates three original/landscape pairs and requires each pair's median and p95 to remain within 10%. Original source comes from the immutable `baselineHead` in `landscape-source-manifest.json`, even after the feature is committed or integrated. Earlier exploratory timings remain as separate reports; shared-host timing varies.

The scoped `Music landscape native qualification` workflow runs on this feature branch's push, or a dispatch naming an exact commit. It compiles that source on Java 17, exercises native controls, MP3 and application navigation, decodes the finished F10 clip, runs the matched timing checks, then plays a full 1800-second fixture. It uploads only allowlisted synthetic landscape evidence. The hosted receipt is absent unless every command passes. Reports and media still need manual inspection; the workflow cannot qualify physical GPU or speaker output.
