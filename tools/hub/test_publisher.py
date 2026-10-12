import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('publisher',Path(__file__).with_name('publish_catalog.py'))
publisher=importlib.util.module_from_spec(spec);spec.loader.exec_module(publisher)

class PublisherTest(unittest.TestCase):
    def test_independent_updates_and_unpublished_rejection(self):
        with tempfile.TemporaryDirectory(dir='target/hub-tmp') as directory:
            root=Path(directory); native=root/'native.json';web=root/'web.json';output=root/'catalog.properties'
            n={'tested':True,'published':True,'source':'a'*40,'build':'playtest-1','notes':'Public native release'}
            w={'tested':True,'published':True,'source':'b'*40,'build':'web-1','notes':'Public web release','url':'https://voxel-one.jamesleaver1.chatgpt.site/games/space-invaders/'}
            native.write_text(json.dumps(n));web.write_text(json.dumps(w));publisher.assemble(native,web,output);before=output.read_text()
            w['build']='web-2';web.write_text(json.dumps(w));publisher.assemble(native,web,output);after=output.read_text()
            self.assertIn('city.build=playtest-1',after);self.assertIn('space-invaders.build=web-2',after)
            self.assertNotEqual(before,after)
            n['build']='playtest-2';n['source']='c'*40;native.write_text(json.dumps(n));publisher.assemble(native,web,output);after=output.read_text()
            self.assertIn('city.build=playtest-2',after);self.assertIn('space-invaders.build=web-2',after)
            n['published']=False;native.write_text(json.dumps(n))
            with self.assertRaises(ValueError):publisher.assemble(native,web,output)
            self.assertEqual(after,output.read_text())

if __name__=='__main__':unittest.main()
