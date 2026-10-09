#!/usr/bin/env python3
"""Write sanitized native workflow results; raw logs/xcresult never become artifacts."""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

mode,repo_arg,out_arg,head,udid=sys.argv[1:]
repo=Path(repo_arg);out=Path(out_arg);build=repo/'ios/build'
out.mkdir(parents=True,exist_ok=True)
if mode=='failure':
    report={'sourceHead':head,'simulatorUdid':udid,'application':'voxel-one','status':'failed','stage':os.environ.get('IOS_CHECK_STAGE','unknown')}
    # Compiler diagnostics and XCTest assertion text only. No runtime or fixture logs.
    diagnostics=[]
    for filename in ('java-build.log','native-build.log','native-tests.log'):
        p=build/filename
        if not p.is_file():continue
        for line in p.read_text(errors='replace').splitlines():
            if 'error:' in line and ('/ios/' in line or 'Test Case' in line or 'XCTAssert' in line or 'failed' in line):
                diagnostics.append(line.replace(str(repo),'SOURCE')[:500])
    report['diagnostics']=diagnostics[:30]
    result=build/'NativeTests.xcresult'
    if result.exists():
        exported=build/'failure-attachments'
        done=subprocess.run(['xcrun','xcresulttool','export','attachments','--path',str(result),'--output-path',str(exported)],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=90)
        if done.returncode==0:
            for index,p in enumerate(sorted(exported.rglob('*.png'))):
                if p.stat().st_size<=6_000_000:shutil.copyfile(p,out/f'native-failure-{index+1:02d}.png')
    (out/'client-failure.json').write_text(json.dumps(report,indent=2)+'\n')
    sys.exit(0)
summary=json.loads((build/'test-summary.json').read_text())
# Xcode's structured result is authoritative. An empty, skipped or partial run fails acceptance.
assert summary.get('result')=='Passed', 'native test result did not pass'
assert summary.get('failedTests')==0 and summary.get('passedTests',0)>=7, 'required native workflow tests did not all run'
assert summary.get('skippedTests',0)==0, 'native workflow test was skipped'
attachments=build/'attachments'
files=list(attachments.rglob('*.png'))
assert files,'actual app screenshots missing'
media=[]
# Exported filenames are unique XCTest identifiers, kept without relabelling the scenario.
for index,p in enumerate(sorted(files)):
    if p.stat().st_size>6_000_000:raise RuntimeError('screenshot exceeds evidence size limit')
    name=f'native-workflow-{index+1:02d}.png';shutil.copyfile(p,out/name);media.append(name)
log=(build/'native-tests.log').read_text(errors='replace')
required=['testOfflineTouchBuildSavePauseAndLandscape','testOnlineLoginCityPlanningAndReconnect','testCollisionJumpRayAndEditRules','testSnapshotValidationAndSaveRoundTrip','testAtmosphereProfileMigrationAndNumericalBounds','testPlanetAtmosphereNativeToggleAndLegacyWorld','testNativeSkyVisibilityBlocksRoofsAndTransmitsWindows']
assert all(any(name in line and 'passed' in line for line in log.splitlines()) for name in required),'required tests missing from native run'
tests=[{'name':name,'status':'passed'} for name in required]
receipt={'sourceHead':head,'simulatorUdid':udid,'application':'voxel-one','evidenceType':'native-client','synthetic':False,'status':'passed','bundleId':'dev.jayms.voxelone.ios','appPath':'ios/build/DerivedData/Build/Products/Debug-iphonesimulator/VoxelOne.app','tests':tests,'videos':[]}
(out/'client-result.json').write_text(json.dumps(receipt,indent=2)+'\n')
(out/'native-workflow-results.json').write_text(json.dumps({'sourceHead':head,'environment':'GitHub-hosted macOS; supplied fresh iPhone Simulator; synthetic Java game profiles','tests':tests,'screenshots':media,'steps':['Offline: start native sandbox; break and place blocks; hold movement; drag look; jump; pause; empty-slot rejection; resume save; cold restart; landscape layout.','Online: sign in to synthetic city; inspect authoritative clock/treasury; reject missing road points; tap road endpoints; submit server command; walk; sign out and reconnect.','Regression: native AABB collision, jump, voxel ray, inventory, duplicate-world rejection and save encoding round trip.'],'limits':['Simulator evidence does not prove physical iPhone GPU, thermal, signing or distribution readiness.','Online phone access needs an HTTPS reverse proxy for the loopback mobile gateway.'],'commands':['xcodebuild build-for-testing -sdk iphonesimulator -destination platform=iOS Simulator,id=ASSIGNED_UDID','xcodebuild test-without-building -destination platform=iOS Simulator,id=ASSIGNED_UDID -parallel-testing-enabled NO'],'playtest':'Actually executed UIKit controls in the installed SceneKit native app; all seven XCTest cases passed.'},indent=2)+'\n')
