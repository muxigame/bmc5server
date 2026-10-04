import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import local_rebuild as rebuild
from test_npc_rebuild import row


class DownloadTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / 'repo'
        self.root.mkdir()
        self.record = dict(row('mods/original.jar', b'verified build'), modId='customnpcs',
            sourceProject='source', artifact='dist/npc.jar', url='https://example.test/npc.jar')

    def test_download_and_cache_require_no_local_source(self):
        with patch('urllib.request.urlopen', return_value=io.BytesIO(b'verified build')) as request:
            path = rebuild.download_rebuild(self.root, self.record)
            self.assertEqual(path.read_bytes(), b'verified build')
            self.assertEqual(rebuild.download_rebuild(self.root, self.record), path)
            self.assertEqual(request.call_count, 1)

    def test_bad_digest_is_not_cached(self):
        with patch('urllib.request.urlopen', return_value=io.BytesIO(b'wrong build')):
            with self.assertRaises(RuntimeError): rebuild.download_rebuild(self.root, self.record)
        self.assertEqual(list(self.root.rglob('*.partial')), [])
        self.assertEqual(list(self.root.rglob('*.jar')), [])

    def test_interrupted_download_removes_partial(self):
        with patch('urllib.request.urlopen', side_effect=OSError('offline')):
            with self.assertRaises(OSError): rebuild.download_rebuild(self.root, self.record)
        self.assertEqual(list(self.root.rglob('*.partial')), [])

    def test_source_resolution_downloads_when_missing(self):
        lock = {'files': [row('mods/original.jar', b'original')], 'external': [], 'localBuilds': [self.record]}
        with patch('urllib.request.urlopen', return_value=io.BytesIO(b'verified build')):
            sources = rebuild.validated_sources(self.root, lock)
        self.assertEqual(sources['mods/original.jar'].read_bytes(), b'verified build')

    def test_bad_explicit_source_does_not_silently_download(self):
        lock = {'files': [row('mods/original.jar', b'original')], 'external': [], 'localBuilds': [self.record]}
        with patch('urllib.request.urlopen') as request:
            with self.assertRaises(RuntimeError): rebuild.validated_sources(self.root, lock, npc_explicit=self.root/'missing.jar')
            request.assert_not_called()

    def test_http_and_credentials_are_rejected(self):
        for url in ['http://example.test/npc.jar', 'https://user:secret@example.test/npc.jar']:
            with self.subTest(url=url):
                with self.assertRaises(ValueError): rebuild.download_rebuild(self.root, dict(self.record, url=url))


if __name__ == '__main__': unittest.main()
