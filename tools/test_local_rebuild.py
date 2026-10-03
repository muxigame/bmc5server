"""Rebuild migration protects local modifications and the immutable upstream ZIP."""
from pathlib import Path
from types import SimpleNamespace
import hashlib, importlib.util, tempfile, unittest, zipfile
import local_rebuild as rebuild

def sha(data): return hashlib.sha256(data).hexdigest()

class MigrationTests(unittest.TestCase):
    def setUp(self):
        base = Path(__file__).resolve().parents[1] / ".runtime/rebuild-tests"; base.mkdir(parents=True, exist_ok=True)
        self.tmp = tempfile.TemporaryDirectory(dir=base); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name) / "pack"; self.root.mkdir()
        self.game = self.root / "game"; self.name = "mods/mcef.jar"
        self.old, self.new = b"upstream original", b"source rebuilt"
        self.source = self.root.parent / "mcef/build/libs/rebuilt.jar"; self.source.parent.mkdir(parents=True); self.source.write_bytes(self.new)
        self.target = self.game / self.name; self.target.parent.mkdir(parents=True)
        self.lock = {"files":[{"path":self.name,"size":len(self.old),"sha256":sha(self.old)}],"external":[],"localBuilds":[{"path":self.name,"size":len(self.new),"sha256":sha(self.new),"version":"rebuilt","modId":"mcef","sourceProject":"mcef","artifact":"build/libs/rebuilt.jar"}]}
    def install(self): rebuild.install(self.root, self.game, self.lock)
    def test_known_original_is_preserved_and_rerun_is_idempotent(self):
        self.target.write_bytes(self.old); self.install(); self.install()
        self.assertEqual(self.target.read_bytes(),self.new)
        self.assertEqual((self.root / ".runtime/rebuilt-originals" / self.name).read_bytes(),self.old)
    def test_unknown_local_jar_is_never_replaced(self):
        self.target.write_bytes(b"custom dirty")
        with self.assertRaises(RuntimeError): self.install()
        self.assertEqual(self.target.read_bytes(),b"custom dirty")
    def test_bad_build_fails_before_destination_changes(self):
        self.source.write_bytes(b"wrong build"); self.target.write_bytes(self.old)
        with self.assertRaises(RuntimeError): self.install()
        self.assertEqual(self.target.read_bytes(),self.old)
        self.assertFalse((self.root / ".runtime").exists())
    def test_changed_backup_is_preserved_and_blocks_replacement(self):
        self.target.write_bytes(self.old); backup=self.root / ".runtime/rebuilt-originals" / self.name
        backup.parent.mkdir(parents=True); backup.write_bytes(b"do not overwrite")
        with self.assertRaises(RuntimeError): self.install()
        self.assertEqual(backup.read_bytes(),b"do not overwrite"); self.assertEqual(self.target.read_bytes(),self.old)
    def test_destination_escape_is_rejected(self):
        self.lock["files"][0]["path"] = "../outside.jar"; self.lock["localBuilds"][0]["path"] = "../outside.jar"
        with self.assertRaises(ValueError): self.install()
        self.assertFalse((self.root / "outside.jar").exists())
    def test_project_escape_is_rejected(self):
        self.lock["localBuilds"][0]["sourceProject"] = "../mcef"
        with self.assertRaises(ValueError): self.install()
    def test_verify_selects_rebuilt_checksum(self):
        self.assertEqual(rebuild.locked_records(self.lock)[0]["sha256"],sha(self.new))
    def test_real_bootstrap_keeps_original_bundle_and_installs_rebuild(self):
        tools=Path(__file__).parent; isclient=(tools/"client.py").exists(); file=tools/("client.py" if isclient else "server.py")
        spec=importlib.util.spec_from_file_location("bootstrap_under_test",file); module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        module.ROOT=self.root; module.CACHE=self.root / ".runtime"; module.CACHE.mkdir()
        bundle=module.CACHE / "original.zip"
        with zipfile.ZipFile(bundle,"w") as z:z.writestr(self.name,self.old)
        self.lock["bundle"]={"sha256":rebuild.digest(bundle)}
        if isclient:
            module.GAME=self.game; self.target.write_bytes(self.old); self.lock["textFiles"]=[]
            (self.root / "pack-policy.json").write_text('{"overlays":[]}'); (self.root / "defaults").mkdir(); (self.root / "defaults/options.txt").write_text('')
        else:
            self.game=self.root; self.target=self.root/self.name; self.target.parent.mkdir(exist_ok=True); self.target.write_bytes(self.old)
            self.lock["retired"]=[]; installer=module.CACHE / "neoforge-installer.jar"; installer.write_bytes(b"already installed")
            self.lock["installer"]={"sha256":sha(installer.read_bytes())}; argfile=self.root / "installed.args"; argfile.write_text("")
            module.arguments_file=lambda lock:argfile; module.java_binary=lambda value:"not executed"
            for rel in ["server.properties","muxi-game-core.json","maid-sites/llm.json","maid-sites/tts.json","maid-sites/stt.json"]:
                f=self.root / "config-examples" / rel; f.parent.mkdir(parents=True,exist_ok=True); f.write_text("")
        old_bundle=rebuild.digest(bundle)
        module.setup(SimpleNamespace(bundle=str(bundle),mcef_jar=None,java=None,accept_eula=False),self.lock)
        self.assertEqual(rebuild.digest(bundle),old_bundle); self.assertEqual(self.target.read_bytes(),self.new)
        self.assertEqual((self.root / ".runtime/rebuilt-originals" / self.name).read_bytes(),self.old)
        module.verify(self.lock)

if __name__ == "__main__": unittest.main()
