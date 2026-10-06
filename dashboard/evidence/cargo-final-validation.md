# Cargo vehicles: final validation after recovery

Status: historical validation for the earlier cargo checkout. It does not validate the current aviation-base recovery. Final-head checks and new media remain pending controller continuation.

Tested game source: 382319295068cad4eff11a513a28ef18f8f89252. The rebase has finished. No game source was changed during this validation. The native harness fixture now closes the old test world and loads complete flat columns from -6 through 6 on both axes. Source and media hashes are in cargo-final-verification.json.

The requested container truck, liquid tanker, delivery van and goods lorry remain available through Shift + the configured vehicle key (default J) near a parked vehicle. J enters/exits; WASD and mouse drive/steer; Ctrl boosts. Each body has its own collision dimensions, cab seat, safe exit and saved type. One offline vehicle slot is supported. Cargo economy accounting and multiplayer cargo vehicles were not added.

## Historical recovery checks

Protocol.VERSION remains 21. CitySimulation reads and writes format-11 magic 0x4349543B. Inspected the resulting source and verified 713 existing base source/test/fixture/evidence/deploy files by Git blob hash against the continued head's parent, excluding the intended cargo-modified paths. All matched. Correction: this comparison used the older regional base only. The blanket no-deletion claim is retracted against aviation base 1ecbf565c449d7b5c96a621310c1a012b80ae1aa: head 5c33d5cc deleted aviation files, downgraded protocol 22 and rejected format-12 saves. The current recovery retains aviation and protocol 22/format 12; pre-continuation checks are recorded in cargo-aviation-recovery.md. All prior tracked evidence is retained unchanged; new captures have cargo-final-* names.

## Actual commands

```sh
mvn -q -Dmaven.repo.local=target/m2 -DargLine="-Djava.io.tmpdir=$PWD/target/test-tmp" -Dtest=RegionalPopulationTest,RegionalSaveCompatibilityTest,RegionalMultiplayerTest,JeepTest,ControlsTest test > target/cargo-final-test-output.txt 2>&1
python3 target/run_final_cargo_smoke.py --display "$DISPLAY"
java -Djava.io.tmpdir="$PWD/target/tmp" --class-path target/m2/org/jcodec/jcodec/0.2.5/jcodec-0.2.5.jar dashboard/evidence/CargoFinalClipCheck.java dashboard/evidence/cargo-final-container-drive.mp4
git diff --check
```

The temporary native wrapper uses deploy/run_cargo_smoke.py unchanged except its output destinations. It can be reproduced without overwriting prior evidence:

```python
from pathlib import Path
s = Path('deploy/run_cargo_smoke.py').read_text()
s = s.replace("root/'dashboard/evidence/cargo-placement-playtest.json'", "root/'dashboard/evidence/cargo-final-playtest.json'")
s = s.replace("root/'dashboard/evidence'/name", "root/'dashboard/evidence'/name.replace('cargo-', 'cargo-final-', 1)")
s = s.replace("root/'dashboard/evidence/cargo-container-drive.mp4'", "root/'dashboard/evidence/cargo-final-container-drive.mp4'")
Path('target/run_final_cargo_smoke.py').write_text(s)
```

Linux, Java 17-compatible source, LWJGL, inherited role DISPLAY/XAUTHORITY, X11, Mesa software rendering, 1280 × 720 window. Synthetic offline profile under target/cargo-home; complete flat test yard; no real save or account data. Camera inspection uses the existing city zoom range. Browser testing does not apply to this native Java/OpenGL client or its save/protocol integrations.

Maven exit 0. RegionalPopulationTest 9, RegionalSaveCompatibilityTest 4, RegionalMultiplayerTest 1, JeepTest 8, ControlsTest 5: 27 tests, zero failures/errors/skips. Format-10 fixtures load and upgrade to format 11; paved lanes and one million regional residents survive reload; truncated saves are rejected. Regional multiplayer and legacy Jeep regressions pass. No full-suite pass is claimed.

## Playtest: real-key native workflow

Main.run and the production renderer ran. xdotool sent real J, Shift+J and W input. Reflection created the isolated yard, positioned the player/camera between scenarios, and read observed state. Gameplay commands used the production input path.

| Steps | Expected | Observed |
| --- | --- | --- |
| Cycle Jeep, container truck, tanker, van and lorry with Shift+J | Distinct bodies; return to Jeep | Passed for all five; fresh PNGs saved |
| Enter with J; hold W until speed reaches 1.5 blocks/s | Driving; movement over 0.2 blocks | Passed for every body |
| Shift+J while driving | Body stays unchanged | Passed for every body |
| J while W remains held | Moving exit rejected | Passed for every body |
| Release W, wait for speed at most 0.05 blocks/s, then J | Safe exit | Passed for every body |
| J from the cab exit, then J again | Safe re-entry and second exit | Passed for every body |
| Save and reload each body | Type preserved | Passed for every body |
| Return to Jeep, add wall 4 blocks behind its centre, press Shift+J | Larger container body rejected | Passed; Jeep retained |

Native wrapper exit 0. The first native attempt ended with exit 143 before completion and is not counted as passed. The successful retry used the fixture cleanup and complete flat columns described above.

## Fresh media inspected

cargo-final-container.png, cargo-final-tanker.png, cargo-final-van.png, cargo-final-lorry.png and cargo-final-jeep.png were captured and visually inspected. The container has ribs and rear doors; the tanker has a stepped tank and fittings; the van is smaller with two axles; the lorry has a separate blue cab, cargo box and three axles. The Jeep capture provides a regression view.

cargo-final-container-drive.mp4 was captured by the production F10 recorder: 26 frames, 19.891 seconds, 1,004,273 bytes, below 6 MB. JCodec decoded and inspected samples at 20%, 45%, 80% and 95%. The 45% sample shows 9 km/h and the rejected-exit notice; the 95% sample shows the parked container truck. The software run shows about 1–3 FPS. These are functional checks, not performance claims.

Older cargo/regional media and reports remain historical evidence. These new files validate the continued game source and the current native harness. No commit, push, merge or deployment was performed by the developer. Independent exact-head review and publication verification remain with the controller/reviewer.
