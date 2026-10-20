# Voxel Music Visualizer

Choose **Voxel Music Visualizer** in the desktop application picker, then **Open visualizer**. It runs locally and needs no account or server. Escape or the window close button returns to the picker. Each launch creates its own audio, analysis, scene and recorder resources.

Direct entry: `java -jar target/voxel-one-1.0-SNAPSHOT-client.jar --game music` (or `--music-visualizer`). Unknown or missing command-line arguments still produce normal errors. Exiting direct entry opens application selection.

Load a local WAV, AIFF or AU file with **O**, the Load button, or a file drop. Supported input is mono/stereo at 8–96 kHz, converted to signed 16-bit PCM. Both the input file and decoded PCM are limited to 32 MiB. Compressed formats such as MP3 are not bundled. Errors stay in the interface; Load can retry. Loading a new file stops the previous track and clears its traces. Decode runs off the rendering thread. Audio output must be available; a missing device gives a recoverable message.

- **Space**: pause/resume; resumes from the beginning after completion.
- **Left / Right**: seek back/forward 5 seconds. Click the timeline for an absolute seek.
- **R**: restart position, preserving pause/play state.
- **Minus / Plus**: sensitivity, 0.25–3. Click the bottom left control to set it.
- **[ / ]**: motion intensity, 0–2. Click the bottom right control to set it.
- **P**: cycle cyan, ember and lime palettes.
- **F11**: fullscreen/windowed.
- **F10**: shared engine recorder; silent MP4 saved in `.voxel-one/recordings` under the local profile.
- **Escape**: return to application selection. Audio stops and resources are released.

The floating Resonance Core expands with bass (40–250 Hz), rotates alternating layers with mids (250–2500 Hz), and brightens its voxel edges with treble (2500–12000 Hz, limited by Nyquist). The floor shows a dim mirrored copy of the core. Sensitivity scales band response and onset thresholds. Motion scales expansion, rotation speed and pulse travel. At zero motion the core can still change color/brightness with treble.

Independent 2048-sample Hann-windowed FFTs combine channel powers without phase cancellation and follow the OpenAL source playback offset, with at most two transforms per rendered frame (one per stereo channel) and a 512-sample hop. Attack and release smoothing reduce jitter. Spectral flux detects onsets with an adaptive threshold, silence gate and a 0.23-second refractory period. Approximate frequency bands do not separate instruments. A seek or track change clears FFT, smoothing, rotation and effect history. Pause and completion decay band values rather than continuing motion. Every beat adds an outward floor pulse and a dissolving voxel stack. Each trace lasts at most 3 seconds; at most 12 can coexist.

No Terrain or City presets are included. This application does not change the Sandbox or City world formats, ports or controls.

## Reproduce validation

Use the assigned graphical display and an isolated synthetic profile:

```sh
mkdir -p target/tmp
mvn -q -Dmaven.repo.local=target/maven-cache -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=MusicAnalysisTest,SandboxIdentityTest,ControlsTest test
python3 deploy/run_music_smoke.py --display "$DISPLAY"
python3 deploy/run_music_regressions.py --display "$DISPLAY"
python3 deploy/check_music_media.py
```

The native harness uses actual X11 keys, the production application picker and file picker, real OpenAL source playback, and the production renderer/F10 recorder. It generates local 100 Hz, 1000 Hz, 6000 Hz, pulsed and silent passages. It checks pause/resume, seek, completion/restart, invalid files, effect bounds/decay, controls, fullscreen, exit and relaunch. OpenAL Soft's null output makes the run deterministic on this VPS: it tests the native clock and PCM upload, not audible physical speaker output. The report names the renderer, resolution, scene settings and observed frame rate. Software-rendered results do not qualify physical-GPU performance or a stable rollout on untested hardware.
