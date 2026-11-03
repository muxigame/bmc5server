from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,zipfile
p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--source-root',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--javac',default='javac');a=p.parse_args()
if hashlib.sha256(a.base.read_bytes()).hexdigest()!='f98ff432ee517efd936d6002c2689813ab2efd6be7579f6df66793a1df4a43d1':raise SystemExit('Unexpected base JAR')
if a.base.resolve()==a.output.resolve():raise SystemExit('Do not overwrite the original')
with tempfile.TemporaryDirectory(prefix='npc-markdata-') as folder:
 t=Path(folder);files=['controllers/data/MarkData','entity/data/IEntityMarkData','mixin/EntityMarkDataMixin'];args=['--release','21','-encoding','UTF-8','-proc:none','-cp',str(a.base.resolve())+os.pathsep+a.classpath_file.read_text().strip(),'-d',str(t/'classes')]+[str(a.source_root/'src/main/java/noppes/npcs'/(f+'.java')) for f in files]
 (t/'args').write_text('\n'.join(json.dumps(x) for x in args),encoding='utf-8');subprocess.run([a.javac,'@'+str(t/'args')],check=True)
 changes={x.relative_to(t/'classes').as_posix():x.read_bytes() for x in (t/'classes').rglob('*.class')}
 with zipfile.ZipFile(a.base) as src:
  cfg=json.loads(src.read('customnpcs.mixins.json'));cfg['mixins'].append('EntityMarkDataMixin');changes['customnpcs.mixins.json']=(json.dumps(cfg,indent=2)+'\n').encode();changes['META-INF/MANIFEST.MF']=src.read('META-INF/MANIFEST.MF').replace(b'Implementation-Version: 1.21.1.20251230-bmc-motion.118',b'Implementation-Version: 1.21.1.20251230-bmc-motion.118-markfix1')
  with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as dst:
   for info in src.infolist():dst.writestr(info,changes.pop(info.filename,src.read(info.filename)))
   for name,data in changes.items():dst.writestr(name,data)
print(hashlib.sha256(a.output.read_bytes()).hexdigest(),a.output.name)
