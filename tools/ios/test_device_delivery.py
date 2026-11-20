"""Linux-compatible mock device artifacts. No Apple signing or device execution."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import plistlib
import stat
import struct
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch
import warnings
import zipfile

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('device_delivery',ROOT/'ios/device-delivery.py')
delivery=importlib.util.module_from_spec(spec);spec.loader.exec_module(delivery)
HEAD='a'*40


def binary(platform=2,cpu=0x100000c):
    return struct.pack('<IIIIIIII',0xfeedfacf,cpu,0,2,1,24,0,0)+struct.pack('<IIIIII',0x32,24,platform,0,0,0)


class FakeXcode:
    def __init__(self):
        self.calls=[];self.dirty=False;self.change_source=False;self.head_calls=0;self.fail_sign=False
        self.app=None

    def __call__(self,args,**kwargs):
        self.calls.append((args,kwargs))
        if args[:3]==['git','rev-parse','HEAD']:
            self.head_calls+=1
            return 'b'*40 if self.change_source and self.head_calls>1 else HEAD
        if args[:3]==['git','status','--porcelain']:return ' M fixture.swift' if self.dirty else ''
        if args[:3]==['git','check-ignore','--quiet']:return ''
        if args[0]=='python3':return ''
        if args[:2]==['xcodebuild','archive']:
            archive=Path(args[args.index('-archivePath')+1]);self.app=archive/'Products/Applications/VoxelOne.app';self.app.mkdir(parents=True)
            version=next(x.split('=',1)[1] for x in args if x.startswith('MARKETING_VERSION='))
            build=next(x.split('=',1)[1] for x in args if x.startswith('CURRENT_PROJECT_VERSION='))
            info=dict(CFBundleIdentifier=delivery.BUNDLE,CFBundleExecutable='VoxelOne',CFBundleSupportedPlatforms=['iPhoneOS'],CFBundleShortVersionString=version,CFBundleVersion=build,MinimumOSVersion='16.0',DTSDKName='iphoneos18.5')
            (self.app/'Info.plist').write_bytes(plistlib.dumps(info));(self.app/'VoxelOne').write_bytes(binary())
            (self.app/'embedded.mobileprovision').write_bytes(b'MOCK profile, never a real provisioning profile')
            return ''
        if args[:2]==['xcodebuild','-exportArchive']:
            out=Path(args[args.index('-exportPath')+1]);out.mkdir()
            with zipfile.ZipFile(out/'mock.ipa','w') as z:
                for p in self.app.iterdir():z.write(p,'Payload/VoxelOne.app/'+p.name)
                z.writestr('SwiftSupport/iphoneos/mock.dylib',b'MOCK export support')
            return ''
        if args[0]=='codesign':
            if self.fail_sign:raise delivery.DeliveryError('signature_verification_failed')
            return ''
        raise AssertionError('Unexpected mock command')


class DeviceDeliveryTests(unittest.TestCase):
    def setUp(self):
        (ROOT/'target').mkdir(exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(prefix='device-delivery-mock-',dir=ROOT/'target');self.addCleanup(self.temp.cleanup)
        self.repo=Path(self.temp.name);self.fake=FakeXcode()

    def build(self,**kwargs):
        return delivery.build(self.repo,source_head=HEAD,version='1.2.3',build_number=kwargs.pop('build_number','7'),system='Darwin',run=self.fake,**kwargs)

    def signing(self):
        config=self.repo/'protected.xcconfig';config.write_text('// MOCK pre-provisioned config\n')
        options=self.repo/'protected.plist';options.write_bytes(plistlib.dumps(dict(teamID='MOCK_PRIVATE_TEAM',destination='upload',uploadSymbols=True,manageAppVersionAndBuildNumber=True)))
        return config,options

    def test_unsigned_device_archive_is_versioned_checked_and_never_installable(self):
        output,r=self.build()
        artifact=output/r['artifact'];self.assertTrue(artifact.is_file())
        self.assertEqual(hashlib.sha256(artifact.read_bytes()).hexdigest(),r['sha256'])
        self.assertEqual(r['sourceHead'],HEAD);self.assertEqual(r['version'],'1.2.3');self.assertEqual(r['build'],'7')
        self.assertFalse(r['signatureVerified']);self.assertEqual(r['installStatus'],'not_installable_unsigned')
        self.assertEqual(r['uploadStatus'],'not_attempted');self.assertEqual(r['physicalQualification'],'pending')
        self.assertEqual(output.stat().st_mode&0o777,0o700)
        self.assertIn(r['sha256'],(output/'SHA256SUMS').read_text())
        with self.assertRaisesRegex(delivery.DeliveryError,'already_exists'):self.build()

    def test_signed_modes_force_local_export_and_keep_protected_values_private(self):
        config,options=self.signing()
        for i,mode in enumerate(delivery.MODES[1:],1):
            output,r=self.build(mode=mode,signing_config=config,export_options=options,build_number=str(i))
            private=plistlib.loads((output/'ExportOptions.private.plist').read_bytes())
            self.assertEqual(private['destination'],'export');self.assertEqual(private['method'],mode)
            self.assertFalse(private['uploadSymbols']);self.assertFalse(private['manageAppVersionAndBuildNumber'])
            self.assertEqual((output/'ExportOptions.private.plist').stat().st_mode&0o777,0o600)
            self.assertNotIn('MOCK_PRIVATE_TEAM',json.dumps(r));self.assertNotIn(str(config),json.dumps(r))
            self.assertTrue(r['signatureVerified']);self.assertEqual(r['installStatus'],'not_attempted')
            self.assertEqual(r['uploadStatus'],'not_attempted')
        signs=[args for args,_ in self.fake.calls if args[0]=='codesign'];self.assertEqual(len(signs),6)
        self.assertTrue(all('anchor apple generic' in args for args in signs))
        self.assertFalse(any('-allowProvisioningUpdates' in args for args,_ in self.fake.calls))

    def test_macos_source_cleanliness_and_signing_inputs_are_real_gates(self):
        with self.assertRaisesRegex(delivery.DeliveryError,'mac_xcode_required'):
            delivery.build(self.repo,source_head=HEAD,version='1.2.3',build_number='7',system='Linux',run=self.fake)
        self.fake.dirty=True
        with self.assertRaisesRegex(delivery.DeliveryError,'exact_clean_source'):self.build()
        self.fake.dirty=False
        with self.assertRaisesRegex(delivery.DeliveryError,'protected_signing'):self.build(mode='debugging')
        self.assertFalse((self.repo/'ios/build/delivery').exists())

    def test_failed_signing_and_source_change_emit_no_success_receipt(self):
        config,options=self.signing();self.fake.fail_sign=True
        with self.assertRaisesRegex(delivery.DeliveryError,'signature_verification_failed'):
            self.build(mode='debugging',signing_config=config,export_options=options)
        self.assertFalse(list(self.repo.rglob('receipt.json')))
        self.fake=FakeXcode();self.fake.change_source=True
        with self.assertRaisesRegex(delivery.DeliveryError,'source_changed'):self.build(build_number='8')
        self.assertFalse(list(self.repo.rglob('receipt.json')))

    def test_binary_rejects_simulator_x86_and_truncated_load_commands(self):
        delivery.device_binary(io.BytesIO(binary()))
        for bad in (binary(platform=7),binary(cpu=0x1000007),binary()[:36],b'MOCK simulator app'):
            with self.assertRaises(delivery.DeliveryError):delivery.device_binary(io.BytesIO(bad))

    def test_device_metadata_version_and_profile_gates(self):
        output,_=self.build();app=output/'VoxelOne.xcarchive/Products/Applications/VoxelOne.app'
        info=plistlib.loads((app/'Info.plist').read_bytes())
        for key,value in [('CFBundleSupportedPlatforms',['iPhoneSimulator']),('CFBundleVersion','8'),('CFBundleIdentifier','wrong.bundle')]:
            changed=info|{key:value}
            with self.assertRaises(delivery.DeliveryError):delivery.app_metadata(changed,'1.2.3','7')
        (app/'embedded.mobileprovision').unlink()
        with self.assertRaisesRegex(delivery.DeliveryError,'profile_required'):
            delivery.verify_signature(app,self.fake,self.repo,output/'private.log')

    def test_ipa_path_alias_symlink_and_size_abuse_rejected_before_extract(self):
        for index,names in enumerate([['../outside'],['Payload//file'],['Payload/./file'],['/Payload/file'],['Other/file']]):
            ipa=self.repo/f'bad{index}.ipa'
            with zipfile.ZipFile(ipa,'w') as z:
                for name in names:z.writestr(name,b'fixture')
            with self.assertRaises(delivery.DeliveryError):delivery.unpack_ipa(ipa,self.repo/f'out{index}')
        ipa=self.repo/'symlink.ipa';entry=zipfile.ZipInfo('Payload/link');entry.external_attr=(stat.S_IFLNK|0o777)<<16
        with zipfile.ZipFile(ipa,'w') as z:z.writestr(entry,b'../outside')
        with self.assertRaises(delivery.DeliveryError):delivery.unpack_ipa(ipa,self.repo/'link-out')
        self.assertFalse((self.repo/'outside').exists())

    def test_cli_help_and_version_bounds(self):
        r=subprocess.run([sys.executable,str(ROOT/'ios/device-delivery.py'),'--help'],capture_output=True,text=True,timeout=5)
        self.assertEqual(r.returncode,0);self.assertIn('--source-head',r.stdout)
        for version,build in [('1.2','1'),('1.2.3','0'),('1.2.3','10000'),('../bad','1')]:
            with self.assertRaises(delivery.DeliveryError):delivery.version_fields(version,build)

    def test_ipa_duplicate_and_resource_caps_rejected(self):
        ipa=self.repo/'duplicate.ipa'
        with warnings.catch_warnings():
            warnings.simplefilter('ignore',UserWarning)
            with zipfile.ZipFile(ipa,'w') as z:
                z.writestr('Payload/file',b'a');z.writestr('Payload/file',b'b')
        with self.assertRaises(delivery.DeliveryError):delivery.unpack_ipa(ipa,self.repo/'duplicate-out')
        ipa=self.repo/'large.ipa'
        with zipfile.ZipFile(ipa,'w',zipfile.ZIP_DEFLATED) as z:z.writestr('Payload/file',b'x'*10000)
        with patch.object(delivery,'MAX_BYTES',512):
            with self.assertRaisesRegex(delivery.DeliveryError,'artifact_size_limit'):delivery.unpack_ipa(ipa,self.repo/'large-out')
        with patch.object(delivery,'MAX_FILES',0):
            with self.assertRaisesRegex(delivery.DeliveryError,'artifact_size_limit'):delivery.unpack_ipa(ipa,self.repo/'files-out')

    def test_ignored_output_parent_alias_is_rejected(self):
        (self.repo/'ios').mkdir();outside=self.repo/'other';outside.mkdir()
        (self.repo/'ios/build').symlink_to(outside,target_is_directory=True)
        with self.assertRaisesRegex(delivery.DeliveryError,'outside_source'):self.build()
        self.assertFalse(list(outside.iterdir()))

    @unittest.skipUnless(hasattr(os,'killpg'),'Device commands require POSIX/macOS process groups')
    def test_owned_command_deadline_fails_and_reaps_without_raw_output(self):
        start=time.monotonic()
        with self.assertRaisesRegex(delivery.DeliveryError,'command_timed_out'):
            delivery.command([sys.executable,'-c','import time;time.sleep(5)'],repo=self.repo,timeout=0.1)
        self.assertLess(time.monotonic()-start,2)
