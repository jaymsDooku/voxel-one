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
fixture_running() {
  local job_pid
  for job_pid in $(jobs -pr); do
    if [ "$job_pid" = "$fixture_pid" ]; then return 0; fi
  done
  return 1
}
cleanup() {
  result=$?
  if [ -n "$fixture_pid" ]; then
    if fixture_running; then kill "$fixture_pid" 2>/dev/null || true; fi
    for _ in $(seq 1 50); do
      if ! fixture_running; then break; fi
      sleep 0.1
    done
    if fixture_running; then kill -KILL "$fixture_pid" 2>/dev/null || true; fi
    wait "$fixture_pid" 2>/dev/null || true
  fi
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
phase() {
  export IOS_CHECK_STAGE="$1"
  # Fixed literals supplied below; no form, command or environment values.
  printf '\nerror: native_client_phase stage=%s\n' "$1" >> ios/build/native-build.log
}
phase java_build
mvn -q -U -DskipTests compile > ios/build/java-build.log 2>&1
phase terrain_contract
java -cp target/classes dev.jayms.net.mobile.NativeTerrainFixtures ios/build/terrain-fixtures.json
phase terrain_contract_compare
cmp ios/Resources/terrain-fixtures.json ios/build/terrain-fixtures.json
phase synthetic_game_servers
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
redirect_gateway="$(python3 - "$profile/ready.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1]))['redirectGateway'])
PY
)"
python3 ios/make-project.py --gateway "$VOXEL_TEST_GATEWAY" --redirect-gateway "$redirect_gateway" > ios/build/project-path.txt
phase device_archive
bash ios/archive-device.sh >> ios/build/native-build.log 2>&1
# Restore the synthetic gateway environment in the generated test scheme.
python3 ios/make-project.py --gateway "$VOXEL_TEST_GATEWAY" --redirect-gateway "$redirect_gateway" > ios/build/project-path.txt
phase native_build
xcodebuild build-for-testing -project ios/build/VoxelOne.xcodeproj -scheme VoxelOne \
  -sdk iphonesimulator -destination "platform=iOS Simulator,id=$udid" \
  -derivedDataPath ios/build/DerivedData CODE_SIGNING_ALLOWED=NO \
  >> ios/build/native-build.log 2>&1
app="$repo/ios/build/DerivedData/Build/Products/Debug-iphonesimulator/VoxelOne.app"
phase install
xcrun simctl install "$udid" "$app"
# The test runner owns/stops/reaps this role's recorder with a bounded deadline.
phase native_playtests
python3 ios/native-test-runner.py --timeout 1500 --simulator "$udid" -- xcodebuild test-without-building -project ios/build/VoxelOne.xcodeproj -scheme VoxelOne \
  -destination "platform=iOS Simulator,id=$udid" -derivedDataPath ios/build/DerivedData \
  -parallel-testing-enabled NO -maximum-concurrent-test-simulator-destinations 1 \
  -resultBundlePath ios/build/NativeTests.xcresult CODE_SIGNING_ALLOWED=NO
phase evidence
# Export XCTest's actual app screenshots. Do not publish the xcresult or raw logs.
xcrun xcresulttool export attachments --path ios/build/NativeTests.xcresult --output-path ios/build/attachments > ios/build/export.log 2>&1
xcrun xcresulttool get test-results summary --path ios/build/NativeTests.xcresult > ios/build/test-summary.json
python3 ios/check-results.py success "$repo" "$evidence" "$head" "$udid"
# The fixed, passing touch-settings test supplies only numeric timing. AVFoundation is
# present on the hosted Mac; lack of an optional ffmpeg install must not omit video.
phase video_export
python3 - "$evidence/client-playtest.mp4" <<'PY'
import os,signal,subprocess,sys
from pathlib import Path
root=Path.cwd()
try:
 # Compiler errors use the existing bounded, trusted build-diagnostic channel.
 with (root/'ios/build/native-build.log').open('ab') as output:
  process=subprocess.Popen(['swift','-swift-version','5','ios/export-playtest.swift',str(root/'ios/build/native-workflow.mp4'),sys.argv[1],str(root/'ios/build/native-video-window.json')],stdout=output,stderr=subprocess.STDOUT,start_new_session=True)
  try:
   code=process.wait(timeout=60)
  except subprocess.TimeoutExpired:
   # The compiler/interpreter group belongs only to this export request.
   try:os.killpg(process.pid,signal.SIGKILL)
   except ProcessLookupError:pass
   process.wait(timeout=2)
   raise
  if code:raise subprocess.CalledProcessError(code,process.args)
except (subprocess.TimeoutExpired,subprocess.CalledProcessError):
 with (root/'ios/build/native-build.log').open('a') as output:output.write('\nerror: native_video_export status=failed_or_timed_out\n')
 raise SystemExit(74)
PY
python3 - "$evidence/client-result.json" <<'PY'
import json,pathlib,sys
p=pathlib.Path(sys.argv[1]);data=json.loads(p.read_text());video=p.parent/'client-playtest.mp4'
if video.is_file():
 if 16<video.stat().st_size<6_000_000:data['videos']=[video.name]
 else:video.unlink()
p.write_text(json.dumps(data,indent=2)+'\n')
PY
# Leave the actual saved native world visible for the independent capture.
xcrun simctl terminate "$udid" dev.jayms.voxelone.ios || true
xcrun simctl launch "$udid" dev.jayms.voxelone.ios
sleep 2
phase completed
