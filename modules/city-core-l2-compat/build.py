from pathlib import Path
import argparse,json,subprocess,tempfile,zipfile
R=Path(__file__).resolve().parent
def main():
 p=argparse.ArgumentParser();p.add_argument('--javac',required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
 if a.out.exists():raise RuntimeError('Refusing to overwrite output')
 cp=a.classpath_file.read_text('utf8').strip()
 with tempfile.TemporaryDirectory(prefix='city-l2-build-') as temp:
  root=Path(temp);classes=root/'classes';classes.mkdir();args=root/'compile.args';args.write_text('\n'.join(['-proc:none','-encoding','UTF-8','-cp',json.dumps(cp),'-d',json.dumps(str(classes)),*[json.dumps(str(x)) for x in sorted((R/'src').glob('*.java'))]])+'\n','utf8')
  subprocess.run([a.javac,'-J-Duser.language=en','-J-Dfile.encoding=UTF-8','@'+str(args)],check=True)
  entries=[(n,(R/'resources'/n).read_bytes()) for n in ['META-INF/MANIFEST.MF','META-INF/neoforge.mods.toml','city_core_l2_compat.mixins.json']]
  entries.extend((f.relative_to(classes).as_posix(),f.read_bytes()) for f in sorted(classes.rglob('*.class')))
  a.out.parent.mkdir(parents=True,exist_ok=True)
  with zipfile.ZipFile(a.out,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,data in entries:
    info=zipfile.ZipInfo(name,date_time=(2026,11,9,0,0,0));info.create_system=3;info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,data,compresslevel=9)
 print(str(a.out))
if __name__=='__main__':main()
