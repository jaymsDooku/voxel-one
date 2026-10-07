Vehicle audio handoff

Car, train and takeoff sounds are implemented in the assigned feature worktree. No owner answer is pending. Controller publication and independent review remain. No commit, push, merge or deployment was performed.

The current base supplies the working automatic railway. Audio uses its moving steam trains, stops during station dwell and removal, and limits train and plane sounds to 160 world units. The driven-car engine has an idle loop and speed-dependent pitch. Takeoff sound runs during stage 2 for the first 6 seconds. Controls menus and editor mute audio. Isometric listening follows camera focus position and height. Missing output hardware is nonfatal; cleanup is idempotent.

Executed checks

- JAVA_HOME=/usr/lib/jvm/jdk-21.0.5-oracle-x64 timeout 180s mvn -q -Dmaven.repo.local=target/maven-cache -Djava.io.tmpdir="$PWD/target/tmp" -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" -Dtest=VehicleAudioTest,AviationTest,JeepTest,RailwayTest,IsometricCameraTest,CitySavesTest,RailwaySaveCompatibilityTest verify: exit 0; 37 tests, 0 errors, 0 failures, 0 skipped. Client, server, launcher and normal JAR packages built. Details: vehicle-audio-build-check.json.
- git diff --check: exit 0. No unmerged files.
- python3 -m py_compile deploy/run_vehicle_audio_playtest.py deploy/check_vehicle_audio_pcm.py: exit 0.
- Final deploy/VehicleAudioPlaytest.java and restored deploy/JeepPlaytest.java compiled with JDK 21 against target/classes and target/maven-cache JARs. The old car harness retains its original hardware-independent checks.
- VehicleAudioDeviceCheck was compiled and executed through Python subprocess with JDK 21, target/classes, target/vehicle-audio-smoke and cached JAR classpath; ALSOFT_DRIVERS=intentionally-unavailable. Exit 0: output-device failure, update and repeated close are nonfatal. This CLI check is noninteractive and needs no browser playtest.
- VerifyVehicleAudioMedia was compiled and executed with JDK 21 against JCodec 0.2.5: VerifyVehicleAudioMedia dashboard/evidence/vehicle-audio-train.mp4 target/vehicle-audio-video-frames. Exit 0: all 5 H264 frames decoded, 960 x 540, 2.438 seconds, 4 changed frames, 163395 bytes. First, middle and last decoded frames were visually inspected and show train motion. Details: vehicle-audio-video-check.json.
- python3 deploy/check_vehicle_audio_pcm.py: exit 0. Four distinct stereo WAV captures, 22050 Hz, 16 bit, 1 second each; RMS 993.82–1181.62, no clipping. Details: vehicle-audio-pcm-check.json.

Playtest: python3 deploy/run_vehicle_audio_playtest.py --display "$DISPLAY": exit 0. Real Main on inherited assigned X11 display, Mesa software rendering, JDK 21, isolated synthetic profile in target/vehicle-audio-home. The harness inherits XAUTHORITY without reading its cookie. Audio was rendered by actual native OpenAL Soft loopback. This validates mixer output; physical speaker output was not tested.

Steps, expected and observed

1. Start parked: silent PCM. Press real J to enter, then W to accelerate: engine source plays and pitch rises. Driven-car screenshot shows 17 km/h.
2. Open/close Controls: mute then engine resume. Coast to a stop and press J to exit: engine silence. All assertions passed.
3. Load an isolated depot/two-station service: production railway simulation moves a train and native chuff PCM plays. At station dwell the chuff stops. Removing the train stays silent. Capture the moving train with the production F10 recorder. All assertions passed.
4. Start an isolated passenger jet at flight stage 2, clock 0: native takeoff PCM plays and the departing plane is visible. Stage 2 at clock 8, boarding, landing, view farther than 160 units, and plane removal all remain silent. Close twice safely. All assertions passed.

The car, train and takeoff PNGs were visually inspected. They show the tested vehicles in the synthetic scene and contain no private account data. WAVs are native output evidence, not microphone recordings or a claim of human listening. F10 video has no audio track; its matching train sound is vehicle-audio-train-moving.wav. Media and reports are saved directly in dashboard/evidence and pending controller publication on the assigned feature branch.

Limits and prior attempts

The full unscoped verify run was interrupted. A bounded offline full verify timed out after 240 seconds; no full-suite pass is claimed. Scoped offline verify passed the affected tests but lacked Maven packaging dependencies. The online scoped retry downloaded them and passed verify. Historical pre-recovery car playtest timed out after 180 seconds; its media was not used. Harness fixture errors were corrected before the successful native run. All delegate tasks are resolved; delegate statements were not used as execution evidence.

The independent reviewer must exercise the submitted source, inspect the published media and check full CI before merging.
