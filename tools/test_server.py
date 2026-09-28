"""No network, no Java process, no production paths."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import server


class BootstrapTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.mock = patch.object(server, 'ROOT', self.root)
        self.mock.start()

    def tearDown(self):
        self.mock.stop()
        self.temp.cleanup()

    def test_normal_path(self):
        self.assertEqual(server.safe_path('mods/a.jar'), self.root / 'mods/a.jar')

    def test_traversal(self):
        for path in ('../secret', '/absolute', 'C:/absolute', 'a\\b', 'mods/../../secret'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                server.safe_path(path)

    def test_verify(self):
        path = self.root / 'test.jar'
        path.write_bytes(b'test')
        lock = {'files': [{'path': 'test.jar', 'size': 4, 'sha256': hashlib.sha256(b'test').hexdigest()}], 'external': []}
        server.verify(lock)
        path.write_bytes(b'FAIL')
        with self.assertRaises(RuntimeError):
            server.verify(lock)

    def test_missing_asset(self):
        with self.assertRaises(RuntimeError):
            server.verify({'files': [{'path': 'missing.jar', 'size': 1, 'sha256': 'x'}], 'external': []})

    def test_retired_asset_rejected(self):
        (self.root / 'old.jar').write_bytes(b'old')
        with self.assertRaisesRegex(RuntimeError, 'Retired asset'):
            server.verify({'files': [], 'external': [], 'retired': [{'path': 'old.jar'}]})

    def test_public_offline_rejected(self):
        (self.root / 'server.properties').write_text('server-ip=0.0.0.0\nonline-mode=false\n')
        with self.assertRaisesRegex(RuntimeError, 'Public binding'):
            server.command(None, {})

    def test_eula_explicit(self):
        (self.root / 'server.properties').write_text('server-ip=127.0.0.1\nonline-mode=false\n')
        with self.assertRaisesRegex(RuntimeError, 'EULA'):
            server.command(None, {})

    def test_cache_checksum(self):
        path = self.root / 'cached'
        path.write_bytes(b'cached')
        with patch.object(server.urllib.request, 'urlopen', side_effect=AssertionError('Network must not be used')):
            self.assertEqual(server.fetch({'sha256': server.digest(path)}, path), path)

    def test_java_version_validation(self):
        class Result:
            returncode = 0
            stderr = 'openjdk version "17.0.1"'
            stdout = ''
        with patch.object(server.subprocess, 'run', return_value=Result()):
            with self.assertRaisesRegex(RuntimeError, 'JDK 21'):
                server.java_binary('java')


if __name__ == '__main__':
    unittest.main()
