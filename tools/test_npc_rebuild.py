"""Exercise NPC upgrades together with upstream MCEF, without game binaries."""
import hashlib
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
import zipfile
import local_rebuild as rebuild


def row(path, data):
    return dict(path=path, size=len(data), sha256=hashlib.sha256(data).hexdigest())


class NpcMigrationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / 'checkout'
        self.game = self.root / 'game'
        self.game.mkdir(parents=True)
        self.old = b'upstream NPC'
        self.new = b'merged NPC'
        self.earlier = b'localized NPC'
        self.original = 'mods/CustomNPCs-original.jar'
        self.installed = 'mods/CustomNPCs-stock.4.jar'
        self.previous = 'mods/CustomNPCs-zh.1.jar'
        self.source = self.root.parent / 'customnpcs-source/dist/npc.jar'
        self.source.parent.mkdir(parents=True)
        self.source.write_bytes(self.new)
        self.record = dict(row(self.original, self.new), installedPath=self.installed,
            modId='customnpcs', version='stock.4', sourceProject='customnpcs-source',
            artifact='dist/npc.jar', superseded=[row(self.previous, self.earlier)])
        self.lock = dict(files=[row(self.original, self.old)], external=[], localBuilds=[self.record])

    def put(self, name, data):
        path = self.game / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path

    def install(self):
        rebuild.install(self.root, self.game, self.lock)

    def test_upgrade_original_backs_up_and_removes_duplicate(self):
        old = self.put(self.original, self.old)
        self.install()
        self.assertFalse(old.exists())
        self.assertEqual((self.game / self.installed).read_bytes(), self.new)
        self.assertEqual((self.root / '.runtime/rebuilt-originals' / self.original).read_bytes(), self.old)
        self.assertEqual(rebuild.verify_rebuilds(self.game, self.lock), [])

    def test_localized_upgrade_and_rerun(self):
        previous = self.put(self.previous, self.earlier)
        self.install()
        self.install()
        self.assertFalse(previous.exists())
        self.assertEqual((self.root / '.runtime/rebuilt-originals' / self.previous).read_bytes(), self.earlier)

    def test_unknown_previous_is_preserved(self):
        previous = self.put(self.previous, b'private changes')
        with self.assertRaises(RuntimeError): self.install()
        self.assertEqual(previous.read_bytes(), b'private changes')
        self.assertFalse((self.game / self.installed).exists())

    def test_unknown_npc_filename_is_preserved(self):
        self.put('mods/CustomNPCs-experimental.jar', b'unknown version')
        with self.assertRaises(RuntimeError): self.install()
        self.assertTrue(rebuild.verify_rebuilds(self.game, self.lock))

    def test_modified_backup_blocks_upgrade(self):
        self.put(self.previous, self.earlier)
        backup = self.root / '.runtime/rebuilt-originals' / self.previous
        backup.parent.mkdir(parents=True)
        backup.write_bytes(b'changed backup')
        with self.assertRaises(RuntimeError): self.install()
        self.assertFalse((self.game / self.installed).exists())

    def test_missing_source_changes_nothing(self):
        self.source.unlink()
        old = self.put(self.original, self.old)
        with self.assertRaises(RuntimeError): self.install()
        self.assertEqual(old.read_bytes(), self.old)

    def test_npc_explicit_argument_is_independent_of_mcef(self):
        external = self.root / 'provided.jar'
        self.source.rename(external)
        sources = rebuild.validated_sources(self.root, self.lock, 'unused-mcef.jar', external)
        self.assertEqual(sources[self.original], external)

    def test_verify_uses_new_name_and_rejects_old_duplicate(self):
        self.put(self.installed, self.new)
        self.assertEqual(rebuild.locked_records(self.lock)[0]['path'], self.installed)
        self.put(self.original, self.old)
        self.assertTrue(rebuild.verify_rebuilds(self.game, self.lock))

    def test_destination_and_retirement_escape_rejected(self):
        self.record['installedPath'] = '../escape.jar'
        with self.assertRaises(ValueError): self.install()
        self.record['installedPath'] = self.installed
        self.record['superseded'][0]['path'] = '../escape.jar'
        with self.assertRaises(ValueError): self.install()

    def test_collision_with_mcef_is_rejected(self):
        self.lock['files'].append(row('mods/mcef.jar', b'mcef'))
        self.record['installedPath'] = 'mods/mcef.jar'
        with self.assertRaises(ValueError): rebuild.locked_records(self.lock)

    def test_npc_failure_does_not_partially_install_mcef(self):
        old_mcef, new_mcef = b'old MCEF', b'new MCEF'
        self.lock['files'].append(row('mods/mcef.jar', old_mcef))
        mcef_source = self.root.parent / 'mcef/rebuilt.jar'
        mcef_source.parent.mkdir()
        mcef_source.write_bytes(new_mcef)
        self.lock['localBuilds'].insert(0, dict(row('mods/mcef.jar', new_mcef), modId='mcef',
            version='muxi.1', sourceProject='mcef', artifact='rebuilt.jar'))
        target = self.put('mods/mcef.jar', old_mcef)
        self.put(self.previous, b'private NPC')
        with self.assertRaises(RuntimeError): self.install()
        self.assertEqual(target.read_bytes(), old_mcef)

    def test_real_setup_preserves_zip_and_installs_both_rebuilds(self):
        tools = Path(__file__).parent
        client = (tools / 'client.py').exists()
        spec = importlib.util.spec_from_file_location('npc_bootstrap_test', tools / ('client.py' if client else 'server.py'))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        module.ROOT = self.root
        module.CACHE = self.root / '.runtime'
        module.CACHE.mkdir()
        if client:
            module.GAME = self.game
            self.lock['textFiles'] = []
            (self.root / 'pack-policy.json').write_text('{"overlays":[]}')
            (self.root / 'defaults').mkdir()
            (self.root / 'defaults/options.txt').write_text('')
        else:
            self.game = self.root
            self.lock['retired'] = []
            installer = module.CACHE / 'neoforge-installer.jar'
            installer.write_bytes(b'installed')
            self.lock['installer'] = row('installer', b'installed')
            argsfile = self.root / 'installed.args'
            argsfile.write_text('')
            module.arguments_file = lambda lock: argsfile
            module.java_binary = lambda value: 'not executed'
            for name in ['server.properties', 'muxi-game-core.json', 'maid-sites/llm.json', 'maid-sites/tts.json', 'maid-sites/stt.json']:
                path = self.root / 'config-examples' / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('')
        old_mcef, new_mcef = b'original mcef', b'rebuilt mcef'
        self.lock['files'].append(row('mods/mcef.jar', old_mcef))
        mcef_source = self.root.parent / 'mcef/rebuilt.jar'
        mcef_source.parent.mkdir()
        mcef_source.write_bytes(new_mcef)
        self.lock['localBuilds'].insert(0, dict(row('mods/mcef.jar', new_mcef), modId='mcef',
            version='muxi.1', sourceProject='mcef', artifact='rebuilt.jar'))
        self.put(self.previous, self.earlier)
        bundle = module.CACHE / 'original.zip'
        with zipfile.ZipFile(bundle, 'w') as z:
            z.writestr(self.original, self.old)
            z.writestr('mods/mcef.jar', old_mcef)
        before = rebuild.digest(bundle)
        self.lock['bundle'] = {'sha256': before}
        args = SimpleNamespace(bundle=str(bundle), mcef_jar=None, npc_jar=str(self.source), java=None, accept_eula=False)
        module.setup(args, self.lock)
        module.verify(self.lock)
        self.assertEqual(before, rebuild.digest(bundle))
        self.assertFalse((self.game / self.previous).exists())
        self.assertFalse((self.game / self.original).exists())
        self.assertEqual((self.game / self.installed).read_bytes(), self.new)
        self.assertEqual((self.game / 'mods/mcef.jar').read_bytes(), new_mcef)


if __name__ == '__main__':
    unittest.main()
