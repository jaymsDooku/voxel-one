Vehicle audio final validation after review recovery

Tested source head: 4c6f8033c7b1bd4c77c733edfd514a4e6a34a810. Production source is unchanged during this validation. Controller publication and independent review remain. No developer commit, push, merge or deployment was performed.

Review fixes verified

RoadRoute.points retains direct endpoints at any heading; railPoints retains cardinal rail legs. RoadRoute, RoadGeometry, CityAddresses, CityTools, RoadTypesTest, RoadWorkflowTest, RejectedRoadWorkflowSmoke, run_noncardinal_smoke.py and all noncardinal historical evidence match restored base 042677b. Git diff over these paths is empty. No deleted road regressions or tracked .lwjgl binaries remain. /.lwjgl/ is ignored, and freshly extracted audio libraries are under ignored target/lwjgl-natives. The extraction cache was removed before the fresh audio playtest to verify clean extraction.

Actual commands and results

- JAVA_HOME=/usr/lib/jvm/jdk-21.0.5-oracle-x64 timeout 180s mvn -o -q -Dmaven.repo.local=target/maven-cache -Djava.io.tmpdir="$PWD/target/tmp" -DargLine="-Djava.io.tmpdir=$PWD/target/tmp -Dorg.lwjgl.system.SharedLibraryExtractPath=$PWD/target/lwjgl-natives" -Dtest=VehicleAudioTest,AviationTest,JeepTest,RailwayTest,IsometricCameraTest,CitySavesTest,RailwaySaveCompatibilityTest,RoadTypesTest,RoadWorkflowTest,RoadBaseCompatibilityTest verify: exit 0. 53 tests, 0 failures, 0 errors, 0 skipped. Client, server, launcher and normal JAR packages built. This is scoped verification; no full-suite pass is claimed.
- /usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/javac -cp target/classes -d target/recovery-checks target/DiagonalRoadRecoveryCheck.java; /usr/lib/jvm/jdk-21.0.5-oracle-x64/bin/java -cp target/classes:target/recovery-checks DiagonalRoadRecoveryCheck: both exit 0. Exact road (150,150) -> (170,157) remains two endpoints, with no (170,150) bend; rails retain the three cardinal points. This CLI integration has no browser workflow.
- python3 deploy/check_vehicle_audio_pcm.py: exit 0. Four distinct stereo PCM WAVs, 22050 Hz, 16 bit, 1 second each, audible RMS above 100 and no clipping. See vehicle-audio-pcm-check.json.
- VerifyVehicleAudioMedia dashboard/evidence/vehicle-audio-train.mp4 target/audio-final-video-frames, executed with JDK 21 and JCodec 0.2.5 classpath: exit 0. All 4 frames decoded, 3 changed frames, 960 x 540 H264, 3.304 seconds, 152295 bytes. First/middle/last decoded images inspected; train motion visible.
- VerifyVehicleAudioMedia dashboard/evidence/audio-final-road-playtest.mp4 target/audio-final-road-video-frames, same decoder/classpath: exit 0. All 178 frames decoded, 177 changed frames, 960 x 540 H264, 106.787 seconds, 4307626 bytes. First/middle/last decoded images inspected. Both clips are below 6000000 bytes.
- VehicleAudioDeviceCheck compiled and executed with JDK 21, target/classes, target/vehicle-audio-smoke and cached JAR classpath, -Dorg.lwjgl.system.SharedLibraryExtractPath=$PWD/target/device-check-natives, and ALSOFT_DRIVERS=intentionally-unavailable: exit 0. Missing-device update and repeated close remain nonfatal. This standalone CLI check has no browser workflow.
- git diff --check: exit 0. No unmerged paths. git ls-files .lwjgl: empty. git check-ignore target/lwjgl-natives/liblwjgl.so confirms the cache is ignored.

Playtest: audio

python3 deploy/run_vehicle_audio_playtest.py --display "$DISPLAY": exit 0. Real Main, inherited assigned X11 DISPLAY and XAUTHORITY, Mesa software rendering, JDK 21, isolated synthetic profile target/vehicle-audio-home. No authentication cookie was read. Audio is actual native OpenAL Soft loopback PCM; physical speaker output was not tested.

Observed: J enters car; W accelerates and raises engine pitch; menu mutes and resumes audio; stopped J exit silences engine. Parked state is silent. Automatic depot/two-station service moves a train with chuff PCM; station dwell and removal are silent. Plane departure stage 2 clock 0 produces takeoff PCM; cruise at clock 8, boarding, landing, distant view and removal remain silent. Repeated close passes. All assertions passed. Fresh car/train/takeoff images were inspected. Fresh F10 train video has no audio track; native train WAV supplies the sound evidence.

Playtest: restored road regression

python3 target/run_audio_final_road.py --display "$DISPLAY" --scenario road: exit 0. Uses preserved RejectedRoadWorkflowSmoke in real Main on the assigned X11 display, Mesa, JDK 21 and fresh isolated profile target/audio-final-road-home. The wrapper is derived from deploy/run_noncardinal_smoke.py with only output prefix audio-final-, target/maven-cache, explicit JDK 21 executables and target native extraction path changed. Original source, smoke scripts and historical evidence remain untouched.

Observed: budget rejection retains the road anchor; retry fills the gap; success advances the chain. Selection, edit and deletion pass. A direct diagonal is built, widened while retaining heading and deleted. Shallow headings for all road types contain no cardinal leg. Fresh screenshots of the diagonal, widened diagonal and shallow roads were inspected. Fresh F10 video decoded fully. All assertions passed.

All captures are from this recovered implementation and are sanitized synthetic scenes. Metadata/source checks and fresh media are saved directly in dashboard/evidence. Intended raw GitHub links use the assigned feature branch and remain pending controller publication. All helper tasks are resolved; no delegated finding was treated as an executed check.
