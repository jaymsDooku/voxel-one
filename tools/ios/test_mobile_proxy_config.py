"""Literal-renderer checks only; actual Nginx/TLS workflow lives in deploy/mobile."""
import importlib.util
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('mobile_proxy',ROOT/'deploy/mobile/render_proxy.py')
proxy=importlib.util.module_from_spec(spec);spec.loader.exec_module(proxy)


class MobileProxyConfigTests(unittest.TestCase):
    def test_literal_render_keeps_loopback_and_action_no_retry(self):
        config=proxy.render('mobile.example.invalid','/protected/public.pem','/protected/key.pem')
        self.assertNotIn('@@',config)
        self.assertIn('listen 443 ssl;',config)
        self.assertIn('proxy_pass http://127.0.0.1:25567;',config)
        self.assertIn('proxy_next_upstream off;',config)
        self.assertIn('access_log off;',config)
        self.assertIn('error_log /dev/null;',config)
        local=proxy.render('localhost','/test/cert.pem','/test/key.pem',30001,30002,True)
        self.assertIn('listen 127.0.0.1:30002 ssl;',local)

    def test_rejects_directive_injection_and_invalid_names_ports_paths(self):
        for host in ('','Example.com','a..b','a;return 200;#','*','.example','a'*64+'.test'):
            with self.subTest(host=host),self.assertRaises(ValueError):proxy.render(host,'/cert','/key')
        for path in ('relative','/a/../key','/key";return 200;#','/a\nkey','/a$key','/a key'):
            with self.subTest(path=path),self.assertRaises(ValueError):proxy.render('localhost','/cert',path)
        for port in (0,65536,True,'443'):
            with self.subTest(port=port),self.assertRaises(ValueError):proxy.render('localhost','/cert','/key',port)
