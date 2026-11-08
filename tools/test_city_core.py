"""Upgrade failure must preserve authored modules and verified rollback assets."""
from pathlib import Path
import copy, hashlib, tempfile, unittest, zipfile
import city_core as core


class CityUpgradeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.game = self.root / 'game'
        (self.game / 'mods').mkdir(parents=True)
        self.old = self.jar('old.jar', b'old')
        self.new = self.jar('new.jar', b'new')
        self.row = {'modId': 'city_settlements', **self.new, 'superseded': [self.old]}
        self.lock = {'files': [], 'external': [self.new], 'cityCore': {'modules': [self.row]}}

    def jar(self, name, content):
        p = self.game / 'mods' / name
        with zipfile.ZipFile(p, 'w') as z:
            z.writestr('META-INF/neoforge.mods.toml', '[[mods]]\nmodId="city_settlements"\n')
            z.writestr('payload', content)
        return {'path': 'mods/' + name, 'size': p.stat().st_size, 'sha256': core.digest(p)}

    def test_known_old_backed_up_and_idempotent(self):
        data = (self.game / self.old['path']).read_bytes()
        core.retire(self.root, self.game, self.lock)
        core.retire(self.root, self.game, self.lock)
        self.assertEqual((self.root / '.runtime/city-core-retired' / self.old['path']).read_bytes(), data)
        self.assertFalse((self.game / self.old['path']).exists())
        self.assertEqual(core.verify(self.root, self.game, self.lock), [])

    def test_modified_old_never_retired(self):
        p = self.game / self.old['path']
        p.write_bytes(b'authored')
        with self.assertRaises(RuntimeError): core.retire(self.root, self.game, self.lock)
        self.assertEqual(p.read_bytes(), b'authored')
        self.assertFalse((self.root / '.runtime').exists())

    def test_unknown_renamed_same_mod_blocks_before_retirement(self):
        self.jar('my-renamed-mod.jar', b'custom')
        with self.assertRaises(RuntimeError): core.retire(self.root, self.game, self.lock)
        self.assertTrue((self.game / self.old['path']).exists())
        self.assertFalse((self.root / '.runtime').exists())

    def test_missing_new_keeps_old(self):
        (self.game / self.new['path']).unlink()
        with self.assertRaises(RuntimeError): core.retire(self.root, self.game, self.lock)
        self.assertTrue((self.game / self.old['path']).exists())

    def test_corrupt_rollback_keeps_old(self):
        p = self.root / '.runtime/city-core-retired' / self.old['path']
        p.parent.mkdir(parents=True); p.write_bytes(b'precious')
        with self.assertRaises(RuntimeError): core.retire(self.root, self.game, self.lock)
        self.assertEqual(p.read_bytes(), b'precious')
        self.assertTrue((self.game / self.old['path']).exists())

    def test_lock_mismatch_and_escape_fail(self):
        lock = copy.deepcopy(self.lock);lock['cityCore']['modules'][0]['sha256'] = '0' * 64
        with self.assertRaises(ValueError): core.preflight(self.root, self.game, lock)
        for name in ['../x.jar', '/x', 'D:/x', 'a\\b']:
            with self.assertRaises(ValueError): core.safe(self.game, name)

    def test_legacy_no_core_lock_does_not_scan_unrelated_jars(self):
        (self.game / 'mods/legacy.jar').write_bytes(b'fixture')
        core.preflight(self.root, self.game, {'files': [], 'external': []})

    def test_optional_dependency_is_not_another_mod_identity(self):
        p = self.game / 'mods/optional.jar'
        with zipfile.ZipFile(p, 'w') as z:
            z.writestr('META-INF/neoforge.mods.toml', '[[mods]]\nmodId="other"\n[[dependencies.other]]\nmodId="city_settlements"\n')
        self.assertEqual(core.mod_ids(p), {'other'})
        core.preflight(self.root, self.game, self.lock)


if __name__ == '__main__': unittest.main()
