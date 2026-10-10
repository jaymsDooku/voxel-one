#!/usr/bin/env bash
# Native exact-source build and normal-control playtests, including local graphics drafts, persistence and the opt-in planetary atmosphere.
set -euo pipefail
udid="${1:?simulator UDID required}"
evidence="${2:?absolute evidence directory required}"
head="${3:?exact source head required}"
repo="$(cd "$(dirname "$0")/.." && pwd)"
cd "$repo"
export IOS_CHECK_STAGE=source
export IOS_EVIDENCE_DIR="$evidence"
fixture_pid=""
video_pid=""
cleanup() {
  result=$?
  if [ -n "$video_pid" ]; then kill -INT "$video_pid" 2>/dev/null || true; wait "$video_pid" 2>/dev/null || true; fi
  if [ -n "$fixture_pid" ]; then kill "$fixture_pid" 2>/dev/null || true; wait "$fixture_pid" 2>/dev/null || true; fi
  if [ "$result" -ne 0 ]; then
    python3 ios/check-results.py failure "$repo" "$evidence" "$head" "$udid" || true
  fi
  exit "$result"
}
trap cleanup EXIT
python3 - "$repo" "$evidence" "$head" "$udid" <<'PY'
import pathlib,re,subprocess,sys,uuid
repo,evidence,head,udid=sys.argv[1:]
assert re.fullmatch('[0-9a-f]{40}',head)
assert str(uuid.UUID(udid)).lower()==udid.lower()
assert pathlib.Path(evidence).is_absolute() and pathlib.Path(evidence).resolve().is_relative_to(pathlib.Path(repo).resolve())
assert subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()==head
assert not subprocess.check_output(['git','diff','--name-only'],text=True).strip()
assert not subprocess.check_output(['git','diff','--cached','--name-only'],text=True).strip()
PY
mkdir -p ios/build
export IOS_CHECK_STAGE=java_build
mvn -q -DskipTests compile > ios/build/java-build.log 2>&1
export IOS_CHECK_STAGE=synthetic_game_servers
# The test profile must be new. Secrets generated in this ignored directory never enter evidence.
profile="$(mktemp -d "$repo/ios/build/fixture-parent.XXXXXX")"
java -cp target/classes dev.jayms.net.mobile.MobileFixtureHost "$profile/profile" "$profile/ready.json" > ios/build/fixture.log 2>&1 &
fixture_pid=$!
for _ in $(seq 1 90); do
  if [ -f "$profile/ready.json" ]; then break; fi
  kill -0 "$fixture_pid"
  sleep 1
done
export VOXEL_TEST_GATEWAY
VOXEL_TEST_GATEWAY="$(python3 - "$profile/ready.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1]))['gateway'])
PY
)"
python3 ios/make-project.py --gateway "$VOXEL_TEST_GATEWAY" > ios/build/project-path.txt
export IOS_CHECK_STAGE=native_build
xcodebuild build-for-testing -project ios/build/VoxelOne.xcodeproj -scheme VoxelOne \
  -sdk iphonesimulator -destination "platform=iOS Simulator,id=$udid" \
  -derivedDataPath ios/build/DerivedData CODE_SIGNING_ALLOWED=NO \
  > ios/build/native-build.log 2>&1
app="$repo/ios/build/DerivedData/Build/Products/Debug-iphonesimulator/VoxelOne.app"
export IOS_CHECK_STAGE=install
xcrun simctl install "$udid" "$app"
# Record only this role's assigned device. Raw video stays in ignored build output.
if command -v ffmpeg >/dev/null 2>&1; then
  xcrun simctl io "$udid" recordVideo --codec=h264 "$repo/ios/build/native-workflow.mp4" > ios/build/recorder.log 2>&1 &
  video_pid=$!
fi
export IOS_CHECK_STAGE=native_playtests
xcodebuild test-without-building -project ios/build/VoxelOne.xcodeproj -scheme VoxelOne \
  -destination "platform=iOS Simulator,id=$udid" -derivedDataPath ios/build/DerivedData \
  -parallel-testing-enabled NO -maximum-concurrent-test-simulator-destinations 1 \
  -resultBundlePath ios/build/NativeTests.xcresult CODE_SIGNING_ALLOWED=NO \
  > ios/build/native-tests.log 2>&1
if [ -n "$video_pid" ]; then
  kill -INT "$video_pid" 2>/dev/null || true; wait "$video_pid" || true;video_pid=""
fi
export IOS_CHECK_STAGE=evidence
# Export XCTest's actual app screenshots. Do not publish the xcresult or raw logs.
xcrun xcresulttool export attachments --path ios/build/NativeTests.xcresult --output-path ios/build/attachments > ios/build/export.log 2>&1
xcrun xcresulttool get test-results summary --path ios/build/NativeTests.xcresult > ios/build/test-summary.json
python3 ios/check-results.py success "$repo" "$evidence" "$head" "$udid"
if command -v ffmpeg >/dev/null 2>&1 && [ -s ios/build/native-workflow.mp4 ]; then
  ffmpeg -hide_banner -loglevel error -y -ss 10 -i ios/build/native-workflow.mp4 -t 40 \
    -vf 'scale=540:-2' -r 15 -c:v libx264 -preset fast -crf 30 -an -movflags +faststart \
    "$evidence/client-playtest.mp4" > ios/build/compression.log 2>&1 || true
fi
python3 - "$evidence/client-result.json" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1]);data=json.loads(p.read_text());video=p.parent/'client-playtest.mp4'
if video.is_file():
 if 16<video.stat().st_size<6_000_000:data['videos']=[video.name]
 else:video.unlink()
p.write_text(json.dumps(data,indent=2)+'\n')
PY
export IOS_CHECK_STAGE=completed
