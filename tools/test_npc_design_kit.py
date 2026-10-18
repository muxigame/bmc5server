import hashlib,json,tempfile,unittest,zipfile
from pathlib import Path
import npc_design_kit as kit
class KitTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.game=self.root/'game';self.game.mkdir();self.path='config/yes_steve_model/custom/cpn_steve/main.json'
 def bundle(self,rows=None,data=b'default',extra=None):
  rows=rows or [{'path':self.path,'size':len(data),'sha256':hashlib.sha256(data).hexdigest()}];p=self.root/('kit'+str(len(list(self.root.glob('*.zip'))))+'.zip')
  with zipfile.ZipFile(p,'w') as z:
   z.writestr('kit-manifest.json',json.dumps({'schema':1,'files':rows}));z.writestr(rows[0]['path'],data)
   if extra:z.writestr(extra,b'x')
  self.lock={'npcDesignKit':{'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'files':rows}};return p
 def test_install_and_repeat(self):
  p=self.bundle();self.assertEqual(kit.install(self.root,self.game,self.lock,p)['installed'],1);self.assertEqual(kit.install(self.root,self.game,self.lock,p)['installed'],0);self.assertEqual(kit.verify(self.root,self.game,self.lock),[])
 def test_preserves_author_edits(self):
  p=self.bundle();f=self.game/self.path;f.parent.mkdir(parents=True);f.write_text('hand authored');kit.install(self.root,self.game,self.lock,p);self.assertEqual(f.read_text(),'hand authored');self.assertEqual(kit.verify(self.root,self.game,self.lock),[])
 def test_wrong_zip_hash(self):
  p=self.bundle();self.lock['npcDesignKit']['sha256']='0'*64
  with self.assertRaises(ValueError):kit.install(self.root,self.game,self.lock,p)
  self.assertFalse((self.game/self.path).exists())
 def test_corrupt_member(self):
  p=self.bundle(rows=[{'path':self.path,'size':7,'sha256':'0'*64}])
  with self.assertRaises(ValueError):kit.install(self.root,self.game,self.lock,p)
  self.assertFalse((self.game/self.path).exists())
 def test_extra_member_rejects_before_write(self):
  p=self.bundle(extra='oops.txt')
  with self.assertRaises(ValueError):kit.install(self.root,self.game,self.lock,p)
  self.assertFalse((self.game/self.path).exists())
 def test_destination_directory_rejected(self):
  p=self.bundle();(self.game/self.path).mkdir(parents=True)
  with self.assertRaises(ValueError):kit.install(self.root,self.game,self.lock,p)
 def test_escape_and_foreign_paths(self):
  for path in ['../outside','config/yes_steve_model/custom/cpn_steve/../../outside','C:/outside','/outside','mods/foreign.jar','world/playerdata/x.dat','config\\x']:
   with self.subTest(path=path),self.assertRaises(ValueError):kit.safe(self.game,path)
 def test_no_kit_backward_compatibility(self):
  self.assertEqual(kit.install(self.root,self.game,{}),{'installed':0,'preserved':0});self.assertEqual(kit.verify(self.root,self.game,{}),[])
 def test_missing_reports_without_network(self):
  self.bundle();self.assertEqual(len(kit.verify(self.root,self.game,self.lock)),1)
 def test_duplicate_names_rejected(self):
  p=self.bundle()
  with zipfile.ZipFile(p,'a') as z:z.writestr(self.path,b'default')
  self.lock['npcDesignKit'].update(size=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
  with self.assertRaises(ValueError):kit.install(self.root,self.game,self.lock,p)
if __name__=='__main__':unittest.main()
