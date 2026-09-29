"""Integrity and preservation checks for the source recovery tool; no Minecraft launch."""
import argparse
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch
import npc_source as recovery


class RecoverySafetyTests(unittest.TestCase):
    def test_archive_paths_cannot_escape_destination(self):
        for value in ('../outside', '/absolute', 'C:/outside', 'a/../../outside', 'a\\..\\outside'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                recovery.safe_member(value)
        self.assertEqual(recovery.safe_member('noppes/npcs/CustomNpcs.java'), Path('noppes/npcs/CustomNpcs.java'))

    def test_integrity_reports_edits_without_overwriting_them(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            original = root / 'original' / recovery.JAR_NAME
            original.parent.mkdir(); original.write_bytes(b'pinned binary fixture')
            ref = root / 'reference/vineflower/Example.java'
            ref.parent.mkdir(parents=True); ref.write_text('class Example {}', encoding='utf-8')
            working = root / 'src/main/java/Example.java'
            working.parent.mkdir(parents=True); working.write_bytes(ref.read_bytes())
            recovery.write_json(root / 'provenance/reference-files.json', {'Example.java': recovery.digest(ref)})
            recovery.write_json(root / 'provenance/working-baseline.json', {'src/main/java/Example.java': recovery.digest(working)})
            working.write_text('class Example { int deliberateEdit; }', encoding='utf-8')
            before = working.read_bytes()
            with patch.object(recovery, 'JAR_SHA', recovery.digest(original)):
                recovery.verify(argparse.Namespace(workspace=root))
                result = json.loads((root / 'reports/integrity.json').read_text(encoding='utf-8'))
                self.assertEqual(result['workingFilesModified'], ['src/main/java/Example.java'])
                self.assertEqual(working.read_bytes(), before)
                ref.write_text('changed reference', encoding='utf-8')
                with self.assertRaisesRegex(ValueError, 'Immutable reference changed'):
                    recovery.verify(argparse.Namespace(workspace=root))

    def test_wrong_binary_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            original = root / 'original' / recovery.JAR_NAME
            original.parent.mkdir(); original.write_bytes(b'not the pinned mod')
            with self.assertRaisesRegex(ValueError, 'Original binary changed'):
                recovery.verify(argparse.Namespace(workspace=root))

    def test_prepare_reuses_pinned_dependencies_and_preserves_working_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp); server = base / 'server'; root = base / 'recovery'
            jar = server / 'mods' / recovery.JAR_NAME
            jar.parent.mkdir(parents=True)
            with zipfile.ZipFile(jar, 'w') as archive:
                archive.writestr('noppes/Foo.class', b'class fixture; decompiler must not rerun')
            ref = root / 'reference/vineflower/noppes/Foo.java'
            ref.parent.mkdir(parents=True); ref.write_text('package noppes; class Foo {}', encoding='utf-8')
            working = root / 'src/main/java/noppes/Foo.java'
            working.parent.mkdir(parents=True); working.write_text('package noppes; class Foo { int myEdit; }', encoding='utf-8')
            recovery.write_json(root / 'provenance/reference-files.json', {'noppes/Foo.java': recovery.digest(ref)})
            recovery.write_json(root / 'provenance/working-baseline.json', {'src/main/java/noppes/Foo.java': recovery.digest(ref)})
            pinned = root / 'dependencies/pinned.jar'; pinned.parent.mkdir(); pinned.write_bytes(b'dependency version 1')
            manifest = root / 'provenance/dependencies.json'
            recovery.write_json(manifest, [{'path': str(pinned), 'sha256': recovery.digest(pinned)}])
            manifest_before = manifest.read_bytes(); working_before = working.read_bytes()
            args = argparse.Namespace(workspace=root, server=server, research=False)
            with patch.object(recovery, 'JAR_SHA', recovery.digest(jar)), patch.object(recovery, 'download'), patch.object(recovery, 'libraries', side_effect=AssertionError('must not rebind dependencies')):
                recovery.prepare(args)
            self.assertEqual(working.read_bytes(), working_before)
            self.assertEqual(manifest.read_bytes(), manifest_before)


if __name__ == '__main__':
    unittest.main()
