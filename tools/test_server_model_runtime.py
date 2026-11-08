import hashlib,json,tempfile,unittest,zipfile
from pathlib import Path
from unittest.mock import patch
import server_model_runtime as m

def rec(path,data):return {'path':path,'size':len(data),'sha256':hashlib.sha256(data).hexdigest()}

class ModelRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        core=self.root/'core.jar';core.write_bytes(b'official-core-fixture')
        native=self.root/'native.jar'
        with zipfile.ZipFile(native,'w') as z:z.writestr('windows/x64/org/lwjgl/lwjgl.dll',b'official-native-fixture')
        a=rec('runtime-libraries/lwjgl.jar',core.read_bytes());a['name']='org.lwjgl:lwjgl:3.3.3'
        b=rec('runtime-libraries/native.jar',native.read_bytes());b.update(name='org.lwjgl:lwjgl:3.3.3:natives-windows',native={'member':'windows/x64/org/lwjgl/lwjgl.dll','file':'lwjgl.dll','size':len(b'official-native-fixture'),'sha256':hashlib.sha256(b'official-native-fixture').hexdigest()})
        self.lock={'serverModelRuntime':{'libraries':[a,b]}}
        (self.root/'.runtime').mkdir();(self.root/'.runtime/model-local-resources.json').write_text(json.dumps({a['path']:str(core),b['path']:str(native)}),encoding='utf8')
        self.patches=[patch.object(m.platform,'system',return_value='Windows'),patch.object(m.platform,'machine',return_value='AMD64')]
        for p in self.patches:p.start();self.addCleanup(p.stop)
    def test_minimal_install_and_nonmutating_original_arguments(self):
        m.setup(self.root,self.lock);self.assertEqual(m.verify(self.root,self.lock),[])
        p=self.root/'original-args.txt';text='-p libraries/bootstrap.jar\n--add-modules ALL-MODULE-PATH\nMain\n';p.write_text(text,encoding='utf8')
        generated=m.arguments(self.root,self.lock,p)
        self.assertEqual(p.read_text('utf8'),text);self.assertIn('org.lwjgl.librarypath',generated.read_text('utf8'));self.assertIn('lwjgl.jar',generated.read_text('utf8'))
        self.assertNotIn('glfw',generated.read_text('utf8').lower())
    def test_modified_native_prevents_start(self):
        m.setup(self.root,self.lock);(self.root/'.runtime/model-natives/lwjgl.dll').write_bytes(b'modified')
        with self.assertRaisesRegex(RuntimeError,'not installed or modified'):m.arguments(self.root,self.lock,self.root/'none.txt')
    def test_authored_factory_override_is_not_silently_deleted(self):
        p=self.root/'fml.toml';p.write_text('[dependencyOverrides]\ncitresewn = ["-connector"]\n# user content\n',encoding='utf8')
        self.lock['serverModelRuntime']['removeExactClientFactoryOverrides']={'fml.toml':{'factorySHA256':'0'*64,'entries':{'citresewn':['-connector']}}}
        with self.assertRaisesRegex(RuntimeError,'manual review'):m.setup(self.root,self.lock)
        self.assertIn('# user content',p.read_text('utf8'))

if __name__=='__main__':unittest.main()
