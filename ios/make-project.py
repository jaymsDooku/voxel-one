#!/usr/bin/env python3
"""Generate an unsigned Xcode app/UI-test project using only the checked-out source."""
import argparse
import json
from pathlib import Path
from xml.sax.saxutils import escape

parser=argparse.ArgumentParser()
parser.add_argument('--gateway',default='')
args=parser.parse_args()
root=Path(__file__).resolve().parent
project=root/'build'/'VoxelOne.xcodeproj'
project.mkdir(parents=True,exist_ok=True)
objects={}
def add(kind,**fields):
    key=f'{len(objects)+1:024X}'
    objects[key]={'isa':kind,**fields}
    return key

def config(settings):
    return add('XCBuildConfiguration',buildSettings=settings,name='Debug')
def config_list(settings):
    return add('XCConfigurationList',buildConfigurations=[config(settings)],defaultConfigurationIsVisible='0',defaultConfigurationName='Debug')
def reference(path,kind):
    return add('PBXFileReference',lastKnownFileType=kind,name=path.name,path=str(path),sourceTree='<absolute>')
def phase(kind,files):
    return add(kind,buildActionMask='2147483647',files=[add('PBXBuildFile',fileRef=f) for f in files],runOnlyForDeploymentPostprocessing='0')

swift=[reference(root/'VoxelOne'/name,'sourcecode.swift') for name in ['App.swift','World.swift','Renderer.swift','Gateway.swift','GameController.swift','Graphics.swift']]
tests=[reference(root/'VoxelOneUITests'/name,'sourcecode.swift') for name in ['NativeWorkflowTests.swift','WorldRulesTests.swift','GraphicsRulesTests.swift']]
resource=reference(root/'Resources'/'sandbox.json','text.json')
app_product=add('PBXFileReference',explicitFileType='wrapper.application',path='VoxelOne.app',sourceTree='BUILT_PRODUCTS_DIR')
test_product=add('PBXFileReference',explicitFileType='wrapper.cfbundle',path='VoxelOneUITests.xctest',sourceTree='BUILT_PRODUCTS_DIR')
products=add('PBXGroup',children=[app_product,test_product],name='Products',sourceTree='<group>')
main_group=add('PBXGroup',children=swift+tests+[resource,products],sourceTree='<group>')
base={'SWIFT_VERSION':'5.0','IPHONEOS_DEPLOYMENT_TARGET':'16.0','SDKROOT':'iphoneos','TARGETED_DEVICE_FAMILY':'1','CODE_SIGNING_ALLOWED':'NO','CODE_SIGN_STYLE':'Automatic','SUPPORTED_PLATFORMS':'iphoneos iphonesimulator','CLANG_ENABLE_MODULES':'YES','SWIFT_OPTIMIZATION_LEVEL':'-Onone','ENABLE_TESTABILITY':'YES','PRODUCT_NAME':'$(TARGET_NAME)'}
app_settings=base|{'PRODUCT_BUNDLE_IDENTIFIER':'dev.jayms.voxelone.ios','INFOPLIST_FILE':str(root/'Info.plist'),'GENERATE_INFOPLIST_FILE':'NO','LD_RUNPATH_SEARCH_PATHS':'$(inherited) @executable_path/Frameworks'}
app=add('PBXNativeTarget',name='VoxelOne',productName='VoxelOne',productReference=app_product,productType='com.apple.product-type.application',buildConfigurationList=config_list(app_settings),buildPhases=[phase('PBXSourcesBuildPhase',swift),phase('PBXResourcesBuildPhase',[resource]),phase('PBXFrameworksBuildPhase',[])],dependencies=[],buildRules=[])
test_settings=base|{'PRODUCT_BUNDLE_IDENTIFIER':'dev.jayms.voxelone.UITests','GENERATE_INFOPLIST_FILE':'YES','TEST_TARGET_NAME':'VoxelOne','LD_RUNPATH_SEARCH_PATHS':'$(inherited) @executable_path/Frameworks @loader_path/Frameworks'}
# World.swift is compiled directly into the test runner for the same native model tests.
test=add('PBXNativeTarget',name='VoxelOneUITests',productName='VoxelOneUITests',productReference=test_product,productType='com.apple.product-type.bundle.ui-testing',buildConfigurationList=config_list(test_settings),buildPhases=[phase('PBXSourcesBuildPhase',tests+[swift[1],swift[5]]),phase('PBXFrameworksBuildPhase',[]),phase('PBXResourcesBuildPhase',[])],dependencies=[],buildRules=[])
project_id=add('PBXProject',attributes={'LastUpgradeCheck':'1600','TargetAttributes':{app:{'CreatedOnToolsVersion':'16.0'},test:{'CreatedOnToolsVersion':'16.0','TestTargetID':app}}},buildConfigurationList=config_list(base),compatibilityVersion='Xcode 14.0',developmentRegion='en',hasScannedForEncodings='0',knownRegions=['en','Base'],mainGroup=main_group,productRefGroup=products,projectDirPath='',projectRoot='',targets=[app,test])
proxy=add('PBXContainerItemProxy',containerPortal=project_id,proxyType='1',remoteGlobalIDString=app,remoteInfo='VoxelOne')
objects[test]['dependencies']=[add('PBXTargetDependency',target=app,targetProxy=proxy)]

def encode(value):
    if isinstance(value,dict):return '{ '+ ' '.join(json.dumps(k)+ ' = '+encode(v)+';' for k,v in value.items())+' }'
    if isinstance(value,list):return '( '+', '.join(encode(v) for v in value)+' )'
    return json.dumps(str(value))
(project/'project.pbxproj').write_text('// !$*UTF8*$!\n'+encode({'archiveVersion':'1','classes':{},'objectVersion':'56','objects':objects,'rootObject':project_id})+'\n')
schemes=project/'xcshareddata'/'xcschemes';schemes.mkdir(parents=True,exist_ok=True)
def buildable(identifier,name,product):
    return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{identifier}" BuildableName="{product}" BlueprintName="{name}" ReferencedContainer="container:VoxelOne.xcodeproj"/>'
app_ref=buildable(app,'VoxelOne','VoxelOne.app');test_ref=buildable(test,'VoxelOneUITests','VoxelOneUITests.xctest')
(schemes/'VoxelOne.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
<BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{app_ref}</BuildActionEntry>
<BuildActionEntry buildForTesting="YES" buildForRunning="NO" buildForProfiling="NO" buildForArchiving="NO" buildForAnalyzing="YES">{test_ref}</BuildActionEntry>
</BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="NO">
<Testables><TestableReference skipped="NO">{test_ref}</TestableReference></Testables>
<EnvironmentVariables><EnvironmentVariable key="VOXEL_TEST_GATEWAY" value="{escape(args.gateway, {'"':'&quot;'})}" isEnabled="YES"/></EnvironmentVariables>
</TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Debug"><BuildableProductRunnable runnableDebuggingMode="0">{app_ref}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Debug" revealArchiveInOrganizer="YES"/>
</Scheme>''')
print(project)
