import hashlib,json,tempfile,unittest
from pathlib import Path
from launcher_base import setup,verify

class LauncherBaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);self.game=self.root/'game';self.game.mkdir()
        self.source=self.root/'public.txt';self.source.write_bytes(b'new')
        self.rec={'path':'config/sample.txt','sourcePath':'config/sample.txt','policy':'Managed','size':3,'sha256':hashlib.sha256(b'new').hexdigest()}
    def lock(self,policy='Managed',previous=None):
        self.rec['policy']=policy
        data={'pack':{'version':'1.4.94'},'filesBaseURL':'https://example.invalid/','files':[self.rec],'knownPrevious':previous or []}
        p=self.root/'base.json';p.write_text(json.dumps(data),encoding='utf8')
        return {'launcherBase':{'path':'base.json','sha256':hashlib.sha256(p.read_bytes()).hexdigest()},'localBuilds':[],'external':[]}
    def put(self,data):
        p=self.game/self.rec['path'];p.parent.mkdir(exist_ok=True);p.write_bytes(data);return p
    def test_managed_unknown_edit_refused_before_mutation(self):
        p=self.put(b'authored');lock=self.lock()
        with self.assertRaisesRegex(RuntimeError,'modified baseline'):setup(self.root,self.game,lock,local_resources={self.rec['path']:str(self.source)})
        self.assertEqual(p.read_bytes(),b'authored')
    def test_seed_preserved_and_managed_verified(self):
        p=self.put(b'authored');lock=self.lock('Seed')
        setup(self.root,self.game,lock,local_resources={self.rec['path']:str(self.source)})
        self.assertEqual(p.read_bytes(),b'authored');self.assertEqual(verify(self.root,self.game,lock),[])
    def test_known_managed_upgrade_and_idempotence(self):
        p=self.put(b'old');old={'path':self.rec['path'],'size':3,'sha256':hashlib.sha256(b'old').hexdigest()};lock=self.lock(previous=[old])
        _,first=setup(self.root,self.game,lock,local_resources={self.rec['path']:str(self.source)})
        _,again=setup(self.root,self.game,lock,local_resources={self.rec['path']:str(self.source)})
        self.assertEqual(p.read_bytes(),b'new');self.assertEqual(self.source.read_bytes(),b'new')
        self.assertEqual(first['changed'],1);self.assertEqual(again['changed'],0)
        self.assertEqual((self.root/'.runtime/launcher-retired'/self.rec['path']).read_bytes(),b'old')
    def test_bad_rollback_blocks_all_writes(self):
        p=self.put(b'old');old={'path':self.rec['path'],'size':3,'sha256':hashlib.sha256(b'old').hexdigest()};lock=self.lock(previous=[old])
        b=self.root/'.runtime/launcher-retired'/self.rec['path'];b.parent.mkdir(parents=True);b.write_bytes(b'bad')
        with self.assertRaisesRegex(RuntimeError,'rollback'):setup(self.root,self.game,lock,local_resources={self.rec['path']:str(self.source)})
        self.assertEqual(p.read_bytes(),b'old')
    def test_path_escape_and_unknown_mod_refused(self):
        lock=self.lock();(self.game/'mods').mkdir();(self.game/'mods/unknown.jar').write_bytes(b'mine')
        with self.assertRaisesRegex(RuntimeError,'Unknown extra mod'):setup(self.root,self.game,lock)
        self.assertEqual((self.game/'mods/unknown.jar').read_bytes(),b'mine')

    def test_known_factory_seed_migrates_but_authored_seed_survives(self):
        p=self.put(b'old');old={'path':self.rec['path'],'size':3,'sha256':hashlib.sha256(b'old').hexdigest()};lock=self.lock('Seed',previous=[old])
        _,first=setup(self.root,self.game,lock,local_resources={self.rec['path']:str(self.source)})
        self.assertEqual(p.read_bytes(),b'new');self.assertEqual(first['changed'],1)
        self.assertEqual((self.root/'.runtime/launcher-retired'/self.rec['path']).read_bytes(),b'old')
        p.write_bytes(b'local author edit')
        _,again=setup(self.root,self.game,lock,local_resources={self.rec['path']:str(self.source)})
        self.assertEqual(p.read_bytes(),b'local author edit');self.assertEqual(again['changed'],0)

    def test_exact_prior_overlay_is_left_for_its_verified_retirement(self):
        lock=self.lock();old=self.game/'mods/CustomNPCs-prior.jar';old.parent.mkdir();old.write_bytes(b'old')
        prior={'path':'mods/CustomNPCs-prior.jar','size':3,'sha256':hashlib.sha256(b'old').hexdigest()}
        lock['localBuilds']=[{'path':'mods/original.jar','installedPath':'mods/CustomNPCs-new.jar','superseded':[prior]}]
        setup(self.root,self.game,lock,local_resources={self.rec['path']:str(self.source)})
        self.assertEqual(old.read_bytes(),b'old')
        old.write_bytes(b'changed')
        with self.assertRaisesRegex(RuntimeError,'Modified superseded local rebuild'):setup(self.root,self.game,lock)

if __name__=='__main__':unittest.main()
